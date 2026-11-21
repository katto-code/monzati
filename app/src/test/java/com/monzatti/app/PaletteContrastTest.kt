package com.monzatti.app

import androidx.compose.ui.graphics.Color
import com.monzatti.app.core.theme.colors
import com.monzatti.app.domain.model.StylePalette
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/** S0-10 / DoD: la interfaz debe cumplir contraste. WCAG AA para texto normal = 4.5:1. */
class PaletteContrastTest {

    private fun channel(v: Float): Double =
        if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)

    private fun luminance(c: Color): Double =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    @Test
    fun everyPaletteHasReadableText() {
        val failures = StylePalette.entries.flatMap { palette ->
            val c = palette.colors()
            listOf(
                "texto/fondo" to contrast(c.ink, c.background),
                "texto/superficie" to contrast(c.ink, c.surface),
                "texto/marco" to contrast(c.ink, c.surfaceVariant),
                "secundario/fondo" to contrast(c.muted, c.background),
                "secundario/superficie" to contrast(c.muted, c.surface),
                "botón principal" to contrast(c.onPrimary, c.primary),
                "botón acento" to contrast(c.onAccent, c.accent)
            ).filter { it.second < 4.5 }.map { "${palette.name} ${it.first}: %.2f".format(it.second) }
        }
        assertTrue("Contraste insuficiente:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
