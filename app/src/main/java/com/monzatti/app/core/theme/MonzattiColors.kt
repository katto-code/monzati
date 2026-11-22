package com.monzatti.app.core.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.monzatti.app.domain.model.StylePalette

/** Colores con nombre de uso. Las pantallas nunca usan colores fijos: leen de aquí vía MonzattiTheme.colors. */
@Immutable
data class MonzattiColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    /** Marcos de imágenes y placeholders. */
    val surfaceVariant: Color,
    /** Texto principal. */
    val ink: Color,
    /** Texto secundario. */
    val muted: Color,
    val line: Color,
    /** Botón principal. */
    val primary: Color,
    val onPrimary: Color,
    /** Botón destacado, chips seleccionados y gráficas. */
    val accent: Color,
    val onAccent: Color,
    /** Logotipo MONZATTI. */
    val brand: Color,
    val positive: Color,
    /** Mensajes de error y estados cancelados. */
    val error: Color,
    /** Colores de la paleta original, para vistas previas. */
    val swatches: List<Color>
)

fun StylePalette.colors(): MonzattiColors = when (this) {
    StylePalette.MONZATTI -> MonzattiColors(
        isDark = false,
        background = Color(0xFFF3F2EF),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFE8E6E2),
        ink = Color(0xFF171717),
        muted = Color(0xFF65625D),
        line = Color(0xFFE4E1DC),
        primary = Color(0xFF171717),
        onPrimary = Color(0xFFFFFFFF),
        accent = Color(0xFFC9A227),
        onAccent = Color(0xFF171717),
        brand = Color(0xFFC9A227),
        positive = Color(0xFF2A8A3A),
        error = Color(0xFFB3261E),
        swatches = listOf(Color(0xFF171717), Color(0xFFC9A227), Color(0xFFF3F2EF), Color(0xFF77746F), Color(0xFFE4E1DC))
    )

    StylePalette.COQUETTE -> MonzattiColors(
        isDark = false,
        background = Color(0xFFFFF8E7),
        surface = Color(0xFFFFFFF0),
        surfaceVariant = Color(0xFFFADADD),
        ink = Color(0xFF4A3B35),
        muted = Color(0xFF7A6156),
        line = Color(0xFFEBD9C9),
        primary = Color(0xFFDCAE96),
        onPrimary = Color(0xFF3B2A24),
        accent = Color(0xFFF4C2C2),
        onAccent = Color(0xFF3B2A24),
        brand = Color(0xFFC8AD7F),
        positive = Color(0xFF5E7F4F),
        error = Color(0xFFB3261E),
        swatches = listOf(Color(0xFFFFFFF0), Color(0xFFFFF8E7), Color(0xFFF4C2C2), Color(0xFFFADADD), Color(0xFFDCAE96), Color(0xFFC8AD7F))
    )

    StylePalette.GOTICA -> MonzattiColors(
        isDark = true,
        background = Color(0xFF0B0B0B),
        surface = Color(0xFF2C2C2C),
        surfaceVariant = Color(0xFF301934),
        ink = Color(0xFFE5E4E2),
        muted = Color(0xFFADABA8),
        line = Color(0xFF444444),
        // 4A0404 casi desaparecía sobre el fondo negro; este rojo se distingue y el texto claro sigue a ~5:1.
        primary = Color(0xFFB22222),
        onPrimary = Color(0xFFE5E4E2),
        accent = Color(0xFFC0C0C0),
        onAccent = Color(0xFF0B0B0B),
        brand = Color(0xFFC0C0C0),
        positive = Color(0xFF8FC79A),
        error = Color(0xFFF2B8B5),
        swatches = listOf(Color(0xFF0B0B0B), Color(0xFF2C2C2C), Color(0xFF4A0404), Color(0xFF301934), Color(0xFFC0C0C0), Color(0xFFE5E4E2))
    )

    StylePalette.STREETWEAR -> MonzattiColors(
        isDark = false,
        background = Color(0xFFF5F5DC),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFE2E2D2),
        ink = Color(0xFF1C1C1C),
        muted = Color(0xFF5B5B57),
        line = Color(0xFF8E8E8E),
        primary = Color(0xFF1C1C1C),
        onPrimary = Color(0xFFF5F5DC),
        accent = Color(0xFFFF5500),
        onAccent = Color(0xFF1C1C1C),
        brand = Color(0xFFFF5500),
        positive = Color(0xFF556B2F),
        error = Color(0xFFB3261E),
        swatches = listOf(Color(0xFF1C1C1C), Color(0xFF8E8E8E), Color(0xFFF5F5DC), Color(0xFF556B2F), Color(0xFF5B7C99), Color(0xFFFF5500), Color(0xFF0047AB))
    )

    StylePalette.OLD_MONEY -> MonzattiColors(
        isDark = false,
        background = Color(0xFFFFFFFF),
        surface = Color(0xFFFAF7F2),
        surfaceVariant = Color(0xFFD8C3A5),
        ink = Color(0xFF1B263B),
        muted = Color(0xFF555C6B),
        line = Color(0xFFA9A9A9),
        primary = Color(0xFF1B263B),
        onPrimary = Color(0xFFFFFFFF),
        accent = Color(0xFFC19A6B),
        onAccent = Color(0xFF1B263B),
        brand = Color(0xFFD4AF37),
        positive = Color(0xFF1E3F20),
        error = Color(0xFF9B1C1C),
        swatches = listOf(Color(0xFFFFFFFF), Color(0xFFD8C3A5), Color(0xFFA9A9A9), Color(0xFF1B263B), Color(0xFFC19A6B), Color(0xFF1E3F20), Color(0xFFD4AF37))
    )
}
