package com.monzatti.app.ui.feature.fittingroom

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.monzatti.app.core.theme.MonzattiTheme
import com.monzatti.app.domain.model.GarmentSizes
import com.monzatti.app.domain.repository.MirrorProjection
import com.monzatti.app.domain.repository.MirrorRepository
import com.monzatti.app.ui.components.BackLink
import com.monzatti.app.ui.components.ChoiceRow
import com.monzatti.app.ui.components.ErrorText
import com.monzatti.app.ui.components.FieldLabel
import com.monzatti.app.ui.components.InfoCard
import com.monzatti.app.ui.components.MutedText
import com.monzatti.app.ui.components.PrimaryButton
import com.monzatti.app.ui.components.ScreenColumn
import com.monzatti.app.ui.components.SecondaryButton
import com.monzatti.app.ui.components.SurfaceCard
import com.monzatti.app.ui.components.Title
import com.monzatti.app.ui.feature.tryon.drawGarment
import com.monzatti.app.ui.feature.tryon.drawMannequin
import com.monzatti.app.ui.feature.tryon.mannequinFrame
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FittingState(val heightCm: Float = 168f, val size: String = "M")

/** 5. Proyección y medidas. */
@HiltViewModel
class FittingRoomViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(FittingState())
    val state: StateFlow<FittingState> = _state.asStateFlow()

    fun setHeight(value: Float) = _state.update { it.copy(heightCm = value) }
    fun setSize(value: String) = _state.update { it.copy(size = value) }
}

@Composable
fun FittingRoomScreen(
    onCamera: () -> Unit,
    onMirror: () -> Unit,
    viewModel: FittingRoomViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = MonzattiTheme.colors
    ScreenColumn {
        Title("Proyección y medidas")
        SurfaceCard {
            FieldLabel("NUEVO · PROBADOR CON CÁMARA")
            Text("Vístete en tiempo real", color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            MutedText("Tu cámara detecta tu postura y la prenda te sigue al moverte.")
            Box(Modifier.padding(top = 10.dp)) { PrimaryButton("📷  Probar con cámara", onClick = onCamera, accent = true) }
        }
        FieldLabel("TU AVATAR · VISTA FRONTAL")
        Box(
            Modifier.fillMaxWidth().height(230.dp).background(c.surfaceVariant, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("🧍", fontSize = 112.sp, modifier = Modifier.padding(bottom = ((state.heightCm - 150f) / 3).dp))
        }
        SurfaceCard {
            FieldLabel("TUS MEDIDAS")
            Text("Altura: ${state.heightCm.toInt()} cm", fontWeight = FontWeight.SemiBold, color = c.ink)
            Slider(value = state.heightCm, onValueChange = viewModel::setHeight, valueRange = 150f..190f)
            MutedText("Busto 90 cm · Cintura 70 cm")
        }
        FieldLabel("TALLA")
        ChoiceRow(GarmentSizes, state.size, viewModel::setSize)
        InfoCard("Ajuste recomendado: ${state.size}")
        SecondaryButton("◎  Ver mi espejo", onClick = onMirror)
    }
}

/** 8. Espejo inteligente: muestra lo proyectado desde el probador (RF-02, tabla mirror_sessions). */
@HiltViewModel
class MirrorViewModel @Inject constructor(private val mirror: MirrorRepository) : ViewModel() {
    val projection: StateFlow<MirrorProjection> = mirror.projection

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun toggle() {
        val current = projection.value
        viewModelScope.launch {
            val result = when {
                current.projecting -> mirror.stop()
                current.item != null -> mirror.project(current.item)
                else -> Result.success(Unit)
            }
            _error.value = result.exceptionOrNull()?.message
        }
    }
}

@Composable
fun MirrorScreen(onBack: () -> Unit, onCamera: () -> Unit, viewModel: MirrorViewModel = hiltViewModel()) {
    val projection by viewModel.projection.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val c = MonzattiTheme.colors
    val item = projection.item
    ScreenColumn {
        BackLink("← Volver", onBack)
        Title("Tu reflejo, reinventado.")
        InfoCard("◎  MONZATTI Mirror 01 · ${if (projection.projecting) "Proyectando" else "Conectado"}")
        // La pantalla del espejo siempre es oscura, como el hardware real.
        Box(
            Modifier
                .fillMaxWidth()
                .height(340.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF111111)),
            contentAlignment = Alignment.Center
        ) {
            if (projection.projecting && item != null) {
                Canvas(Modifier.fillMaxSize()) {
                    val body = mannequinFrame(size.width, size.height)
                    drawMannequin(body, Color(0xFF3A3A3A))
                    drawGarment(body, item.kind, Color(item.color))
                }
                Text(
                    "EN VIVO · ${item.name}",
                    color = c.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                )
            } else {
                Text(
                    if (item == null) "Pantalla en espera\nElige una prenda en el probador y toca «Al espejo»" else "Proyección en pausa",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp
                )
            }
        }
        ErrorText(error)
        MutedText("Cambia tu look desde el móvil y visualízalo al instante en tu espejo.")
        if (item == null) {
            PrimaryButton("📷  Abrir probador con cámara", onClick = onCamera, accent = true)
        } else {
            PrimaryButton(
                if (projection.projecting) "Detener proyección" else "Reanudar proyección",
                onClick = viewModel::toggle,
                accent = true
            )
            SecondaryButton("Elegir otra prenda", onClick = onCamera)
        }
    }
}
