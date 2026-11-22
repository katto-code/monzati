package com.monzatti.app.ui.feature.closet

import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import com.monzatti.app.domain.tryon.GarmentColors
import com.monzatti.app.domain.tryon.toTryOnItem
import com.monzatti.app.ui.components.SecondaryButton
import com.monzatti.app.ui.components.SkeletonGrid
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.monzatti.app.core.theme.MonzattiTheme
import com.monzatti.app.domain.model.Garment
import com.monzatti.app.domain.model.GarmentCategories
import com.monzatti.app.domain.model.GarmentSizes
import com.monzatti.app.domain.repository.ClosetRepository
import com.monzatti.app.ui.components.AppTextField
import com.monzatti.app.ui.components.BackLink
import com.monzatti.app.ui.components.Brand
import com.monzatti.app.ui.components.ChoiceRow
import com.monzatti.app.ui.components.ErrorText
import com.monzatti.app.ui.components.FieldLabel
import com.monzatti.app.ui.components.InfoCard
import com.monzatti.app.ui.components.ItemImage
import com.monzatti.app.ui.components.MutedText
import com.monzatti.app.ui.components.PrimaryButton
import com.monzatti.app.ui.components.ScreenColumn
import com.monzatti.app.ui.components.SurfaceCard
import com.monzatti.app.ui.components.Title
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ClosetViewModel @Inject constructor(private val closet: ClosetRepository) : ViewModel() {

    val garments: StateFlow<List<Garment>> = closet.garments

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    init {
        viewModelScope.launch {
            closet.refresh().onFailure { _error.value = it.message }
            _loading.value = false
        }
    }

    fun toggleFavorite(id: String) = viewModelScope.launch {
        closet.toggleFavorite(id).onFailure { _error.value = it.message }
    }

    fun update(garment: Garment, onDone: () -> Unit) = viewModelScope.launch {
        _saving.value = true
        closet.update(garment.id, garment.name, garment.category, garment.size, garment.color)
            .onSuccess { onDone() }
            .onFailure { _error.value = it.message }
        _saving.value = false
    }

    fun delete(id: String, onDone: () -> Unit) = viewModelScope.launch {
        _saving.value = true
        closet.delete(id)
            .onSuccess { onDone() }
            .onFailure { _error.value = it.message }
        _saving.value = false
    }

    fun add(name: String, category: String, size: String, color: String, onDone: () -> Unit) = viewModelScope.launch {
        _saving.value = true
        closet.add(name, category, size, color)
            .onSuccess { onDone() }
            .onFailure { _error.value = it.message }
        _saving.value = false
    }
}

@Composable
fun ClosetScreen(
    onAdd: () -> Unit,
    onTryOn: (String?) -> Unit,
    onChangeStyle: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: ClosetViewModel = hiltViewModel()
) {
    val garments by viewModel.garments.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    var category by rememberSaveable { mutableStateOf("Todo") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val c = MonzattiTheme.colors

    garments.firstOrNull { it.id == editingId }?.let { garment ->
        EditGarmentDialog(garment, viewModel, onClose = { editingId = null })
    }

    ScreenColumn {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f)) { Brand() }
            TextButton(onClick = onChangeStyle) {
                Icon(Icons.Outlined.Palette, contentDescription = null, tint = c.muted, modifier = Modifier.size(16.dp))
                Text(" Cambiar estilo", color = c.muted, fontSize = 12.sp)
            }
            TextButton(onClick = onSignOut) { Text("Salir", color = c.muted, fontSize = 12.sp) }
        }
        Title("Mi closet")
        MutedText("${garments.size} prendas · Infinitas posibilidades")
        SecondaryButton("Probador con cámara", icon = Icons.Outlined.PhotoCamera, onClick = { onTryOn(null) })
        ChoiceRow(listOf("Todo") + GarmentCategories, category) { category = it }
        ErrorText(error)
        val visible = garments.filter { category == "Todo" || it.category == category }
        if (loading) {
            SkeletonGrid(cards = 4, cardHeight = 220.dp)
        } else if (visible.isEmpty()) {
            InfoCard("Aún no tienes prendas en esta categoría.")
        } else {
            val rows = (visible.size + 1) / 2
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().height((rows * 262).dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                userScrollEnabled = false
            ) {
                items(visible, key = { it.id }) { garment ->
                    SurfaceCard {
                        ItemImage(garment.emoji, 88.dp, imagePath = garment.imagePath)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(garment.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = c.ink)
                            TextButton(onClick = { viewModel.toggleFavorite(garment.id) }, contentPadding = PaddingValues(0.dp)) {
                                Icon(
                                    if (garment.favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = if (garment.favorite) "Quitar de favoritas" else "Marcar como favorita",
                                    tint = c.accent
                                )
                            }
                        }
                        MutedText(garment.detail)
                        if (garment.toTryOnItem() != null) {
                            TextButton(onClick = { onTryOn(garment.id) }, contentPadding = PaddingValues(0.dp)) {
                                Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = c.muted, modifier = Modifier.size(16.dp))
                                Text(" Probármela", color = c.muted, fontSize = 12.sp)
                            }
                        }
                        TextButton(onClick = { editingId = garment.id }, contentPadding = PaddingValues(0.dp)) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, tint = c.muted, modifier = Modifier.size(16.dp))
                            Text(" Editar", color = c.muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        PrimaryButton("+  Nueva prenda", onClick = onAdd, accent = true)
    }
}

@Composable
private fun EditGarmentDialog(garment: Garment, viewModel: ClosetViewModel, onClose: () -> Unit) {
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var name by rememberSaveable(garment.id) { mutableStateOf(garment.name) }
    var category by rememberSaveable(garment.id) { mutableStateOf(garment.category) }
    var size by rememberSaveable(garment.id) { mutableStateOf(garment.size.ifBlank { "M" }) }
    var color by rememberSaveable(garment.id) { mutableStateOf(garment.color.ifBlank { "Negro" }) }
    var confirmDelete by rememberSaveable(garment.id) { mutableStateOf(false) }
    val c = MonzattiTheme.colors

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = c.background,
        title = { Text(if (confirmDelete) "¿Borrar «${garment.name}»?" else "Editar prenda", color = c.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (confirmDelete) {
                    MutedText("Se borra de tu closet y su foto de tu cuenta. No se puede deshacer.")
                } else {
                    FieldLabel("NOMBRE")
                    AppTextField(name, { name = it })
                    FieldLabel("CATEGORÍA")
                    ChoiceRow(GarmentCategories, category) { category = it }
                    FieldLabel("COLOR")
                    ColorChoiceRow(color) { color = it }
                    FieldLabel("TALLA")
                    ChoiceRow(GarmentSizes, size) { size = it }
                }
                ErrorText(error)
            }
        },
        confirmButton = {
            if (confirmDelete) {
                TextButton(onClick = { viewModel.delete(garment.id, onClose) }, enabled = !saving) { Text("Borrar", color = c.accent) }
            } else {
                TextButton(
                    onClick = { viewModel.update(garment.copy(name = name, category = category, size = size, color = color), onClose) },
                    enabled = !saving
                ) { Text("Guardar", color = c.accent) }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (confirmDelete) confirmDelete = false else confirmDelete = true }) {
                Text(if (confirmDelete) "Cancelar" else "Borrar prenda", color = c.muted)
            }
        }
    )
}

@Composable
internal fun ColorChoiceRow(selected: String, onSelect: (String) -> Unit) {
    val c = MonzattiTheme.colors
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        GarmentColors.named.forEach { (name, argb) ->
            val isSelected = name == selected
            Row(
                Modifier
                    .background(if (isSelected) c.accent else c.surface, CircleShape)
                    .border(1.dp, if (isSelected) c.accent else c.line, CircleShape)
                    .clickable { onSelect(name) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(14.dp).background(Color(argb), CircleShape).border(1.dp, c.line, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(name, fontSize = 12.sp, color = if (isSelected) c.onAccent else c.ink)
            }
        }
    }
}
