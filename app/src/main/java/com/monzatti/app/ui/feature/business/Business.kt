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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.monzatti.app.domain.model.GarmentSizes
import com.monzatti.app.domain.model.NewProduct
import com.monzatti.app.domain.model.Product
import com.monzatti.app.domain.model.UserProfile
import com.monzatti.app.domain.repository.AuthRepository
import com.monzatti.app.domain.repository.ProductRepository
import com.monzatti.app.ui.components.AppTextField
import com.monzatti.app.ui.components.ErrorText
import com.monzatti.app.ui.components.FieldLabel
import com.monzatti.app.ui.components.InfoCard
import com.monzatti.app.ui.components.MetricCard
import com.monzatti.app.ui.components.MultiChoiceRow
import com.monzatti.app.ui.components.MutedText
import com.monzatti.app.ui.components.PrimaryButton
import com.monzatti.app.ui.components.ScreenColumn
import com.monzatti.app.ui.components.SurfaceCard
import com.monzatti.app.ui.components.Title
import com.monzatti.app.ui.components.Skeleton
import com.monzatti.app.domain.shop.SalesSummary
import com.monzatti.app.domain.shop.mxn
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class BusinessViewModel @Inject constructor(
    private val repository: ProductRepository,
    auth: AuthRepository
) : ViewModel() {

    val user: StateFlow<UserProfile?> = auth.currentUser
    val products: StateFlow<List<Product>> = repository.products

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _sales = MutableStateFlow<SalesSummary?>(null)
    /** Null mientras carga. */
    val sales: StateFlow<SalesSummary?> = _sales.asStateFlow()

    private val _publishing = MutableStateFlow(false)
    val publishing: StateFlow<Boolean> = _publishing.asStateFlow()

    init {
        viewModelScope.launch {
            repository.refresh().onFailure { _error.value = it.message }
            repository.salesSummary()
                .onSuccess { _sales.value = it }
                .onFailure { _error.value = it.message; _sales.value = SalesSummary(0, 0, 0, 0, 0) }
        }
    }

    fun publish(name: String, price: String, sizes: Set<String>, stock: String, onDone: () -> Unit) {
        val parsedPrice = price.trim().toIntOrNull()
        val parsedStock = stock.trim().toIntOrNull()
        when {
            parsedPrice == null || parsedPrice < 0 -> _error.value = "Escribe un precio válido."
            parsedStock == null || parsedStock < 0 -> _error.value = "Escribe existencias válidas."
            else -> viewModelScope.launch {
                _publishing.value = true
                repository.publish(NewProduct(name, parsedPrice, GarmentSizes.filter { it in sizes }, parsedStock))
                    .onSuccess { _error.value = null; onDone() }
                    .onFailure { _error.value = it.message }
                _publishing.value = false
            }
        }
    }
}

@Composable
private fun BusinessHeader(onChangeStyle: () -> Unit, onSignOut: () -> Unit) {
    val c = MonzattiTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { FieldLabel("MONZATTI / BUSINESS") }
        TextButton(onClick = onChangeStyle) { Text("✦ Estilo", color = c.muted, fontSize = 11.sp) }
        TextButton(onClick = onSignOut) { Text("Salir", color = c.muted, fontSize = 11.sp) }
    }
}

/** Panel de ventas (las cifras de ventas y pedidos siguen siendo de ejemplo). */
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
    val c = MonzattiTheme.colors
    ScreenColumn {
        BusinessHeader(onChangeStyle, onSignOut)
        Title("Hola, ${user?.firstName ?: "Mariana"}.")
        MutedText("Tu marca, en movimiento.")
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
                listOf(10, 15, 12, 20, 16, 26, 33, 29, 38, 35, 43, 50).forEach {
                    Box(Modifier.weight(1f).height(it.dp).background(c.accent, RoundedCornerShape(3.dp)))
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
    }
}

/** Alta de productos. */
@Composable
fun NewProductScreen(onPublished: () -> Unit, viewModel: BusinessViewModel = hiltViewModel()) {
    val error by viewModel.error.collectAsStateWithLifecycle()
    val publishing by viewModel.publishing.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("Chamarra de lino") }
    var price by rememberSaveable { mutableStateOf("2100") }
    var stock by rememberSaveable { mutableStateOf("5") }
    var sizes by rememberSaveable { mutableStateOf(setOf("S", "M", "L")) }
    ScreenColumn {
        Title("Nuevo producto")
        MutedText("Comparte tu próxima pieza favorita.")
        FieldLabel("NOMBRE")
        AppTextField(name, { name = it })
        FieldLabel("PRECIO · MXN")
        AppTextField(price, { price = it }, number = true)
        FieldLabel("TALLAS")
        MultiChoiceRow(GarmentSizes, sizes) { size -> sizes = if (size in sizes) sizes - size else sizes + size }
        FieldLabel("EXISTENCIAS POR TALLA")
        AppTextField(stock, { stock = it }, number = true)
        ErrorText(error)
        PrimaryButton("Publicar producto", onClick = { viewModel.publish(name, price, sizes, stock, onPublished) }, accent = true, loading = publishing)
        MutedText("El producto aparecerá en el catálogo de la tienda al publicarlo.")
    }
}

/** Gestión de inventario. */
@Composable
fun InventoryScreen(viewModel: BusinessViewModel = hiltViewModel()) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val c = MonzattiTheme.colors
    ScreenColumn {
        Title("Inventario")
        MutedText("Existencias por producto y talla. Con 0 existencias no se puede comprar.")
        if (products.isEmpty()) InfoCard("Aún no tienes productos registrados.")
        products.forEach { product ->
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
                Text(
                    product.stockBySize.entries.joinToString("   ") { (size, stock) -> "$size: $stock" },
                    color = c.muted,
                    fontSize = 12.sp
                )
            }
        }
    }
}
