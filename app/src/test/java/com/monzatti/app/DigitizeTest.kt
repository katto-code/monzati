package com.monzatti.app

import com.monzatti.app.domain.tryon.GarmentColors
import com.monzatti.app.domain.tryon.GarmentSuggestions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DigitizeTest {

    @Test
    fun nearestColorMatchesPalette() {
        assertEquals("Negro", GarmentColors.nearest(0xFF101010))
        assertEquals("Blanco", GarmentColors.nearest(0xFFFFFFFF))
        assertEquals("Mezclilla", GarmentColors.nearest(0xFF5A7D9A))
        assertEquals("Camel", GarmentColors.nearest(0xFFC29B6A))
        assertEquals("Rojo", GarmentColors.nearest(0xFFB0221A))
        // Cada color de la lista es el más cercano a sí mismo.
        GarmentColors.named.forEach { (name, argb) -> assertEquals(name, GarmentColors.nearest(argb)) }
    }

    @Test
    fun categoryFromLabelsUsesMostConfident() {
        assertEquals("Pantalones", GarmentSuggestions.category(listOf("Jeans" to 0.9f, "Shirt" to 0.4f)))
        assertEquals("Chamarras", GarmentSuggestions.category(listOf("Outerwear" to 0.7f, "Sleeve" to 0.5f)))
        assertEquals("Vestidos", GarmentSuggestions.category(listOf("Dress" to 0.8f)))
        assertNull(GarmentSuggestions.category(listOf("Table" to 0.9f)))
    }

    @Test
    fun suggestedName() {
        assertEquals("Chamarra mezclilla", GarmentSuggestions.name("Chamarras", "Mezclilla"))
        assertEquals("Blusa", GarmentSuggestions.name("Blusas", ""))
    }
}
