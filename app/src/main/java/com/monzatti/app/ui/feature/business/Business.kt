package com.monzatti.app.ui.feature.business

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.monzatti.app.domain.model.NewProduct
import com.monzatti.app.domain.model.Product
import com.monzatti.app.domain.model.UserProfile
import com.monzatti.app.domain.repository.AuthRepository
import com.monzatti.app.domain.repository.ProductRepository
import com.monzatti.app.domain.shop.BrandOrder
import com.monzatti.app.domain.shop.SalesSummary
import com.monzatti.app.domain.shop.ShopRules
import com.monzatti.app.domain.shop.mxn
import com.monzatti.app.ui.components.AppTextField
import com.monzatti.app.ui.components.ChoiceRow
import com.monzatti.app.ui.components.ErrorText
import com.monzatti.app.ui.components.FieldLabel
import com.monzatti.app.ui.components.InfoCard
import com.monzatti.app.ui.components.MetricCard
import com.monzatti.app.ui.components.MultiChoiceRow
import com.monzatti.app.ui.components.MutedText
import com.monzatti.app.ui.components.PrimaryButton
import com.monzatti.app.ui.components.ScreenColumn
import com.monzatti.app.ui.components.Skeleton
import com.monzatti.app.ui.components.SurfaceCard
import com.monzatti.app.ui.components.Title
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class BusinessViewModel @Inject constructor(
    private val repository: ProductRepository,
    auth: AuthRepository
) : ViewModel() {

    val user: StateFlow<UserProfile?> = auth.currentUser
    /** Solo los productos de las marcas del socio con sesión. */
    val products: StateFlow<List<Product>> = combine(repository.products, auth.currentUser) { list, user ->
        list.filter { user != null && it.ownerId == user.id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _sales = MutableStateFlow<SalesSummary?>(null)
    /** Null mientras carga. */
    val sales: StateFlow<SalesSummary?> = _sales.asStateFlow()

    private val _orders = MutableStateFlow<List<BrandOrder>?>(null)
    /** Null mientras carga. */
    val orders: StateFlow<List<BrandOrder>?> = _orders.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        viewModelScope.launch {
            repository.refresh().onFailure { _error.value = it.message }
            loadSales()
        }
    }

    private suspend fun loadSales() {
        repository.salesSummary()
            .onSuccess { _sales.value = it }
            .onFailure { _error.value = it.message; _sales.value = SalesSummary(0, 0, 0, 0, 0) }
        repository.brandOrders()
            .onSuccess { _orders.value = it }
            .onFailure { _error.value = it.message; _orders.value = emptyList() }
    }

    /** Corre una acción del socio mostrando carga y el error, si lo hay. */
    private fun act(onDone: () -> Unit = {}, block: suspend () -> Result<Unit>) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            block().onSuccess { _error.value = null; onDone() }.onFailure { _error.value = it.message }
            _busy.value = false
        }
    }

    fun publish(name: String, price: String, sizes: Set<String>, stock: String, category: String, onDone: () -> Unit) {
        val parsedPrice = price.trim().toIntOrNull()
        val parsedStock = stock.trim().toIntOrNull()
        when {
            parsedPrice == null || parsedPrice < 0 -> _error.value = "Escribe un precio válido."
            parsedStock == null || parsedStock < 0 -> _error.value = "Escribe existencias válidas."
            else -> act(onDone) {
                repository.publish(NewProduct(name, parsedPrice, GarmentSizes.filter { it in sizes }, parsedStock, category))
            }
        }
    }

    fun save(productId: String, price: String, stock: Map<String, String>, onDone: () -> Unit) {
        val parsedPrice = price.trim().toIntOrNull()
        val parsedStock = stock.mapValues { it.value.trim().toIntOrNull() }
        when {
            parsedPrice == null || parsedPrice < 0 -> _error.value = "Escribe un precio válido."
            parsedStock.values.any { it == null || it < 0 } -> _error.value = "Escribe existencias válidas en cada talla."
            else -> act(onDone) { repository.update(productId, parsedPrice, parsedStock.mapValues { it.value!! }) }
        }
    }

    fun archive(productId: String) = act { repository.archive(productId) }

    fun renameBrand(name: String, onDone: () -> Unit) = act(onDone) { repository.renameBrand(name) }

    fun markShipped(orderId: String) = act {
        repository.markShipped(orderId).onSuccess { loadSales() }
    }
}

/** Las mismas categorías que filtran la tienda (sin zapatos, que el catálogo aún no muestra). */
private val ProductCategories = GarmentCategories.filter { it != "Zapatos" }

private val OrderDay = DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale("es", "MX"))

@Composable
private fun BusinessHeader(onChangeStyle: () -> Unit, onSignOut: () -> Unit) {
    val c = MonzattiTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { FieldLabel("MONZATTI / BUSINESS") }
        TextButton(onClick = onChangeStyle) { Text("✦ Estilo", color = c.muted, fontSize = 11.sp) }
        TextButton(onClick = onSignOut) { Text("Salir", color = c.muted, fontSize = 11.sp) }
    }
}

/** Panel de ventas: cifras, gráfica semanal y pedidos de la marca. */
@Composable
fun SalesScreen(
    onAddProduct: () -> Unit,
    onChangeStyle: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: BusinessViewModel = hiltViewModel()
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val c = MonzattiTheme.colors
    ScreenColumn {
        BusinessHeader(onChangeStyle, onSignOut)
        Title("Hola, ${user?.firstName ?: ""}.")
        MutedText("Tu marca, en movimiento.")
        // shortcut: sin productos el nombre sale del perfil aunque la marca ya se haya renombrado; leer brands si molesta.
        BrandNameCard(products.firstOrNull()?.brand ?: user?.fullName.orEmpty(), busy, viewModel::renameBrand)
        ErrorText(error)
        SurfaceCard {
            FieldLabel("VENTAS TOTALES · MXN")
            val s = sales
            if (s == null) {
                Skeleton(Modifier.fillMaxWidth(0.6f).height(36.dp))
            } else {
                Text(mxn(s.gross).removeSuffix(" MXN"), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = c.ink)
                Text("Neto para tu marca: ${mxn(s.net)} · comisión Monzatti ${mxn(s.commission)}", color = c.positive, fontSize = 12.sp)
            }
            Spacer(Modifier.height(14.dp))
            val o = orders
            if (o != null) {
                val weeks = ShopRules.weeklySales(o, LocalDate.now())
                val max = weeks.max()
                if (max == 0) {
                    MutedText("Aún no hay ventas en las últimas 12 semanas.")
                } else {
                    Row(Modifier.fillMaxWidth().height(50.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
                        weeks.forEach {
                            Box(Modifier.weight(1f).height((2 + 48 * it / max).dp).background(c.accent, RoundedCornerShape(3.dp)))
                        }
                    }
                    MutedText("Ventas por semana · últimas 12 semanas")
                }
            }
            Spacer(Modifier.height(8.dp))
            MutedText("Comisión Monzatti: 12 % (rango 10–15 %) sobre el importe neto de cada prenda.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("PEDIDOS", sales?.orders?.toString() ?: "–", Modifier.weight(1f))
            MetricCard("PIEZAS", sales?.units?.toString() ?: "–", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("PRODUCTOS", products.size.toString(), Modifier.weight(1f))
        }
        PrimaryButton("+  Registrar producto", onClick = onAddProduct, accent = true)
        FieldLabel("PEDIDOS DE TU MARCA")
        when {
            orders == null -> Skeleton(Modifier.fillMaxWidth().height(60.dp))
            orders!!.isEmpty() -> InfoCard("Aún no tienes pedidos.")
            else -> orders!!.forEach { BrandOrderCard(it, busy) { viewModel.markShipped(it.id) } }
        }
    }
}

@Composable
private fun BrandNameCard(name: String, busy: Boolean, onRename: (String, () -> Unit) -> Unit) {
    val c = MonzattiTheme.colors
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    SurfaceCard {
        FieldLabel("TU MARCA")
        if (editing) {
            AppTextField(draft, { draft = it }, placeholder = "Nombre de tu marca")
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton("Guardar", onClick = { onRename(draft) { editing = false } }, loading = busy, modifier = Modifier.weight(1f))
                TextButton(onClick = { editing = false }) { Text("Cancelar", color = c.muted) }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = c.ink)
                TextButton(onClick = { draft = name; editing = true }) { Text("Cambiar nombre", color = c.muted, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun BrandOrderCard(order: BrandOrder, busy: Boolean, onShip: () -> Unit) {
    val c = MonzattiTheme.colors
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(order.shortId, fontWeight = FontWeight.SemiBold, color = c.ink)
                MutedText(order.createdAt.format(OrderDay))
            }
            Text(mxn(order.total), fontWeight = FontWeight.SemiBold, color = c.ink, fontSize = 13.sp)
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = c.line)
        order.lines.forEach { Text("${it.name} · ${it.size} × ${it.quantity}", color = c.muted, fontSize = 12.sp) }
        Spacer(Modifier.height(8.dp))
        if (order.shipped) {
            Text("Enviado ✓", color = c.positive, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        } else {
            PrimaryButton("Marcar como enviado", onClick = onShip, loading = busy)
        }
    }
}

/** Alta de productos. */
@Composable
fun NewProductScreen(onPublished: () -> Unit, viewModel: BusinessViewModel = hiltViewModel()) {
    val error by viewModel.error.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var stock by rememberSaveable { mutableStateOf("") }
    var sizes by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var category by rememberSaveable { mutableStateOf(ProductCategories.first()) }
    ScreenColumn {
        Title("Nuevo producto")
        MutedText("Comparte tu próxima pieza favorita.")
        FieldLabel("NOMBRE")
        AppTextField(name, { name = it }, placeholder = "Ej. Chamarra de lino")
        FieldLabel("PRECIO · MXN")
        AppTextField(price, { price = it }, placeholder = "Ej. 2100", number = true)
        FieldLabel("CATEGORÍA")
        ChoiceRow(ProductCategories, category) { category = it }
        FieldLabel("TALLAS")
        MultiChoiceRow(GarmentSizes, sizes) { size -> sizes = if (size in sizes) sizes - size else sizes + size }
        FieldLabel("EXISTENCIAS POR TALLA")
        AppTextField(stock, { stock = it }, placeholder = "Ej. 5", number = true)
        ErrorText(error)
        PrimaryButton("Publicar producto", onClick = { viewModel.publish(name, price, sizes, stock, category, onPublished) }, accent = true, loading = busy)
        MutedText("El producto aparecerá en el catálogo de la tienda al publicarlo.")
    }
}

/** Gestión de inventario: existencias, precio y baja. */
@Composable
fun InventoryScreen(viewModel: BusinessViewModel = hiltViewModel()) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    ScreenColumn {
        Title("Inventario")
        MutedText("Existencias por producto y talla. Con 0 existencias no se puede comprar.")
        ErrorText(error)
        if (products.isEmpty()) InfoCard("Aún no tienes productos registrados.")
        products.forEach { product ->
            InventoryCard(
                product,
                busy,
                onSave = { price, stock, done -> viewModel.save(product.id, price, stock, done) },
                onArchive = { viewModel.archive(product.id) }
            )
        }
    }
}

@Composable
private fun InventoryCard(
    product: Product,
    busy: Boolean,
    onSave: (String, Map<String, String>, () -> Unit) -> Unit,
    onArchive: () -> Unit
) {
    val c = MonzattiTheme.colors
    var editing by rememberSaveable(product.id) { mutableStateOf(false) }
    var confirmArchive by remember { mutableStateOf(false) }
    // Se reinicia con cada versión del producto, para editar siempre lo último guardado.
    var price by remember(product) { mutableStateOf(product.price.toString()) }
    var stock by remember(product) { mutableStateOf(product.stockBySize.mapValues { it.value.toString() }) }
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(product.name, fontWeight = FontWeight.SemiBold, color = c.ink)
                MutedText("${product.brand} · ${mxn(product.price)}")
            }
            val status = when {
                product.totalStock == 0 -> "Agotado"
                product.isLowStock -> "Bajo"
                else -> "Activo"
            }
            Text(
                "${product.totalStock} · $status",
                color = if (product.totalStock == 0 || product.isLowStock) c.ink else c.positive,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = c.line)
        if (!editing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    product.stockBySize.entries.joinToString("   ") { (size, n) -> "$size: $n" },
                    Modifier.weight(1f),
                    color = c.muted,
                    fontSize = 12.sp
                )
                TextButton(onClick = { editing = true }) { Text("Editar", color = c.muted, fontSize = 12.sp) }
            }
        } else {
            FieldLabel("PRECIO · MXN")
            AppTextField(price, { price = it }, number = true)
            FieldLabel("EXISTENCIAS")
            stock.keys.forEach { size ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(size, Modifier.weight(1f), color = c.ink, fontSize = 13.sp)
                    AppTextField(stock.getValue(size), { stock = stock + (size to it) }, number = true, modifier = Modifier.weight(2f))
                }
            }
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Guardar cambios", onClick = { onSave(price, stock) { editing = false } }, loading = busy)
            Row {
                TextButton(onClick = { editing = false }) { Text("Cancelar", color = c.muted) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { confirmArchive = true }) { Text("Dar de baja", color = c.muted) }
            }
        }
    }
    if (confirmArchive) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text("¿Dar de baja «${product.name}»?") },
            text = { Text("Deja de verse en la tienda y se quita de los carritos. Tus pedidos anteriores se conservan.") },
            confirmButton = { TextButton(onClick = { confirmArchive = false; onArchive() }) { Text("Dar de baja") } },
            dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text("Cancelar") } }
        )
    }
}
