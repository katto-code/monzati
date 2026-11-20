package com.monzatti.app.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.monzatti.app.R
import com.monzatti.app.domain.model.StylePalette

val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold)
)

private val MonzattiTypography: Typography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Poppins),
        displayMedium = base.displayMedium.copy(fontFamily = Poppins),
        displaySmall = base.displaySmall.copy(fontFamily = Poppins),
        headlineLarge = base.headlineLarge.copy(fontFamily = Poppins),
        headlineMedium = base.headlineMedium.copy(fontFamily = Poppins),
        headlineSmall = base.headlineSmall.copy(fontFamily = Poppins),
        titleLarge = base.titleLarge.copy(fontFamily = Poppins),
        titleMedium = base.titleMedium.copy(fontFamily = Poppins),
        titleSmall = base.titleSmall.copy(fontFamily = Poppins),
        bodyLarge = base.bodyLarge.copy(fontFamily = Poppins),
        bodyMedium = base.bodyMedium.copy(fontFamily = Poppins),
        bodySmall = base.bodySmall.copy(fontFamily = Poppins),
        labelLarge = base.labelLarge.copy(fontFamily = Poppins),
        labelMedium = base.labelMedium.copy(fontFamily = Poppins),
        labelSmall = base.labelSmall.copy(fontFamily = Poppins)
    )
}

private val LocalMonzattiColors = staticCompositionLocalOf { StylePalette.MONZATTI.colors() }

object MonzattiTheme {
    val colors: MonzattiColors
        @Composable @ReadOnlyComposable get() = LocalMonzattiColors.current
}

@Composable
fun MonzattiTheme(palette: StylePalette, content: @Composable () -> Unit) {
    val c = palette.colors()
    // En Material, "primary" se usa para foco, cursores, sliders y enlaces: usamos el color de texto para que
    // siempre se lea. Los botones de Monzatti usan los tokens primary/accent directamente.
    val scheme = if (c.isDark) {
        darkColorScheme(
            primary = c.ink, onPrimary = c.background,
            secondary = c.accent, onSecondary = c.onAccent,
            secondaryContainer = c.accent, onSecondaryContainer = c.onAccent,
            background = c.background, onBackground = c.ink,
            surface = c.surface, onSurface = c.ink,
            surfaceVariant = c.surfaceVariant, onSurfaceVariant = c.muted,
            surfaceContainer = c.surface, surfaceContainerLow = c.surface, surfaceContainerHigh = c.surface,
            outline = c.muted, outlineVariant = c.line
        )
    } else {
        lightColorScheme(
            primary = c.ink, onPrimary = c.background,
            secondary = c.accent, onSecondary = c.onAccent,
            secondaryContainer = c.accent, onSecondaryContainer = c.onAccent,
            background = c.background, onBackground = c.ink,
            surface = c.surface, onSurface = c.ink,
            surfaceVariant = c.surfaceVariant, onSurfaceVariant = c.muted,
            surfaceContainer = c.surface, surfaceContainerLow = c.surface, surfaceContainerHigh = c.surface,
            outline = c.muted, outlineVariant = c.line
        )
    }
    CompositionLocalProvider(LocalMonzattiColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = MonzattiTypography, content = content)
    }
}
