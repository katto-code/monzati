package com.monzatti.app

import com.monzatti.app.ui.components.sampleSizeFor
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageSamplingTest {
    @Test
    fun `reduce fotos grandes sin bajar del tamaño pedido`() {
        assertEquals(1, sampleSizeFor(400, 300, 512))
        assertEquals(1, sampleSizeFor(1000, 800, 512))
        assertEquals(2, sampleSizeFor(1024, 768, 512))
        assertEquals(4, sampleSizeFor(4032, 3024, 512))
    }
}
