package com.monzatti.app.ui.feature.about

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.monzatti.app.ui.components.BackLink
import com.monzatti.app.ui.components.FieldLabel
import com.monzatti.app.ui.components.InfoCard
import com.monzatti.app.ui.components.MutedText
import com.monzatti.app.ui.components.PrimaryButton
import com.monzatti.app.ui.components.ScreenColumn
import com.monzatti.app.ui.components.Title
import com.monzatti.app.ui.feature.update.VersionCard

@Composable
fun AboutScreen(onBack: () -> Unit, onNext: () -> Unit) {
    ScreenColumn {
        BackLink("← Volver", onBack)
        FieldLabel("PROYECTO DE TITULACIÓN · UTTEC")
        Title("¿Qué es Monzatti?")
        MutedText("Monzatti es un ecosistema móvil para gestionar tu guardarropa y comprar ropa de marcas locales. Permite digitalizar prendas, armar outfits con asistencia de IA, probar prendas sobre un avatar con tus medidas y comprar en un marketplace.")
        MutedText("Los socios comerciales gestionan productos, inventario y ventas. Un espejo inteligente proyecta el atuendo elegido.")
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Ver objetivo y alcance  →", onClick = onNext, accent = true)
        VersionCard()
    }
}

@Composable
fun ObjectiveScreen(onBack: () -> Unit, onOpen: () -> Unit) {
    ScreenColumn {
        BackLink("← Volver", onBack)
        Title("Objetivo y alcance")
        InfoCard("PARA QUÉ\nDigitalizar el guardarropa · Probar ropa en un avatar · Impulsar marcas locales")
        InfoCard("QUÉ QUEREMOS LOGRAR\nApp móvil con dos tipos de cuenta · Marketplace con comisión · Espejo inteligente conectado")
        InfoCard("CÓMO\nAndroid nativo con Kotlin, Jetpack Compose, Hilt y Supabase")
        MutedText("Calendario del proyecto: noviembre de 2026 a abril de 2027.")
        PrimaryButton("Abrir Monzatti  →", onClick = onOpen, accent = true)
    }
}
