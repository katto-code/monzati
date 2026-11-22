package com.monzatti.app.ui.feature.shop

import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.foundation.layout.size

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import com.monzatti.app.domain.model.GarmentCategories
import com.monzatti.app.domain.model.GarmentSizes
import com.monzatti.app.domain.model.Product
import com.monzatti.app.domain.repository.ProductRepository
import com.monzatti.app.domain.shop.CartRepository
import com.monzatti.app.domain.shop.ShopRules
import com.monzatti.app.domain.shop.mxn
import com.monzatti.app.domain.tryon.toTryOnItem
import com.monzatti.app.ui.components.AppTextField
import com.monzatti.app.ui.components.BackLink
import com.monzatti.app.ui.components.BadgeIconButton
import com.monzatti.app.ui.components.ChoiceRow
import com.monzatti.app.ui.components.ErrorText
import com.monzatti.app.ui.components.FieldLabel
import com.monzatti.app.ui.components.InfoCard
import com.monzatti.app.ui.components.ItemImage
import com.monzatti.app.ui.components.MutedText
import com.monzatti.app.ui.components.PrimaryButton
import com.monzatti.app.ui.components.QuantityStepper
import com.monzatti.app.ui.components.ScreenColumn
import com.monzatti.app.ui.components.SecondaryButton
import com.monzatti.app.ui.components.SkeletonGrid
import com.monzatti.app.ui.components.Title
import com.monzatti.app.ui.components.Toast
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ShopViewModel @Inject constructor(
    private val repository: ProductRepository,
    private val cart: CartRepository
) : ViewModel() {

    val products: StateFlow<List<Product>> = repository.products
    val cartCount: StateFlow<Int> = cart.lines.map { lines -> lines.sumOf { it.quantity } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _loading = MutableStateFlow(repository.products.value.isEmpty())
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _adding = MutableStateFlow(false)
    val adding: StateFlow<Boolean> = _adding.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _added = MutableStateFlow<String?>(null)
    /** Texto de confirmación tras añadir al carrito ("Blusa de seda · M"). */
    val added: StateFlow<String?> = _added.asStateFlow()

    init {
        viewModelScope.launch {
            // El catálogo vive en el repositorio (singleton): solo se descarga la primera vez.
            if (repository.products.value.isEmpty()) repository.refresh().onFailure { _message.value = it.message }
            cart.refresh()
            _loading.value = false
        }
    }

    fun addToCart(product: Product, size: String, quantity: Int) {
        if (_adding.value) return
        _adding.value = true
        _message.value = null
        viewModelScope.launch {
            cart.add(product.id, size, quantity)
                .onSuccess { _added.value = "${product.name} · talla $size añadida" }
                .onFailure { _message.value = it.message }
            _adding.value = false
        }
    }

    fun clearMessages() {
        _message.value = null
        _added.value = null
    }
}

private val ShopCategories = listOf("Todo") + GarmentCategories.filter { it != "Zapatos" }

/** 6. Tienda virtual / marketplace. */
@Composable
fun ShopScreen(
    onProduct: (String) -> Unit,
    onTryOn: (String) -> Unit,
    onCart: () -> Unit,
    onOrders: () -> Unit,
    viewModel: ShopViewModel = hiltViewModel()
) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val cartCount by viewModel.cartCount.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Todo") }
    val c = MonzattiTheme.colors

    ScreenColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Title("La selección")
                MutedText("Talento local. Estilo sin fronteras.")
            }
            TextButton(onClick = onOrders) { Text("Pedidos", color = c.muted, fontSize = 12.sp) }
            BadgeIconButton(Icons.Outlined.ShoppingBag, "Carrito", cartCount, onCart)
        }
        AppTextField(search, { search = it }, placeholder = "Busca prendas o marcas")
        ChoiceRow(ShopCategories, category) { category = it }
        ErrorText(message)
        val filtered = products.filter {
            (category == "Todo" || it.category == category) &&
                (it.name.contains(search, ignoreCase = true) || it.brand.contains(search, ignoreCase = true))
        }
        AnimatedContent(
            targetState = when {
                loading -> 0
                filtered.isEmpty() -> 1
                else -> 2
            },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "catalog"
        ) { state ->
            when (state) {
                0 -> SkeletonGrid(cards = 4, cardHeight = 220.dp)
                1 -> InfoCard("No encontramos productos con esa búsqueda.")
                // Filas normales (no lazy): la pantalla ya hace scroll y así cada fila crece con la letra.
                else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    filtered.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { product ->
                                key(product.id) { ProductCard(product, onProduct, onTryOn, Modifier.weight(1f).fillMaxHeight()) }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductCard(product: Product, onProduct: (String) -> Unit, onTryOn: (String) -> Unit, modifier: Modifier) {
    val c = MonzattiTheme.colors
    Card(
        onClick = { onProduct(product.id) },
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = c.surface)
    ) {
        Column(Modifier.padding(10.dp)) {
            Box {
                ItemImage(product.emoji, 92.dp)
                if (product.totalStock == 0) {
                    Text(
                        "AGOTADO",
                        color = c.onPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(c.primary, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            FieldLabel(product.brand.uppercase())
            Text(product.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = c.ink, maxLines = 1)
            Text(mxn(product.price), fontSize = 12.sp, color = c.ink)
            if (product.toTryOnItem() != null) {
                TextButton(onClick = { onTryOn(product.id) }, contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = c.muted, modifier = Modifier.size(16.dp))
                    Text(" Probar con cámara", color = c.muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun ProductScreen(
    productId: String,
    onBack: () -> Unit,
    onTryOn: (String) -> Unit,
    onCart: () -> Unit,
    viewModel: ShopViewModel = hiltViewModel()
) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val cartCount by viewModel.cartCount.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val added by viewModel.added.collectAsStateWithLifecycle()
    val adding by viewModel.adding.collectAsStateWithLifecycle()
    val product = products.firstOrNull { it.id == productId }
    val c = MonzattiTheme.colors

    var size by rememberSaveable(productId) {
        mutableStateOf(product?.let { p -> (listOf("M") + GarmentSizes).firstOrNull { p.isAvailable(it) } } ?: "M")
    }
    var quantity by rememberSaveable(productId) { mutableIntStateOf(1) }

    ScreenColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { BackLink("Catálogo", onBack) }
            BadgeIconButton(Icons.Outlined.ShoppingBag, "Carrito", cartCount, onCart)
        }
        if (product == null) {
            InfoCard("Este producto ya no está disponible.")
        } else {
            ItemImage(product.emoji, 200.dp, emojiSize = 84)
            FieldLabel("${product.brand.uppercase()} · DISEÑO LOCAL")
            Title(product.name)
            Text(mxn(product.price), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = c.ink)
            FieldLabel("ELIGE TU TALLA")
            ChoiceRow(GarmentSizes, size) { size = it; quantity = 1; viewModel.clearMessages() }
            val stock = product.stockBySize[size] ?: 0
            val available = stock > 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                MutedText(
                    when {
                        !available -> "Talla $size agotada"
                        stock <= 3 -> "¡Últimas $stock piezas en talla $size!"
                        else -> "$stock disponibles en talla $size"
                    }
                )
                Box(Modifier.weight(1f))
                if (available) QuantityStepper(quantity, { quantity = ShopRules.clampQuantity(it, stock) }, max = minOf(stock, ShopRules.MAX_QUANTITY))
            }
            ErrorText(message)
            Toast(added, actionLabel = "Ver carrito", onAction = onCart, onDismiss = viewModel::clearMessages)
            PrimaryButton(
                if (available) "Añadir al carrito · ${mxn(product.price * quantity)}" else "Agotado",
                onClick = { viewModel.addToCart(product, size, quantity) },
                enabled = available,
                loading = adding
            )
            if (product.toTryOnItem() != null) SecondaryButton("Probar con cámara", icon = Icons.Outlined.PhotoCamera) { onTryOn(product.id) }
            MutedText("Envío gratis · Pago seguro en modo de prueba · Diseño mexicano")
        }
    }
    LaunchedEffect(productId) { viewModel.clearMessages() }
}
