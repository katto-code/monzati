package com.monzatti.app.ui.feature.tryon

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import android.content.Context
import android.graphics.Bitmap
import com.monzatti.app.data.remote.AiTryOnClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
    private val mirror: MirrorRepository,
    private val ai: AiTryOnClient,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _ai = MutableStateFlow<AiPhotoState>(AiPhotoState.Idle)
    val aiState: StateFlow<AiPhotoState> = _ai.asStateFlow()
    private var consentGiven = false

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

    /** Botón "Foto con IA": pide consentimiento la primera vez y luego hace la cuenta regresiva. */
    fun startAiPhoto(capture: suspend () -> Bitmap) {
        if (selected.value == null || !com.monzatti.app.BuildConfig.AI_PHOTOS_ENABLED) return
        if (!ai.isConfigured) {
            _ai.value = AiPhotoState.Failed(
                "El servidor de IA no está configurado. Agrega TRYON_SERVER_URL y TRYON_SERVER_TOKEN en " +
                    "local.properties (ver tryon-server/README.md)."
            )
            return
        }
        if (!consentGiven) {
            _ai.value = AiPhotoState.Consent
            pendingCapture = capture
            return
        }
        runAiPhoto(capture)
    }

    private var pendingCapture: (suspend () -> Bitmap)? = null

    fun acceptConsent() {
        consentGiven = true
        pendingCapture?.let { runAiPhoto(it) }
        pendingCapture = null
    }

    private fun runAiPhoto(capture: suspend () -> Bitmap) {
        val item = selected.value ?: return
        viewModelScope.launch {
            for (s in 3 downTo 1) {
                _ai.value = AiPhotoState.Countdown(s)
                delay(1000)
            }
            _ai.value = AiPhotoState.Generating
            val result = runCatching {
                val person = capture()
                val (personBytes, garmentBytes) = withContext(Dispatchers.Default) {
                    person.toJpeg() to garmentImageBytes(item)
                }
                val jpeg = ai.generate(personBytes, garmentBytes, item.kind.serverCategory()).getOrThrow()
                withContext(Dispatchers.Default) { decodeJpeg(jpeg) }
            }
            _ai.value = result.fold(
                { AiPhotoState.Result(it) },
                { AiPhotoState.Failed(it.message ?: "No se pudo generar la foto.") }
            )
        }
    }

    /** Guarda la foto generada en "mis looks" (almacenamiento privado de la app). */
    fun saveAiPhoto() {
        val state = _ai.value as? AiPhotoState.Result ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val dir = File(context.filesDir, "looks").apply { mkdirs() }
            File(dir, "look-${System.currentTimeMillis()}.jpg").writeBytes(state.bitmap.toJpeg(maxSide = 2048, quality = 95))
            _ai.value = state.copy(saved = true)
        }
    }

    fun dismissAi() {
        _ai.value = AiPhotoState.Idle
        pendingCapture = null
    }

    fun projectToMirror() {
        val item = selected.value ?: return
        viewModelScope.launch {
            _message.value = mirror.project(item)
                .fold({ "Proyectando en Mirror 01" }, { it.message ?: "No se pudo conectar con el espejo." })
        }
    }
}
