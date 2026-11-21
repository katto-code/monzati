package com.monzatti.app

import com.monzatti.app.domain.model.Garment
import com.monzatti.app.domain.model.Product
import com.monzatti.app.domain.tryon.GarmentColors
import com.monzatti.app.domain.tryon.GarmentKind
import com.monzatti.app.domain.tryon.TryOnSource
import com.monzatti.app.domain.tryon.toTryOnItem
import com.monzatti.app.ui.feature.tryon.BodyFrame
import com.monzatti.app.ui.feature.tryon.BodyFrameSmoother
import com.monzatti.app.ui.feature.tryon.Joint
import com.monzatti.app.ui.feature.tryon.Pt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TryOnTest {

    private fun side(shoulder: Pt, hip: Pt? = null) = buildMap {
        put(Joint.SHOULDER, shoulder)
        hip?.let { put(Joint.HIP, it) }
    }

    @Test
    fun frameOrdersSidesByScreenPosition() {
        // Cámara frontal en espejo: el hombro izquierdo del cuerpo aparece a la derecha de la pantalla.
        val body = BodyFrame.from(
            bodyLeft = side(Pt(300f, 200f), Pt(280f, 400f)),
            bodyRight = side(Pt(100f, 200f), Pt(120f, 400f))
        )
        assertNotNull(body)
        assertEquals(100f, body!!.shoulderLeft.x)
        assertEquals(300f, body.shoulderRight.x)
        assertEquals(120f, body.hipLeft.x)
        assertFalse(body.hipsEstimated)
    }

    @Test
    fun missingHipsAreEstimatedBelowShoulders() {
        val body = BodyFrame.from(side(Pt(100f, 200f)), side(Pt(300f, 200f)))!!
        assertTrue(body.hipsEstimated)
        // Ancho de hombros 200 → cadera ~270 px más abajo.
        assertEquals(200f + 200f * BodyFrame.TORSO_RATIO, body.hipLeft.y, 0.5f)
        assertTrue(body.hipLeft.x > body.shoulderLeft.x)
    }

    @Test
    fun noFrameWithoutBothShoulders() {
        assertNull(BodyFrame.from(side(Pt(100f, 200f)), emptyMap()))
        // Hombros casi encimados = persona de perfil o muy lejos.
        assertNull(BodyFrame.from(side(Pt(100f, 200f)), side(Pt(105f, 201f))))
    }

    @Test
    fun downNormalPointsDownEvenWhenTilted() {
        val n = BodyFrame.downNormal(Pt(100f, 220f), Pt(300f, 180f))
        assertTrue(n.y > 0f)
        assertEquals(1f, kotlin.math.hypot(n.x, n.y), 1e-4f)
    }

    @Test
    fun smootherReducesJitterAndToleratesShortGaps() {
        val smoother = BodyFrameSmoother(alpha = 0.5f, maxMissedFrames = 2)
        val a = BodyFrame.from(side(Pt(100f, 200f)), side(Pt(300f, 200f)))!!
        val b = BodyFrame.from(side(Pt(120f, 200f)), side(Pt(320f, 200f)))!!
        smoother.update(a)
        assertEquals(110f, smoother.update(b)!!.shoulderLeft.x, 0.01f)
        assertNotNull(smoother.update(null))
        assertNotNull(smoother.update(null))
        assertNull(smoother.update(null))
    }

    @Test
    fun garmentKindFromCategoryAndName() {
        assertEquals(GarmentKind.DRESS, GarmentKind.from("Vestidos", "Vestido midi"))
        assertEquals(GarmentKind.JACKET, GarmentKind.from("Nuevo", "Blazer camel"))
        assertEquals(GarmentKind.BLOUSE, GarmentKind.from("Blusas", "Playera oversize"))
        assertNull(GarmentKind.from("Pantalones", "Pantalón sastre"))
        assertNull(GarmentKind.from("Zapatos", "Botines de piel"))
    }

    @Test
    fun colorsResolveByNameOrHex() {
        assertEquals(0xFF1C1C1C, GarmentColors.resolve("negro"))
        assertEquals(0xFFF4C2C2, GarmentColors.resolve("#F4C2C2"))
        assertEquals(0xFF8E8E8E, GarmentColors.resolve("color raro"))
    }

    @Test
    fun shopAndClosetItemsBecomeTryOnItems() {
        val product = Product("p", "Marca", "Vestido de encaje", "👗", "Vestidos", 3890, mapOf("M" to 1), "Negro")
        val item = product.toTryOnItem()!!
        assertEquals(TryOnSource.SHOP, item.source)
        assertEquals(3890, item.price)
        val pants = Garment("g", "Pantalón", "👖", "Pantalones", "M", "")
        assertNull(pants.toTryOnItem())
    }
}
