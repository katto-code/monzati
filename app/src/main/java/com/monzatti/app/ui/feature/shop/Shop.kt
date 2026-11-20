package com.monzatti.app.ui.feature.shop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.monzatti.app.core.theme.MonzattiTheme
import com.monzatti.app.domain.model.GarmentSizes
import com.monzatti.app.domain.model.Product
import com.monzatti.app.domain.repository.ProductRepository
import com.monzatti.app.domain.tryon.toTryOnItem
import com.monzatti.app.ui.components.AppTextField
import com.monzatti.app.ui.components.BackLink
import com.monzatti.app.ui.components.ChoiceRow
import com.monzatti.app.ui.components.ErrorText
import com.monzatti.app.ui.components.FieldLabel
import com.monzatti.app.ui.components.InfoCard
import com.monzatti.app.ui.components.ItemImage
import com.monzatti.app.ui.components.MutedText
import com.monzatti.app.ui.components.PrimaryButton
import com.monzatti.app.ui.components.ScreenColumn
import com.monzatti.app.ui.components.Title
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ShopViewModel @Inject constructor(private val repository: ProductRepository) : ViewModel() {

    val products: StateFlow<List<Product>> = repository.products
    val bagCount: StateFlow<Int> = repository.bagCount

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch { repository.refresh().onFailure { _message.value = it.message } }
    }

    fun addToBag(productId: String, size: String) = viewModelScope.launch {
        _message.value = repository.addToBag(productId, size).exceptionOrNull()?.message
    }

    fun clearMessage() {
        _message.value = null
    }
}

/** 6. Tienda virtual / marketplace. */
@Composable
fun ShopScreen(onProduct: (String) -> Unit, onTryOn: (String) -> Unit, viewModel: ShopViewModel = hiltViewModel()) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val bagCount by viewModel.bagCount.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var search by rememberSaveable { mutableStateOf("") }
    val c = MonzattiTheme.colors

    ScreenColumn {
        Title("La selección")
        MutedText("Talento local. Estilo sin fronteras.")
        AppTextField(search, { search = it }, placeholder = "Busca prendas o marcas")
        ErrorText(message)
        val filtered = products.filter {
            it.name.contains(search, ignoreCase = true) || it.brand.contains(search, ignoreCase = true)
        }
        if (filtered.isEmpty()) {
            InfoCard("No encontramos productos con esa búsqueda.")
        } else {
            val rows = (filtered.size + 1) / 2
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().height((rows * 230).dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                userScrollEnabled = false
            ) {
                items(filtered, key = { it.id }) { product ->
                    Card(onClick = { onProduct(product.id) }, colors = CardDefaults.cardColors(containerColor = c.surface)) {
                        Column(Modifier.padding(10.dp)) {
                            ItemImage(product.emoji, 86.dp)
                            FieldLabel(product.brand.uppercase())
                            Text(product.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = c.ink)
                            Text("$${product.price} MXN", fontSize = 12.sp, color = c.ink)
                            if (product.totalStock == 0) MutedText("Agotado")
                            if (product.toTryOnItem() != null) {
                                TextButton(onClick = { onTryOn(product.id) }, contentPadding = PaddingValues(0.dp)) {
                                    Text("📷 Probar con cámara", color = c.muted, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        MutedText("Bolsa: $bagCount artículo(s)")
    }
}

@Composable
fun ProductScreen(
    productId: String,
    onBack: () -> Unit,
    onTryOn: (String) -> Unit,
    viewModel: ShopViewModel = hiltViewModel()
) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val bagCount by viewModel.bagCount.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var size by rememberSaveable { mutableStateOf("M") }
    val product = products.firstOrNull { it.id == productId }

    ScreenColumn {
        BackLink("← Catálogo", onBack)
        if (product == null) {
            InfoCard("Este producto ya no está disponible.")
        } else {
            ProductDetail(product, size, bagCount, message, onSize = { size = it; viewModel.clearMessage() }, onTryOn = { onTryOn(product.id) }) {
                viewModel.addToBag(product.id, size)
            }
        }
    }
}

@Composable
private fun ProductDetail(
    product: Product,
    size: String,
    bagCount: Int,
    message: String?,
    onSize: (String) -> Unit,
    onTryOn: () -> Unit,
    onBag: () -> Unit
) {
    run {
        ItemImage(product.emoji, 170.dp, emojiSize = 76)
        FieldLabel("${product.brand.uppercase()} · DISEÑO LOCAL")
        Title(product.name)
        Text("$${product.price} MXN", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MonzattiTheme.colors.ink)
        FieldLabel("ELIGE TU TALLA")
        ChoiceRow(GarmentSizes, size, onSize)
        val available = product.isAvailable(size)
        MutedText(if (available) "${product.stockBySize[size]} disponibles en talla $size" else "Talla $size agotada")
        ErrorText(message)
        if (product.toTryOnItem() != null) PrimaryButton("📷  Probar con cámara", onClick = onTryOn, accent = true)
        PrimaryButton(
            if (available) "Añadir a bolsa ($bagCount)" else "Agotado",
            onClick = onBag,
            enabled = available
        )
    }
}
