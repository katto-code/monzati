package com.monzatti.app.ui.feature.tryon

import kotlin.math.hypot

/** Punto en coordenadas de pantalla (píxeles de la vista de cámara). Kotlin puro para poder probarlo. */
data class Pt(val x: Float, val y: Float) {
    operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)
    operator fun minus(o: Pt) = Pt(x - o.x, y - o.y)
    operator fun times(k: Float) = Pt(x * k, y * k)
    fun distanceTo(o: Pt): Float = hypot(x - o.x, y - o.y)
}

fun lerp(a: Pt, b: Pt, t: Float) = Pt(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

enum class Joint { SHOULDER, ELBOW, WRIST, HIP, KNEE }

/**
 * Esqueleto mínimo para vestir: "left" y "right" son los lados de la PANTALLA (no del cuerpo),
 * así el dibujo no depende de si la cámara está en modo espejo.
 */
data class BodyFrame(
    val shoulderLeft: Pt,
    val shoulderRight: Pt,
    val hipLeft: Pt,
    val hipRight: Pt,
    val elbowLeft: Pt? = null,
    val elbowRight: Pt? = null,
    val wristLeft: Pt? = null,
    val wristRight: Pt? = null,
    val kneeLeft: Pt? = null,
    val kneeRight: Pt? = null,
    /** True si la cadera no se veía y se calculó a partir de los hombros. */
    val hipsEstimated: Boolean = false
) {
    val shoulderWidth: Float get() = shoulderLeft.distanceTo(shoulderRight)

    companion object {
        /** Proporción típica hombros→cadera respecto al ancho de hombros. */
        const val TORSO_RATIO = 1.35f
        private const val MIN_SHOULDER_WIDTH = 24f

        /**
         * Construye el marco con los puntos confiables de cada lado del cuerpo.
         * Necesita ambos hombros; si falta la cadera, la estima.
         */
        fun from(bodyLeft: Map<Joint, Pt>, bodyRight: Map<Joint, Pt>): BodyFrame? {
            val sl = bodyLeft[Joint.SHOULDER] ?: return null
            val sr = bodyRight[Joint.SHOULDER] ?: return null
            // Ordena por posición en pantalla.
            val (a, b) = if (sl.x <= sr.x) bodyLeft to bodyRight else bodyRight to bodyLeft
            val sA = a.getValue(Joint.SHOULDER)
            val sB = b.getValue(Joint.SHOULDER)
            val width = sA.distanceTo(sB)
            if (width < MIN_SHOULDER_WIDTH) return null

            val hA = a[Joint.HIP]
            val hB = b[Joint.HIP]
            val estimated = hA == null || hB == null
            val down = downNormal(sA, sB)
            val hipLeft = if (estimated) sA + down * (width * TORSO_RATIO) + (sB - sA) * 0.06f else hA!!
            val hipRight = if (estimated) sB + down * (width * TORSO_RATIO) - (sB - sA) * 0.06f else hB!!

            return BodyFrame(
                shoulderLeft = sA,
                shoulderRight = sB,
                hipLeft = hipLeft,
                hipRight = hipRight,
                elbowLeft = a[Joint.ELBOW],
                elbowRight = b[Joint.ELBOW],
                wristLeft = a[Joint.WRIST],
                wristRight = b[Joint.WRIST],
                kneeLeft = a[Joint.KNEE],
                kneeRight = b[Joint.KNEE],
                hipsEstimated = estimated
            )
        }

        /** Vector unitario perpendicular a los hombros que apunta hacia abajo en pantalla. */
        fun downNormal(left: Pt, right: Pt): Pt {
            val d = left.distanceTo(right).coerceAtLeast(1e-3f)
            val ux = (right.x - left.x) / d
            val uy = (right.y - left.y) / d
            val n = Pt(-uy, ux)
            return if (n.y >= 0f) n else n * -1f
        }
    }
}

/**
 * Suaviza el temblor entre cuadros (media exponencial) y tolera unos cuadros sin detección
 * antes de esconder la prenda.
 */
class BodyFrameSmoother(private val alpha: Float = 0.45f, private val maxMissedFrames: Int = 8) {

    private var last: BodyFrame? = null
    private var missed = 0

    fun update(next: BodyFrame?): BodyFrame? {
        if (next == null) {
            missed++
            if (missed > maxMissedFrames) last = null
            return last
        }
        missed = 0
        val prev = last
        val smoothed = if (prev == null) next else BodyFrame(
            shoulderLeft = mix(prev.shoulderLeft, next.shoulderLeft),
            shoulderRight = mix(prev.shoulderRight, next.shoulderRight),
            hipLeft = mix(prev.hipLeft, next.hipLeft),
            hipRight = mix(prev.hipRight, next.hipRight),
            elbowLeft = mixOrNull(prev.elbowLeft, next.elbowLeft),
            elbowRight = mixOrNull(prev.elbowRight, next.elbowRight),
            wristLeft = mixOrNull(prev.wristLeft, next.wristLeft),
            wristRight = mixOrNull(prev.wristRight, next.wristRight),
            kneeLeft = mixOrNull(prev.kneeLeft, next.kneeLeft),
            kneeRight = mixOrNull(prev.kneeRight, next.kneeRight),
            hipsEstimated = next.hipsEstimated
        )
        last = smoothed
        return smoothed
    }

    fun reset() {
        last = null
        missed = 0
    }

    private fun mix(old: Pt, new: Pt) = lerp(old, new, alpha)
    private fun mixOrNull(old: Pt?, new: Pt?): Pt? = when {
        new == null -> null
        old == null -> new
        else -> mix(old, new)
    }
}
