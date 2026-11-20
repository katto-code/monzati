package com.monzatti.app.ui.feature.tryon

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monzatti.app.core.theme.MonzattiTheme
import com.monzatti.app.domain.tryon.TryOnItem
import com.monzatti.app.domain.tryon.TryOnSource
import com.monzatti.app.ui.components.PrimaryButton
import com.monzatti.app.ui.components.rememberImageFile
import com.monzatti.app.ui.components.ScreenColumn
import com.monzatti.app.ui.components.SecondaryButton
import com.monzatti.app.ui.components.Title
import com.monzatti.app.ui.components.MutedText

private val Overlay = Color(0xCC111111)

/** Probador con cámara: la cámara frontal funciona como espejo y la prenda sigue el cuerpo. */
@Composable
fun TryOnScreen(onBack: () -> Unit, onOpenMirror: () -> Unit, viewModel: TryOnViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        askedOnce = true
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    if (granted) {
        TryOnCamera(onBack, onOpenMirror, viewModel)
    } else {
        ScreenColumn {
            Spacer(Modifier.height(40.dp))
            Title("Pruébatelo con tu cámara.")
            MutedText("Monzatti usa la cámara para detectar tu postura y vestirte en tiempo real. Las imágenes se procesan en tu celular y no se guardan ni se envían.")
            PrimaryButton("Permitir cámara", onClick = { launcher.launch(Manifest.permission.CAMERA) }, accent = true)
            if (askedOnce) MutedText("Si lo rechazaste antes, actívalo en Ajustes › Apps › Monzatti › Permisos.")
            SecondaryButton("Volver", onClick = onBack)
        }
    }
}

@Composable
private fun TryOnCamera(onBack: () -> Unit, onOpenMirror: () -> Unit, viewModel: TryOnViewModel) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var useFront by rememberSaveable { mutableStateOf(true) }
    var cameraMissing by remember { mutableStateOf(false) }
    val smoother = remember { BodyFrameSmoother() }
    var frame by remember { mutableStateOf<BodyFrame?>(null) }

    // Cuadros por segundo de la detección (indicador para la demo y para medir el KPI).
    var fps by remember { mutableIntStateOf(0) }
    var frames by remember { mutableIntStateOf(0) }
    var windowStart by remember { mutableLongStateOf(System.nanoTime()) }

    var drag by remember { mutableFloatStateOf(0f) }
    val controller = rememberPoseController()
    val context = LocalContext.current
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val garmentPhoto = rememberImageFile(selected?.imagePath)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!cameraMissing) {
            PoseCameraPreview(
                useFrontCamera = useFront,
                onPose = { pose ->
                    frame = smoother.update(pose)
                    frames++
                    val now = System.nanoTime()
                    if (now - windowStart >= 1_000_000_000L) {
                        fps = frames
                        frames = 0
                        windowStart = now
                    }
                },
                onCameraUnavailable = {
                    if (useFront) useFront = false else cameraMissing = true
                },
                modifier = Modifier.fillMaxSize(),
                controller = controller
            )
        }

        // Prenda sobre el cuerpo + gesto para cambiar de prenda.
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (drag > 120f) viewModel.step(-1) else if (drag < -120f) viewModel.step(1)
                            drag = 0f
                        },
                        onDragCancel = { drag = 0f },
                        onHorizontalDrag = { _, amount -> drag += amount }
                    )
                }
        ) {
            val f = frame
            val item = selected
            val photo = garmentPhoto
            if (f != null && item != null) {
                if (photo != null) drawGarmentImage(f, item.kind, photo) else drawGarment(f, item.kind, Color(item.color))
            }
        }

        // Barra superior
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RoundIcon("←", onBack)
            Row(
                Modifier.background(Overlay, CircleShape).padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).background(if (frame != null) Color(0xFF4CD964) else Color(0xFFFF453A), CircleShape))
                Spacer(Modifier.width(7.dp))
                Text(
                    if (frame != null) "EN VIVO · $fps fps" else "BUSCANDO CUERPO",
                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp
                )
            }
            Spacer(Modifier.weight(1f))
            RoundIcon("⟲") { useFront = !useFront; smoother.reset(); frame = null }
        }

        // Guía cuando no hay cuerpo en cuadro.
        if (frame == null) {
            Text(
                when {
                    cameraMissing -> "Este dispositivo no tiene cámara disponible."
                    else -> "Colócate de frente a 1–2 m,\ncon hombros y cadera a la vista"
                },
                color = Color.White,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Overlay, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            )
        }

        BottomPanel(
            items = items,
            selected = selected,
            message = message,
            onSelect = viewModel::select,
            onBag = viewModel::addToBag,
            onMirror = { viewModel.projectToMirror() },
            onOpenMirror = onOpenMirror,
            onAiPhoto = { viewModel.startAiPhoto { controller.capturePhoto(context, mirror = useFront) } },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        AiPhotoOverlay(
            state = aiState,
            itemName = selected?.name.orEmpty(),
            onAccept = viewModel::acceptConsent,
            onSave = viewModel::saveAiPhoto,
            onDismiss = viewModel::dismissAi
        )
    }
}

/** Capas de la foto con IA: consentimiento, cuenta regresiva, generando, resultado o error. */
@Composable
private fun AiPhotoOverlay(
    state: AiPhotoState,
    itemName: String,
    onAccept: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = MonzattiTheme.colors
    when (state) {
        AiPhotoState.Idle -> Unit
        is AiPhotoState.Countdown -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AnimatedContent(targetState = state.seconds, transitionSpec = { (scaleIn() + fadeIn()) togetherWith fadeOut() }, label = "count") {
                Text("$it", color = Color.White, fontSize = 120.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "¡Posa de frente!",
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 120.dp).background(Overlay, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        else -> Box(
            Modifier.fillMaxSize().background(c.background.copy(alpha = 0.96f)).pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (state) {
                    AiPhotoState.Consent -> {
                        Text("📸", fontSize = 48.sp)
                        Text("Foto realista con IA", color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Tomaremos una foto tuya y la enviaremos al servidor de IA de Monzatti para vestirte con «$itemName». " +
                                "La foto no se guarda en el servidor: se borra al terminar.",
                            color = c.muted, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 19.sp
                        )
                        PrimaryButton("Acepto, tomar foto", onClick = onAccept, accent = true)
                        SecondaryButton("Ahora no", onClick = onDismiss)
                    }
                    AiPhotoState.Generating -> {
                        CircularProgressIndicator(color = c.accent, trackColor = c.line, strokeWidth = 3.dp)
                        Text("Vistiéndote con IA…", color = c.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("Esto tarda de 10 a 30 segundos.", color = c.muted, fontSize = 13.sp)
                    }
                    is AiPhotoState.Result -> {
                        Image(
                            state.bitmap.asImageBitmap(),
                            contentDescription = "Foto generada con IA",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).clip(RoundedCornerShape(18.dp))
                        )
                        Text(itemName, color = c.ink, fontWeight = FontWeight.SemiBold)
                        PrimaryButton(if (state.saved) "✓ Guardada en mis looks" else "Guardar en mis looks", onClick = onSave, accent = true, enabled = !state.saved)
                        SecondaryButton("Cerrar", onClick = onDismiss)
                    }
                    is AiPhotoState.Failed -> {
                        Text("⚠", fontSize = 40.sp, color = c.ink)
                        Text("No se pudo generar la foto", color = c.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text(state.message, color = c.muted, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 19.sp)
                        SecondaryButton("Cerrar", onClick = onDismiss)
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).background(Overlay, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(icon, color = Color.White, fontSize = 20.sp) }
}

@Composable
private fun BottomPanel(
    items: List<TryOnItem>,
    selected: TryOnItem?,
    message: String?,
    onSelect: (String) -> Unit,
    onBag: () -> Unit,
    onMirror: () -> Unit,
    onOpenMirror: () -> Unit,
    onAiPhoto: () -> Unit,
    modifier: Modifier
) {
    val c = MonzattiTheme.colors
    val listState = rememberLazyListState()
    LaunchedEffect(selected?.id, items.size) {
        val index = items.indexOfFirst { it.id == selected?.id }
        if (index >= 0) listState.animateScrollToItem(index)
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(c.surface.copy(alpha = 0.94f), RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .padding(top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (selected != null) {
            Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(selected.subtitle.uppercase(), color = c.muted, fontSize = 10.sp, letterSpacing = 1.1.sp)
                    Text(selected.name, color = c.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
                selected.price?.let { Text(com.monzatti.app.domain.shop.mxn(it), color = c.ink, fontWeight = FontWeight.SemiBold) }
            }
        } else {
            Text(
                "Aún no hay prendas para probar. Agrega blusas, chamarras o vestidos.",
                color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp)
            )
        }

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items, key = { it.id }) { item ->
                val isSelected = item.id == selected?.id
                Row(
                    Modifier
                        .background(if (isSelected) c.accent else c.background, CircleShape)
                        .border(1.dp, if (isSelected) c.accent else c.line, CircleShape)
                        .clickable { onSelect(item.id) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(14.dp).background(Color(item.color), CircleShape).border(1.dp, c.line, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(item.name, color = if (isSelected) c.onAccent else c.ink, fontSize = 12.sp)
                }
            }
        }

        if (!message.isNullOrBlank()) {
            Text(message, color = c.ink, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp))
        }

        Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (selected?.source == TryOnSource.SHOP) {
                Button(
                    onClick = onBag,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary)
                ) { Text("Añadir al carrito") }
            }
            Button(
                onClick = onMirror,
                enabled = selected != null,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(11.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent)
            ) { Text("◎  Al espejo", fontWeight = FontWeight.SemiBold) }
        }
        Button(
            onClick = onAiPhoto,
            enabled = selected != null,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            shape = RoundedCornerShape(11.dp),
            colors = ButtonDefaults.buttonColors(containerColor = c.surfaceVariant, contentColor = c.ink)
        ) { Text("📸  Foto realista con IA", fontWeight = FontWeight.SemiBold) }
        Text(
            "Desliza de lado para cambiar de prenda · Ver espejo →",
            color = c.muted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenMirror)
        )
    }
}
