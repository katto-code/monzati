package com.monzatti.app.ui.feature.closet

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import com.monzatti.app.core.theme.MonzattiTheme
import com.monzatti.app.data.local.GarmentImages
import com.monzatti.app.domain.model.GarmentCategories
import com.monzatti.app.domain.model.GarmentSizes
import com.monzatti.app.domain.repository.ClosetRepository
import com.monzatti.app.domain.tryon.GarmentColors
import com.monzatti.app.domain.tryon.GarmentSuggestions
import com.monzatti.app.ui.components.AppTextField
import com.monzatti.app.ui.components.BackLink
import com.monzatti.app.ui.components.ChoiceRow
import com.monzatti.app.ui.components.ErrorText
import com.monzatti.app.ui.components.FieldLabel
import com.monzatti.app.ui.components.LoadingOverlay
import com.monzatti.app.ui.components.MutedText
import com.monzatti.app.ui.components.PrimaryButton
import com.monzatti.app.ui.components.ScreenColumn
import com.monzatti.app.ui.components.SecondaryButton
import com.monzatti.app.ui.components.Title
import com.monzatti.app.ui.components.rememberImageFile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Paso del flujo: cámara → procesando → revisar → guardado. */
sealed interface DigitizePhase {
    data object Camera : DigitizePhase
    data object Processing : DigitizePhase
    data class Review(
        val imagePath: String?,
        val category: String,
        val color: String,
        val name: String,
        /** True si no se pudo recortar el fondo (se guarda la foto completa). */
        val withBackground: Boolean = false
    ) : DigitizePhase
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

/**
 * 3. Fotos / escaneo de ropa: recorta el fondo (ML Kit Subject Segmentation), sugiere categoría
 * (Image Labeling) y color (píxeles de la prenda). Todo corre en el celular, sin enviar la foto.
 */
@HiltViewModel
class DigitizeViewModel @Inject constructor(
    private val closet: ClosetRepository,
    private val images: GarmentImages
) : ViewModel() {

    private val _phase = MutableStateFlow<DigitizePhase>(DigitizePhase.Camera)
    val phase: StateFlow<DigitizePhase> = _phase.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val segmenter by lazy {
        SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundBitmap().build())
    }
    private val labeler by lazy { ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS) }

    fun process(photo: Bitmap) {
        _phase.value = DigitizePhase.Processing
        _error.value = null
        viewModelScope.launch {
            val review = withContext(Dispatchers.Default) {
                val bitmap = downscale(photo, 1280)
                val input = InputImage.fromBitmap(bitmap, 0)
                val labels = runCatching { labeler.process(input).awaitResult() }.getOrDefault(emptyList())
                    .map { it.text to it.confidence }
                val cutout = runCatching { segmenter.process(input).awaitResult().foregroundBitmap }.getOrNull()
                val source = cutout ?: bitmap
                val color = dominantColor(source, transparentBackground = cutout != null)
                val category = GarmentSuggestions.category(labels) ?: GarmentCategories.first()
                DigitizePhase.Review(
                    imagePath = images.saveDraft(source),
                    category = category,
                    color = color,
                    name = GarmentSuggestions.name(category, color),
                    withBackground = cutout == null
                )
            }
            _phase.value = review
        }
    }

    fun skipPhoto() {
        _phase.value = DigitizePhase.Review(null, GarmentCategories.first(), "Negro", "Prenda nueva")
    }

    fun retake() {
        _phase.value = DigitizePhase.Camera
    }

    fun save(name: String, category: String, size: String, color: String, onDone: () -> Unit) {
        val review = _phase.value as? DigitizePhase.Review ?: return
        _saving.value = true
        viewModelScope.launch {
            closet.add(name, category, size, color, review.imagePath)
                .onSuccess { onDone() }
                .onFailure { _error.value = it.message }
            _saving.value = false
        }
    }

    override fun onCleared() {
        runCatching { segmenter.close() }
        runCatching { labeler.close() }
    }

    private fun downscale(bitmap: Bitmap, maxSide: Int): Bitmap {
        val side = max(bitmap.width, bitmap.height)
        if (side <= maxSide) return bitmap
        val k = maxSide.toFloat() / side
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * k).toInt(), (bitmap.height * k).toInt(), true)
    }

    /** Vota el color de la lista más cercano en una muestra de píxeles de la prenda. */
    private fun dominantColor(bitmap: Bitmap, transparentBackground: Boolean): String {
        val votes = HashMap<String, Int>()
        val stepX = max(1, bitmap.width / 60)
        val stepY = max(1, bitmap.height / 60)
        // Sin recorte, se toma solo el centro (donde suele estar la prenda).
        val x0 = if (transparentBackground) 0 else bitmap.width / 4
        val x1 = if (transparentBackground) bitmap.width else bitmap.width * 3 / 4
        val y0 = if (transparentBackground) 0 else bitmap.height / 4
        val y1 = if (transparentBackground) bitmap.height else bitmap.height * 3 / 4
        var y = y0
        while (y < y1) {
            var x = x0
            while (x < x1) {
                val p = bitmap.getPixel(x, y)
                if ((p ushr 24) > 200) {
                    val name = GarmentColors.nearest(p.toLong() and 0xFFFFFFFFL)
                    votes[name] = (votes[name] ?: 0) + 1
                }
                x += stepX
            }
            y += stepY
        }
        return votes.maxByOrNull { it.value }?.key ?: "Negro"
    }

    fun onCaptureError(message: String) {
        _error.value = message
        _phase.value = DigitizePhase.Camera
    }
}

@Composable
fun DigitizeScreen(onDone: () -> Unit, viewModel: DigitizeViewModel = hiltViewModel()) {
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize()) {
        when (val p = phase) {
            DigitizePhase.Camera, DigitizePhase.Processing -> {
                if (granted) {
                    CaptureStep(onBack = onDone, onCaptured = viewModel::process, onSkip = viewModel::skipPhoto, onError = viewModel::onCaptureError)
                } else {
                    ScreenColumn {
                        BackLink("← Mi closet", onDone)
                        Title("Digitaliza tu prenda.")
                        MutedText("Necesitamos la cámara para fotografiar la prenda. La foto se procesa en tu celular.")
                        PrimaryButton("Permitir cámara", onClick = { launcher.launch(Manifest.permission.CAMERA) }, accent = true)
                        SecondaryButton("Registrar sin foto", onClick = viewModel::skipPhoto)
                    }
                }
            }
            is DigitizePhase.Review -> ReviewStep(p, onRetake = viewModel::retake, onDone = onDone, viewModel = viewModel)
        }
        LoadingOverlay(phase is DigitizePhase.Processing, "Recortando el fondo…")
    }
}

/** Cámara trasera con detección de la prenda y captura automática cuando está quieta. */
@Composable
private fun CaptureStep(
    onBack: () -> Unit,
    onCaptured: (Bitmap) -> Unit,
    onSkip: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val c = MonzattiTheme.colors
    val latestCaptured by rememberUpdatedState(onCaptured)
    val latestError by rememberUpdatedState(onError)

    val controller = remember { LifecycleCameraController(context) }
    val detector = remember {
        ObjectDetection.getClient(
            ObjectDetectorOptions.Builder().setDetectorMode(ObjectDetectorOptions.STREAM_MODE).build()
        )
    }
    var box by remember { mutableStateOf<Rect?>(null) }
    var viewSize by remember { mutableStateOf(Size.Zero) }
    var stableSince by remember { mutableLongStateOf(0L) }
    var lastCenter by remember { mutableStateOf<Offset?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var useBack by rememberSaveable { mutableStateOf(true) }

    fun capture() {
        if (capturing) return
        capturing = true
        controller.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val rotation = image.imageInfo.rotationDegrees
                    val raw = image.toBitmap()
                    image.close()
                    val bitmap = if (rotation == 0) raw else Bitmap.createBitmap(
                        raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation.toFloat()) }, true
                    )
                    latestCaptured(bitmap)
                    capturing = false
                }

                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                    latestError("No se pudo tomar la foto. Intenta de nuevo.")
                }
            }
        )
    }

    DisposableEffect(owner, useBack) {
        val executor = ContextCompat.getMainExecutor(context)
        val selector = if (useBack) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
        controller.setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE or LifecycleCameraController.IMAGE_ANALYSIS)
        controller.imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        controller.setImageAnalysisAnalyzer(
            executor,
            MlKitAnalyzer(listOf(detector), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, executor) { result ->
                val found = result.getValue(detector)?.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }?.boundingBox
                box = found
                val now = System.currentTimeMillis()
                if (found == null || viewSize == Size.Zero) {
                    stableSince = 0L
                    lastCenter = null
                    return@MlKitAnalyzer
                }
                val area = found.width().toFloat() * found.height() / (viewSize.width * viewSize.height)
                val center = Offset(found.exactCenterX(), found.exactCenterY())
                val prev = lastCenter
                val still = prev != null && abs(center.x - prev.x) < viewSize.width * 0.03f && abs(center.y - prev.y) < viewSize.height * 0.03f
                lastCenter = center
                if (area > 0.12f && still) {
                    if (stableSince == 0L) stableSince = now
                    if (now - stableSince > 1300 && !capturing) capture()
                } else {
                    stableSince = 0L
                }
            }
        )
        controller.initializationFuture.addListener({
            val chosen = if (controller.hasCamera(selector)) selector else CameraSelector.DEFAULT_FRONT_CAMERA
            if (controller.hasCamera(chosen)) {
                controller.cameraSelector = chosen
                controller.bindToLifecycle(owner)
            }
        }, executor)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
        }
    }
    DisposableEffect(Unit) { onDispose { detector.close() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    this.controller = controller
                }
            }
        )
        Canvas(Modifier.fillMaxSize()) {
            viewSize = size
            val b = box
            if (b != null) {
                drawRoundRect(
                    color = c.accent,
                    topLeft = Offset(b.left.toFloat(), b.top.toFloat()),
                    size = Size(b.width().toFloat(), b.height().toFloat()),
                    cornerRadius = CornerRadius(24f, 24f),
                    style = Stroke(width = 6f)
                )
            }
        }
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).background(Color(0xCC111111), CircleShape).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) { Text("←", color = Color.White, fontSize = 20.sp) }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(44.dp).background(Color(0xCC111111), CircleShape).clickable { useBack = !useBack },
                    contentAlignment = Alignment.Center
                ) { Text("⟲", color = Color.White, fontSize = 20.sp) }
            }
        }
        Text(
            when {
                box == null -> "Extiende la prenda sobre un fondo liso\ny apunta la cámara"
                stableSince > 0L -> "¡Quieto! Tomando la foto…"
                else -> "Acércate hasta que la prenda llene el recuadro"
            },
            color = Color.White,
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 84.dp)
                .background(Color(0xCC111111), RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(76.dp)
                    .border(4.dp, Color.White, CircleShape)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(if (capturing) c.accent else Color.White)
                    .clickable { capture() }
            )
            Text(
                "Registrar sin foto",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .background(Color(0x99111111), RoundedCornerShape(10.dp))
                    .clickable(onClick = onSkip)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun ReviewStep(review: DigitizePhase.Review, onRetake: () -> Unit, onDone: () -> Unit, viewModel: DigitizeViewModel) {
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var name by rememberSaveable(review) { mutableStateOf(review.name) }
    var category by rememberSaveable(review) { mutableStateOf(review.category) }
    var color by rememberSaveable(review) { mutableStateOf(review.color) }
    var size by rememberSaveable(review) { mutableStateOf("M") }
    val image = rememberImageFile(review.imagePath)
    val c = MonzattiTheme.colors

    ScreenColumn {
        BackLink("← Mi closet", onDone)
        FieldLabel(if (review.imagePath != null) "PRENDA DIGITALIZADA" else "NUEVA PRENDA")
        Title(if (review.imagePath != null) "Así quedó tu prenda." else "Captura cada detalle.")
        if (review.imagePath != null) {
            Box(
                Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(16.dp)).background(c.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                CheckerBackground()
                if (image != null) {
                    Image(image, contentDescription = name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(14.dp))
                }
            }
            MutedText(
                if (review.withBackground) "No pudimos recortar el fondo; se guardará la foto completa."
                else "Fondo recortado en tu celular · Categoría y color sugeridos"
            )
        }
        FieldLabel("NOMBRE DE LA PRENDA")
        AppTextField(name, { name = it })
        FieldLabel("CATEGORÍA")
        ChoiceRow(GarmentCategories, category) { category = it }
        FieldLabel("COLOR")
        ColorChoiceRow(color) { color = it }
        FieldLabel("TALLA")
        ChoiceRow(GarmentSizes, size) { size = it }
        ErrorText(error)
        PrimaryButton("Guardar en mi closet", onClick = { viewModel.save(name, category, size, color, onDone) }, accent = true, loading = saving)
        if (review.imagePath != null) SecondaryButton("Tomar otra foto", onClick = onRetake)
    }
}

/** Cuadrícula tipo "transparente" detrás de la prenda recortada. */
@Composable
private fun CheckerBackground() {
    val c = MonzattiTheme.colors
    Canvas(Modifier.fillMaxSize()) {
        val cell = 16.dp.toPx()
        var y = 0f
        var row = 0
        while (y < size.height) {
            var x = if (row % 2 == 0) 0f else cell
            while (x < size.width) {
                drawRect(c.surface.copy(alpha = 0.6f), Offset(x, y), Size(cell, cell))
                x += cell * 2
            }
            y += cell
            row++
        }
    }
}
