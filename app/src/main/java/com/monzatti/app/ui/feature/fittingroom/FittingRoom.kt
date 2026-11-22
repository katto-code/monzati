package com.monzatti.app.ui.feature.fittingroom

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.monzatti.app.domain.model.Measurements
import com.monzatti.app.domain.repository.MirrorProjection
import com.monzatti.app.domain.repository.ProfileRepository
import com.monzatti.app.ui.components.AppTextField
import com.monzatti.app.ui.components.SkeletonGrid
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.monzatti.app.domain.repository.MirrorRepository
import com.monzatti.app.ui.components.BackLink
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
import com.monzatti.app.ui.feature.tryon.drawGarmentImage
import com.monzatti.app.ui.components.rememberImageFile
import com.monzatti.app.ui.feature.tryon.drawMannequin
import com.monzatti.app.ui.feature.tryon.mannequinFrame
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 5. Proyección y medidas: las medidas se guardan en el perfil (RF-03). */
@HiltViewModel
class FittingRoomViewModel @Inject constructor(private val profile: ProfileRepository) : ViewModel() {
    private val _measurements = MutableStateFlow<Measurements?>(null)
    val measurements: StateFlow<Measurements?> = _measurements.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            profile.measurements()
                .onSuccess { _measurements.value = it }
                .onFailure { _measurements.value = Measurements(); _message.value = it.message }
        }
    }

    fun save(m: Measurements) = viewModelScope.launch {
        _saving.value = true
        profile.saveMeasurements(m)
            .onSuccess { _measurements.value = m; _message.value = "Medidas guardadas." }
            .onFailure { _message.value = it.message }
        _saving.value = false
    }
}

@Composable
fun FittingRoomScreen(
    onCamera: () -> Unit,
    onMirror: () -> Unit,
    viewModel: FittingRoomViewModel = hiltViewModel()
) {
    val saved by viewModel.measurements.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val c = MonzattiTheme.colors
    ScreenColumn {
        Title("Proyección y medidas")
        SurfaceCard {
            FieldLabel("NUEVO · PROBADOR CON CÁMARA")
            Text("Vístete en tiempo real", color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            MutedText("Tu cámara detecta tu postura y la prenda te sigue al moverte.")
            Box(Modifier.padding(top = 10.dp)) { PrimaryButton("📷  Probar con cámara", onClick = onCamera, accent = true) }
        }
        val m = saved
        if (m == null) {
            SkeletonGrid(cards = 2, cardHeight = 120.dp)
        } else {
            MeasurementsForm(m, saving, onSave = viewModel::save)
        }
        message?.let { MutedText(it) }
        SecondaryButton("◎  Ver mi espejo", onClick = onMirror)
    }
}

@Composable
private fun MeasurementsForm(saved: Measurements, saving: Boolean, onSave: (Measurements) -> Unit) {
    val c = MonzattiTheme.colors
    fun Double?.text() = this?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }.orEmpty()
    var height by rememberSaveable(saved) { mutableStateOf(saved.heightCm.text()) }
    var bust by rememberSaveable(saved) { mutableStateOf(saved.bustCm.text()) }
    var waist by rememberSaveable(saved) { mutableStateOf(saved.waistCm.text()) }
    var hip by rememberSaveable(saved) { mutableStateOf(saved.hipCm.text()) }
    fun String.cm() = replace(',', '.').toDoubleOrNull()
    val draft = Measurements(height.cm(), bust.cm(), waist.cm(), hip.cm())
    // Mismos límites que la tabla profiles, para no mandar algo que la base rechace.
    val invalid = listOf(height to (100.0..230.0), bust to (1.0..300.0), waist to (1.0..300.0), hip to (1.0..300.0))
        .any { (text, range) -> text.isNotBlank() && text.cm()?.let { it in range } != true }

    FieldLabel("TU AVATAR · VISTA FRONTAL")
    Box(
        Modifier.fillMaxWidth().height(230.dp).background(c.surfaceVariant, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        val h = (draft.heightCm ?: 168.0).toFloat().coerceIn(140f, 200f)
        Text("🧍", fontSize = 112.sp, modifier = Modifier.padding(bottom = ((h - 140f) / 3).dp))
    }
    SurfaceCard {
        FieldLabel("TUS MEDIDAS (CM)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MeasureField("Altura", height, { height = it }, Modifier.weight(1f))
            MeasureField("Busto", bust, { bust = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MeasureField("Cintura", waist, { waist = it }, Modifier.weight(1f))
            MeasureField("Cadera", hip, { hip = it }, Modifier.weight(1f))
        }
        if (invalid) ErrorText("Revisa las medidas: la altura va de 100 a 230 cm.")
    }
    InfoCard(draft.suggestedSize?.let { "Talla sugerida por tu busto: $it" } ?: "Escribe tu busto para sugerirte una talla.")
    PrimaryButton("Guardar medidas", onClick = { onSave(draft) }, loading = saving, enabled = !invalid && draft != saved)
}

@Composable
private fun MeasureField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    androidx.compose.foundation.layout.Column(modifier) {
        MutedText(label)
        AppTextField(value, onChange, placeholder = "cm", number = true)
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
        BackLink("Volver", onBack)
        Title("Tu reflejo, reinventado.")
        val status = when {
            item == null -> "En espera"
            !projection.projecting -> "En pausa"
            projection.live -> "Proyectando · aviso enviado"
            else -> "Guardado · sin aviso en vivo"
        }
        InfoCard("◎  MONZATTI Mirror 01 · $status")
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
                val photo = rememberImageFile(item.imagePath)
                Canvas(Modifier.fillMaxSize()) {
                    val body = mannequinFrame(size.width, size.height)
                    drawMannequin(body, Color(0xFF3A3A3A))
                    if (photo != null) drawGarmentImage(body, item.kind, photo) else drawGarment(body, item.kind, Color(item.color))
                }
                Text(
                    "EN VIVO · ${item.name}",
                    color = c.accent,
                    fontSize = 12.sp,
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
