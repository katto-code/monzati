package com.monzatti.app.ui.feature.assistant

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.runtime.Composable
import com.monzatti.app.ui.components.FieldLabel
import com.monzatti.app.ui.components.InfoCard
import com.monzatti.app.ui.components.MutedText
import com.monzatti.app.ui.components.PrimaryButton
import com.monzatti.app.ui.components.ScreenColumn
import com.monzatti.app.ui.components.Title

/**
 * 4. Asistente de IA: próximamente. Antes respondía frases fijas que parecían reales.
 * RN-03: cuando exista, las sugerencias deben usar al menos 70 % prendas del closet y como máximo 30 % de marcas aliadas.
 */
@Composable
fun AssistantScreen(onTryOn: () -> Unit) {
    ScreenColumn {
        FieldLabel("MONZATTI INTELLIGENCE")
        Title("Tu estilo, elevado.")
        InfoCard("Próximamente: un asistente que arma outfits con las prendas de tu closet.", icon = Icons.Outlined.AutoAwesome)
        MutedText("Mientras tanto, prueba tus prendas sobre tu cuerpo con la cámara.")
        PrimaryButton("Abrir probador", onClick = onTryOn, accent = true)
    }
}
