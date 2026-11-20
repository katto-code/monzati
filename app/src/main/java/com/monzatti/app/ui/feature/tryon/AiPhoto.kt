package com.monzatti.app.ui.feature.tryon

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import android.content.Context
import androidx.core.content.ContextCompat
import com.monzatti.app.domain.tryon.GarmentKind
import com.monzatti.app.domain.tryon.TryOnItem
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Estado de la foto con IA. */
sealed interface AiPhotoState {
    data object Idle : AiPhotoState
    data object Consent : AiPhotoState
    data class Countdown(val seconds: Int) : AiPhotoState
    data object Generating : AiPhotoState
    data class Result(val bitmap: Bitmap, val saved: Boolean = false) : AiPhotoState
    data class Failed(val message: String) : AiPhotoState
}

/** Categoría que entiende el servidor (CatVTON). */
fun GarmentKind.serverCategory(): String = if (this == GarmentKind.DRESS) "overall" else "upper"

/** Toma una foto con el controlador de la cámara; la frontal se espeja para que coincida con lo que se ve. */
suspend fun LifecycleCameraController.capturePhoto(context: Context, mirror: Boolean): Bitmap =
    suspendCancellableCoroutine { cont ->
        takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val rotation = image.imageInfo.rotationDegrees
                    val raw = image.toBitmap()
                    image.close()
                    val matrix = Matrix().apply {
                        postRotate(rotation.toFloat())
                        if (mirror) postScale(-1f, 1f)
                    }
                    cont.resume(Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true))
                }

                override fun onError(exception: ImageCaptureException) {
                    cont.resumeWithException(exception)
                }
            }
        )
    }

fun Bitmap.toJpeg(maxSide: Int = 1280, quality: Int = 90): ByteArray {
    val side = maxOf(width, height)
    val scaled = if (side > maxSide) {
        val k = maxSide.toFloat() / side
        Bitmap.createScaledBitmap(this, (width * k).toInt(), (height * k).toInt(), true)
    } else this
    return ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
}

/**
 * Imagen de la prenda para el servidor: la foto digitalizada si existe; si no, la prenda dibujada en plano
 * sobre fondo blanco (las prendas del catálogo aún no tienen fotos).
 */
fun garmentImageBytes(item: TryOnItem): ByteArray {
    item.imagePath?.let { path ->
        val file = File(path)
        if (file.exists()) return file.readBytes()
    }
    val w = 768
    val h = 1024
    val image = ImageBitmap(w, h)
    val canvas = Canvas(image)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
        drawRect(Color.White)
        // Prenda "colgada": hombros arriba, sin persona.
        val frame = BodyFrame(
            shoulderLeft = Pt(w * 0.27f, h * 0.16f),
            shoulderRight = Pt(w * 0.73f, h * 0.16f),
            hipLeft = Pt(w * 0.32f, h * (if (item.kind == GarmentKind.DRESS) 0.5f else 0.72f)),
            hipRight = Pt(w * 0.68f, h * (if (item.kind == GarmentKind.DRESS) 0.5f else 0.72f)),
            elbowLeft = Pt(w * 0.13f, h * 0.42f),
            elbowRight = Pt(w * 0.87f, h * 0.42f),
            wristLeft = Pt(w * 0.09f, h * 0.66f),
            wristRight = Pt(w * 0.91f, h * 0.66f),
            kneeLeft = Pt(w * 0.33f, h * 0.9f),
            kneeRight = Pt(w * 0.67f, h * 0.9f)
        )
        drawGarment(frame, item.kind, Color(item.color))
    }
    return ByteArrayOutputStream().also { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
}

fun decodeJpeg(bytes: ByteArray): Bitmap =
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("La respuesta del servidor no es una imagen.")
