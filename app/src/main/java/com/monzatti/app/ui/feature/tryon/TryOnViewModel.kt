package com.monzatti.app.ui.feature.tryon

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.monzatti.app.domain.model.GarmentSizes
import com.monzatti.app.domain.repository.ClosetRepository
import com.monzatti.app.domain.repository.MirrorProjection
import com.monzatti.app.domain.repository.MirrorRepository
import com.monzatti.app.domain.repository.ProductRepository
import com.monzatti.app.domain.shop.CartRepository
import com.monzatti.app.domain.tryon.TryOnItem
import com.monzatti.app.domain.tryon.TryOnSource
import com.monzatti.app.domain.tryon.toTryOnItem
import com.monzatti.app.ui.navigation.TryOnRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Prendas probables de la tienda y del closet, la seleccionada, y acciones de bolsa y espejo. */
@HiltViewModel
class TryOnViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val products: ProductRepository,
    private val cart: CartRepository,
    closet: ClosetRepository,
    private val mirror: MirrorRepository
) : ViewModel() {

    val items: StateFlow<List<TryOnItem>> =
        combine(products.products, closet.garments) { shop, mine ->
            shop.mapNotNull { it.toTryOnItem() } + mine.mapNotNull { it.toTryOnItem() }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val selectedId = MutableStateFlow(savedStateHandle.toRoute<TryOnRoute>().startItemId)

    val selected: StateFlow<TryOnItem?> =
        combine(items, selectedId) { list, id -> list.firstOrNull { it.id == id } ?: list.firstOrNull() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val projection: StateFlow<MirrorProjection> = mirror.projection

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch { products.refresh() }
        viewModelScope.launch { closet.refresh() }
    }

    fun select(id: String) {
        selectedId.value = id
        _message.value = null
    }

    /** Desliza a la siguiente (+1) o anterior (-1) prenda, en círculo. */
    fun step(direction: Int) {
        val list = items.value
        if (list.isEmpty()) return
        val index = list.indexOfFirst { it.id == selected.value?.id }.coerceAtLeast(0)
        select(list[(index + direction).mod(list.size)].id)
    }

    fun addToBag() {
        val item = selected.value ?: return
        if (item.source != TryOnSource.SHOP) return
        val product = products.products.value.firstOrNull { it.id == item.id } ?: return
        // Talla M si hay; si no, la primera disponible (RF-01: nunca una agotada).
        val size = listOf("M") + GarmentSizes
        val available = size.firstOrNull { product.isAvailable(it) }
        if (available == null) {
            _message.value = "${item.name} está agotada."
            return
        }
        viewModelScope.launch {
            _message.value = cart.add(item.id, available)
                .fold({ "Añadida al carrito · talla $available" }, { it.message })
        }
    }

    fun projectToMirror() {
        val item = selected.value ?: return
        viewModelScope.launch {
            _message.value = mirror.project(item)
                .fold({ "Proyectando en Mirror 01" }, { it.message ?: "No se pudo conectar con el espejo." })
        }
    }
}
