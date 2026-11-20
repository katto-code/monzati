package com.monzatti.app.ui.feature.tryon

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.monzatti.app.domain.tryon.GarmentKind

private fun Pt.o() = Offset(x, y)
private fun Offset.unit(): Offset = this / getDistance().coerceAtLeast(1e-3f)
private fun mix(a: Offset, b: Offset, t: Float) = a + (b - a) * t

/**
 * Dibuja una prenda vectorial sobre el esqueleto. La forma sigue hombros, codos, muñecas, cadera y
 * rodillas, así acompaña a la persona al moverse. Cuando existan fotos de prendas con fondo
 * recortado, se pueden dibujar con la misma geometría.
 */
fun DrawScope.drawGarment(frame: BodyFrame, kind: GarmentKind, color: Color) {
    val sA = frame.shoulderLeft.o()
    val sB = frame.shoulderRight.o()
    val w = (sB - sA).getDistance()
    if (w < 1f) return
    val u = (sB - sA).unit()
    val n = BodyFrame.downNormal(frame.shoulderLeft, frame.shoulderRight).o()
    val mid = (sA + sB) / 2f
    val hA = frame.hipLeft.o()
    val hB = frame.hipRight.o()
    val hipMid = (hA + hB) / 2f
    val torsoLen = (hipMid - mid).getDistance().coerceAtLeast(w * 0.9f)

    val shade = lerp(color, Color.Black, 0.28f)
    val highlight = lerp(color, Color.White, 0.16f)
    val sleeveColor = lerp(color, Color.Black, 0.10f)
    val outline = lerp(color, Color.Black, 0.55f).copy(alpha = 0.55f)
    val outlineWidth = (w * 0.012f).coerceIn(1.5f, 4f)

    // Mangas (debajo del torso para que la costura quede cubierta).
    val longSleeves = kind == GarmentKind.JACKET
    fun sleeve(shoulder: Offset, elbow: Pt?, wrist: Pt?, outward: Offset) {
        val start = shoulder + outward * (w * 0.03f) + n * (w * 0.07f)
        val e = elbow?.o() ?: (shoulder + outward * (w * 0.22f) + n * (w * 0.60f))
        val sleeveWidth = w * if (longSleeves) 0.30f else 0.32f
        val end = if (longSleeves) e else mix(start, e, if (kind == GarmentKind.DRESS) 0.32f else 0.48f)
        drawLine(sleeveColor, start, end, strokeWidth = sleeveWidth, cap = StrokeCap.Round)
        if (longSleeves) {
            val wr = wrist?.o() ?: (e + (e - start) * 0.85f)
            drawLine(sleeveColor, e, wr, strokeWidth = sleeveWidth * 0.82f, cap = StrokeCap.Round)
            // Puño
            drawLine(shade, mix(e, wr, 0.88f), wr, strokeWidth = sleeveWidth * 0.86f, cap = StrokeCap.Round)
        }
    }
    sleeve(sA, frame.elbowLeft, frame.wristLeft, u * -1f)
    sleeve(sB, frame.elbowRight, frame.wristRight, u)

    // Falda del vestido (debajo del torso).
    if (kind == GarmentKind.DRESS) {
        val hipW = (hB - hA).getDistance().coerceAtLeast(w * 0.7f)
        val kneeMid = if (frame.kneeLeft != null && frame.kneeRight != null) {
            (frame.kneeLeft.o() + frame.kneeRight.o()) / 2f + n * (w * 0.08f)
        } else {
            hipMid + n * (torsoLen * 1.05f)
        }
        val hemHalf = hipW * 0.95f
        val waistL = hA - u * (hipW * 0.12f) - n * (torsoLen * 0.12f)
        val waistR = hB + u * (hipW * 0.12f) - n * (torsoLen * 0.12f)
        val hemL = kneeMid - u * hemHalf
        val hemR = kneeMid + u * hemHalf
        val skirt = Path().apply {
            moveTo(waistL.x, waistL.y)
            lineTo(waistR.x, waistR.y)
            val cR = mix(waistR, hemR, 0.5f) + u * (hipW * 0.08f)
            quadraticBezierTo(cR.x, cR.y, hemR.x, hemR.y)
            val cHem = kneeMid + n * (w * 0.10f)
            quadraticBezierTo(cHem.x, cHem.y, hemL.x, hemL.y)
            val cL = mix(waistL, hemL, 0.5f) - u * (hipW * 0.08f)
            quadraticBezierTo(cL.x, cL.y, waistL.x, waistL.y)
            close()
        }
        drawPath(skirt, Brush.linearGradient(listOf(color, shade), start = hipMid, end = kneeMid))
        drawPath(skirt, outline, style = Stroke(outlineWidth))
        // Pliegues
        for (t in listOf(0.3f, 0.5f, 0.7f)) {
            val top = mix(waistL, waistR, t)
            val bottom = mix(hemL, hemR, t)
            drawLine(shade.copy(alpha = 0.35f), mix(top, bottom, 0.25f), bottom, strokeWidth = outlineWidth)
        }
    }

    // Torso
    val hemDrop = torsoLen * when (kind) {
        GarmentKind.BLOUSE -> 0.10f
        GarmentKind.JACKET -> 0.22f
        GarmentKind.DRESS -> 0.0f
    }
    val hipW = (hB - hA).getDistance()
    val hipOut = maxOf(hipW * 0.20f, w * 0.06f)
    val a = sA - u * (w * 0.10f) + n * (w * 0.02f)
    val b = sB + u * (w * 0.10f) + n * (w * 0.02f)
    val neckHalf = w * 0.17f
    val neckL = mid - u * neckHalf - n * (w * 0.04f)
    val neckR = mid + u * neckHalf - n * (w * 0.04f)
    val neckDip = mid + n * (w * if (kind == GarmentKind.JACKET) 0.08f else 0.18f)
    val hemL = hA - u * hipOut + n * hemDrop
    val hemR = hB + u * hipOut + n * hemDrop
    val hemMid = (hemL + hemR) / 2f
    val waistL = mix(a, hemL, 0.55f) + u * (w * 0.05f)
    val waistR = mix(b, hemR, 0.55f) - u * (w * 0.05f)

    val torso = Path().apply {
        moveTo(a.x, a.y)
        lineTo(neckL.x, neckL.y)
        quadraticBezierTo(neckDip.x, neckDip.y, neckR.x, neckR.y)
        lineTo(b.x, b.y)
        quadraticBezierTo(waistR.x, waistR.y, hemR.x, hemR.y)
        val hemCurve = hemMid + n * (w * 0.04f)
        quadraticBezierTo(hemCurve.x, hemCurve.y, hemL.x, hemL.y)
        quadraticBezierTo(waistL.x, waistL.y, a.x, a.y)
        close()
    }
    drawPath(torso, Brush.linearGradient(listOf(highlight, color, shade), start = mid, end = hemMid))

    when (kind) {
        GarmentKind.JACKET -> {
            // Playera blanca al frente y solapas.
            val inner = Path().apply {
                val tl = mid - u * (w * 0.13f) + n * (w * 0.02f)
                val tr = mid + u * (w * 0.13f) + n * (w * 0.02f)
                val br = hemMid + u * (w * 0.09f) - n * (w * 0.02f)
                val bl = hemMid - u * (w * 0.09f) - n * (w * 0.02f)
                moveTo(tl.x, tl.y); lineTo(tr.x, tr.y); lineTo(br.x, br.y); lineTo(bl.x, bl.y); close()
            }
            drawPath(inner, Color(0xFFF2F0EC))
            val lapelEnd = mid + n * (torsoLen * 0.48f)
            drawLine(shade, neckL, lapelEnd - u * (w * 0.10f), strokeWidth = outlineWidth * 1.6f, cap = StrokeCap.Round)
            drawLine(shade, neckR, lapelEnd + u * (w * 0.10f), strokeWidth = outlineWidth * 1.6f, cap = StrokeCap.Round)
        }
        GarmentKind.BLOUSE -> {
            // Ribete del cuello.
            val collar = Path().apply {
                moveTo(neckL.x, neckL.y)
                quadraticBezierTo(neckDip.x, neckDip.y, neckR.x, neckR.y)
            }
            drawPath(collar, shade, style = Stroke(outlineWidth * 2f))
        }
        GarmentKind.DRESS -> {
            // Cinturilla.
            drawLine(shade, mix(waistL, hemL, 0.75f), mix(waistR, hemR, 0.75f), strokeWidth = outlineWidth * 2f)
        }
    }
    drawPath(torso, outline, style = Stroke(outlineWidth))
}

/**
 * Dibuja la FOTO real de la prenda (fondo recortado) sobre el cuerpo: se escala al ancho de hombros,
 * gira con la inclinación de los hombros y cuelga desde el cuello.
 */
fun DrawScope.drawGarmentImage(frame: BodyFrame, kind: GarmentKind, image: ImageBitmap) {
    val sA = frame.shoulderLeft.o()
    val sB = frame.shoulderRight.o()
    val w = (sB - sA).getDistance()
    if (w < 1f || image.width == 0) return
    val mid = (sA + sB) / 2f
    val hipMid = (frame.hipLeft.o() + frame.hipRight.o()) / 2f
    val torso = (hipMid - mid).getDistance().coerceAtLeast(w)
    // Ancho de la foto respecto a los hombros (incluye mangas) y cuánto baja.
    val widthFactor = when (kind) {
        GarmentKind.JACKET -> 2.0f
        GarmentKind.DRESS -> 1.7f
        GarmentKind.BLOUSE -> 1.85f
    }
    val drawW = w * widthFactor
    val drawH = drawW * image.height / image.width
    // Para vestidos se asegura que llegue a las rodillas.
    val minH = if (kind == GarmentKind.DRESS) torso * 2.1f else torso * 1.1f
    val finalH = maxOf(drawH, minH)
    val finalW = if (finalH > drawH) drawW * 0.92f else drawW
    val angle = Math.toDegrees(kotlin.math.atan2((sB.y - sA.y).toDouble(), (sB.x - sA.x).toDouble())).toFloat()
    val top = mid.y - w * 0.18f
    withTransform({ rotate(angle, pivot = mid) }) {
        drawImage(
            image = image,
            dstOffset = IntOffset((mid.x - finalW / 2).toInt(), top.toInt()),
            dstSize = IntSize(finalW.toInt(), finalH.toInt())
        )
    }
}

/** Esqueleto fijo de un maniquí, para el espejo cuando no hay cámara. */
fun mannequinFrame(width: Float, height: Float) = BodyFrame(
    shoulderLeft = Pt(width / 2 - height * 0.13f, height * 0.25f),
    shoulderRight = Pt(width / 2 + height * 0.13f, height * 0.25f),
    hipLeft = Pt(width / 2 - height * 0.09f, height * 0.55f),
    hipRight = Pt(width / 2 + height * 0.09f, height * 0.55f),
    elbowLeft = Pt(width / 2 - height * 0.19f, height * 0.40f),
    elbowRight = Pt(width / 2 + height * 0.19f, height * 0.40f),
    wristLeft = Pt(width / 2 - height * 0.20f, height * 0.54f),
    wristRight = Pt(width / 2 + height * 0.20f, height * 0.54f),
    kneeLeft = Pt(width / 2 - height * 0.07f, height * 0.74f),
    kneeRight = Pt(width / 2 + height * 0.07f, height * 0.74f)
)

fun DrawScope.drawMannequin(frame: BodyFrame, color: Color) {
    val w = frame.shoulderWidth
    val mid = (frame.shoulderLeft.o() + frame.shoulderRight.o()) / 2f
    drawCircle(color, radius = w * 0.24f, center = mid - Offset(0f, w * 0.52f))
    drawLine(color, mid - Offset(0f, w * 0.25f), mid, strokeWidth = w * 0.16f)
    val limbs = listOf(
        frame.shoulderLeft to frame.elbowLeft, frame.elbowLeft to frame.wristLeft,
        frame.shoulderRight to frame.elbowRight, frame.elbowRight to frame.wristRight,
        frame.hipLeft to frame.kneeLeft, frame.hipRight to frame.kneeRight
    )
    limbs.forEach { (p, q) -> if (p != null && q != null) drawLine(color, p.o(), q.o(), strokeWidth = w * 0.16f, cap = StrokeCap.Round) }
    frame.kneeLeft?.let { drawLine(color, it.o(), it.o() + Offset(0f, w * 0.65f), strokeWidth = w * 0.14f, cap = StrokeCap.Round) }
    frame.kneeRight?.let { drawLine(color, it.o(), it.o() + Offset(0f, w * 0.65f), strokeWidth = w * 0.14f, cap = StrokeCap.Round) }
    val torso = Path().apply {
        moveTo(frame.shoulderLeft.x, frame.shoulderLeft.y)
        lineTo(frame.shoulderRight.x, frame.shoulderRight.y)
        lineTo(frame.hipRight.x, frame.hipRight.y)
        lineTo(frame.hipLeft.x, frame.hipLeft.y)
        close()
    }
    drawPath(torso, color)
}
