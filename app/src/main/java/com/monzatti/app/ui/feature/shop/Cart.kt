package com.monzatti.app.ui.feature.shop

import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.filled.Check

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.monzatti.app.core.theme.MonzattiTheme
import com.monzatti.app.domain.repository.ProductRepository
import com.monzatti.app.domain.shop.CartLine
import com.monzatti.app.domain.shop.CartRepository
import com.monzatti.app.domain.shop.Order
import com.monzatti.app.domain.shop.ShopRules
import com.monzatti.app.domain.shop.mxn
import com.monzatti.app.ui.components.AppTextField
import com.monzatti.app.ui.components.BackLink
import com.monzatti.app.ui.components.ErrorText
import com.monzatti.app.ui.components.FieldLabel
import com.monzatti.app.ui.components.InfoCard
import com.monzatti.app.ui.components.ItemImage
import com.monzatti.app.ui.components.LoadingOverlay
import com.monzatti.app.ui.components.MutedText
import com.monzatti.app.ui.components.PrimaryButton
import com.monzatti.app.ui.components.QuantityStepper
import com.monzatti.app.ui.components.ScreenColumn
import com.monzatti.app.ui.components.SecondaryButton
import com.monzatti.app.ui.components.Skeleton
import com.monzatti.app.ui.components.SurfaceCard
import com.monzatti.app.ui.components.Title
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado del pago: apartar (RN-02) → pagar (prueba) → pedido. */
sealed interface CheckoutPhase {
    data object Reserving : CheckoutPhase
    data class Reserved(val expiresAt: Long) : CheckoutPhase
    data object Paying : CheckoutPhase
    data class Done(val order: Order) : CheckoutPhase
    data class Failed(val message: String) : CheckoutPhase
}

@HiltViewModel
class CartViewModel @Inject constructor(
    private val cart: CartRepository,
    private val products: ProductRepository
) : ViewModel() {

    val lines: StateFlow<List<CartLine>> = cart.lines
    val orders: StateFlow<List<Order>> = cart.orders

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _phase = MutableStateFlow<CheckoutPhase>(CheckoutPhase.Reserving)
    val phase: StateFlow<CheckoutPhase> = _phase.asStateFlow()

    init {
        viewModelScope.launch {
            cart.refresh().onFailure { _error.value = it.message }
            cart.refreshOrders()
            _loading.value = false
        }
    }

    fun setQuantity(line: CartLine, quantity: Int) = viewModelScope.launch {
        cart.setQuantity(line.id, quantity).onFailure { _error.value = it.message }
    }

    fun remove(line: CartLine) = viewModelScope.launch {
        cart.remove(line.id).onFailure { _error.value = it.message }
    }

    private var checkoutStarted = false

    /** Aparta una sola vez por pantalla de pago; no se repite al rotar ni al recomponer. */
    fun beginCheckout() {
        if (!checkoutStarted) startCheckout()
    }

    fun startCheckout() {
        checkoutStarted = true
        _phase.value = CheckoutPhase.Reserving
        viewModelScope.launch {
            _phase.value = cart.startCheckout().fold(
                { CheckoutPhase.Reserved(it) },
                { CheckoutPhase.Failed(it.message ?: "No pudimos apartar tus prendas.") }
            )
        }
    }

    /** RN-02: al vencer la reserva no se renueva sola; la persona decide si vuelve a intentar. */
    fun expire() {
        if (_phase.value is CheckoutPhase.Reserved) _phase.value = CheckoutPhase.Failed("Tu reserva expiró. Vuelve a intentar el pago.")
    }

    fun pay(address: String, cardName: String) {
        val reserved = _phase.value as? CheckoutPhase.Reserved ?: return
        if (address.isBlank()) {
            _error.value = "Escribe tu dirección de envío."
            return
        }
        if (cardName.isBlank()) {
            _error.value = "Escribe el nombre que aparece en la tarjeta."
            return
        }
        _error.value = null
        _phase.value = CheckoutPhase.Paying
        viewModelScope.launch {
            val ref = "TEST-" + System.currentTimeMillis().toString(36).uppercase()
            cart.confirmOrder(ref, address.trim()).fold(
                {
                    _phase.value = CheckoutPhase.Done(it)
                    products.refresh() // el pago descontó existencias en el servidor
                },
                {
                    _error.value = it.message
                    _phase.value = if (System.currentTimeMillis() > reserved.expiresAt) {
                        CheckoutPhase.Failed(it.message ?: "Tu reserva expiró.")
                    } else {
                        reserved
                    }
                }
            )
        }
    }

    fun clearError() = _error.update { null }

    override fun onCleared() {
        val p = _phase.value
        // viewModelScope ya está cancelado aquí; la liberación debe sobrevivir a la pantalla.
        if (checkoutStarted && p !is CheckoutPhase.Done && p !is CheckoutPhase.Paying) {
            CoroutineScope(Dispatchers.IO).launch { cart.cancelCheckout() }
        }
    }
}

@Composable
fun CartScreen(
    onBack: () -> Unit,
    onCheckout: () -> Unit,
    onShop: () -> Unit,
    viewModel: CartViewModel = hiltViewModel()
) {
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val c = MonzattiTheme.colors

    ScreenColumn {
        BackLink("Seguir comprando", onBack)
        Title("Tu carrito")
        when {
            loading -> repeat(2) { Skeleton(Modifier.fillMaxWidth().height(96.dp), corner = 14.dp) }
            lines.isEmpty() -> {
                Spacer(Modifier.height(24.dp))
                Icon(Icons.Outlined.ShoppingBag, contentDescription = null, tint = c.muted, modifier = Modifier.size(56.dp).align(Alignment.CenterHorizontally))
                Text(
                    "Tu carrito está vacío",
                    color = c.ink,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                MutedText("Descubre piezas de diseñadores locales.")
                PrimaryButton("Ir a la tienda", onClick = onShop, accent = true)
            }
            else -> {
                MutedText("${lines.sumOf { it.quantity }} pieza(s) de ${lines.map { it.brand }.distinct().size} marca(s) locales")
                lines.forEach { line ->
                    CartLineCard(line, onQuantity = { viewModel.setQuantity(line, it) }, onRemove = { viewModel.remove(line) })
                }
                ErrorText(error)
                Summary(lines)
                PrimaryButton("Proceder al pago", onClick = onCheckout, accent = true)
                MutedText("Al pagar apartamos tus prendas por ${ShopRules.RESERVATION_MINUTES} minutos.")
            }
        }
    }
}

@Composable
private fun CartLineCard(line: CartLine, onQuantity: (Int) -> Unit, onRemove: () -> Unit) {
    val c = MonzattiTheme.colors
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(72.dp)) { ItemImage(line.emoji, 72.dp, emojiSize = 32) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                FieldLabel(line.brand.uppercase())
                Text(line.name, color = c.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                MutedText("Talla ${line.size}" + if (line.color.isNotBlank()) " · ${line.color}" else "")
                Text(mxn(line.total), color = c.ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            QuantityStepper(line.quantity, onQuantity, max = minOf(line.stock, ShopRules.MAX_QUANTITY).coerceAtLeast(1))
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onRemove) { Text("Quitar", color = c.muted, fontSize = 12.sp) }
        }
        if (line.stock < line.quantity) MutedText("Solo quedan ${line.stock} en esta talla.")
    }
}

@Composable
private fun Summary(lines: List<CartLine>) {
    val c = MonzattiTheme.colors
    SurfaceCard {
        SummaryRow("Subtotal", mxn(ShopRules.subtotal(lines)))
        SummaryRow("Envío", if (ShopRules.shipping(lines) == 0) "Gratis" else mxn(ShopRules.shipping(lines)))
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = c.line)
        SummaryRow("Total", mxn(ShopRules.total(lines)), strong = true)
    }
}

@Composable
private fun SummaryRow(label: String, value: String, strong: Boolean = false) {
    val c = MonzattiTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = if (strong) c.ink else c.muted, fontSize = if (strong) 16.sp else 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = c.ink, fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium, fontSize = if (strong) 16.sp else 13.sp)
    }
}

@Composable
fun CheckoutScreen(
    onBack: () -> Unit,
    onDone: (String) -> Unit,
    viewModel: CartViewModel = hiltViewModel()
) {
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var address by rememberSaveable { mutableStateOf("") }
    var cardName by rememberSaveable { mutableStateOf("") }
    val c = MonzattiTheme.colors

    LaunchedEffect(Unit) { viewModel.beginCheckout() }
    LaunchedEffect(phase) { (phase as? CheckoutPhase.Done)?.let { onDone(it.order.id) } }

    Box(Modifier.fillMaxSize()) {
        ScreenColumn {
            BackLink("Carrito", onBack)
            Title("Pago")
            when (val p = phase) {
                is CheckoutPhase.Failed -> {
                    InfoCard(p.message, icon = Icons.Outlined.WarningAmber)
                    PrimaryButton("Volver a intentar", onClick = viewModel::startCheckout, accent = true)
                    SecondaryButton("Revisar carrito", onClick = onBack)
                }
                else -> {
                    if (p is CheckoutPhase.Reserved) ReservationTimer(p.expiresAt, onExpired = viewModel::expire)
                    FieldLabel("ENVÍO")
                    AppTextField(address, { address = it }, placeholder = "Calle, número, ciudad")
                    MutedText("Entrega estimada: 3 a 5 días hábiles · Envío gratis")

                    FieldLabel("PAGO")
                    SurfaceCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CreditCard, contentDescription = null, tint = c.ink, modifier = Modifier.size(20.dp))
                            Text("  Tarjeta", color = c.ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(
                                "MODO PRUEBA",
                                color = c.onAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.background(c.accent, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        AppTextField(cardName, { cardName = it }, placeholder = "Nombre en la tarjeta")
                        Spacer(Modifier.height(8.dp))
                        Text("4242 4242 4242 4242   ·   12/30   ·   •••", color = c.muted, fontSize = 13.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.height(6.dp))
                        MutedText("Tarjeta de prueba: no se realiza ningún cargo real.")
                    }

                    FieldLabel("RESUMEN")
                    SurfaceCard {
                        lines.forEach { line ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text("${line.quantity} × ${line.name} (${line.size})", color = c.ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text(mxn(line.total), color = c.ink, fontSize = 13.sp)
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = c.line)
                        Row(Modifier.fillMaxWidth()) {
                            Text("Total", color = c.ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(mxn(ShopRules.total(lines)), color = c.ink, fontWeight = FontWeight.Bold)
                        }
                    }
                    ErrorText(error)
                    PrimaryButton(
                        "Pagar ${mxn(ShopRules.total(lines))}",
                        onClick = { viewModel.pay(address, cardName) },
                        accent = true,
                        enabled = p is CheckoutPhase.Reserved && lines.isNotEmpty(),
                        loading = p is CheckoutPhase.Paying
                    )
                    MutedText("Monzatti cobra una comisión a la marca, no a ti.")
                }
            }
        }
        LoadingOverlay(phase is CheckoutPhase.Reserving, "Apartando tus prendas…")
        LoadingOverlay(phase is CheckoutPhase.Paying, "Procesando pago de prueba…")
    }
}

/** Cuenta regresiva de la reserva (RN-02). */
@Composable
private fun ReservationTimer(expiresAt: Long, onExpired: () -> Unit) {
    val c = MonzattiTheme.colors
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(expiresAt) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= expiresAt) {
                onExpired()
                break
            }
            delay(1000)
        }
    }
    val left = ((expiresAt - now) / 1000).coerceAtLeast(0)
    Row(
        Modifier.fillMaxWidth().background(c.surfaceVariant, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("⏱", fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Text("Apartamos tus prendas por", color = c.ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text("%d:%02d".format(left / 60, left % 60), color = c.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun OrderConfirmedScreen(
    orderId: String,
    onOrders: () -> Unit,
    onShop: () -> Unit,
    viewModel: CartViewModel = hiltViewModel()
) {
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val order = orders.firstOrNull { it.id == orderId }
    val c = MonzattiTheme.colors
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    ScreenColumn {
        Spacer(Modifier.height(32.dp))
        AnimatedVisibility(
            visible = shown,
            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Box(Modifier.size(88.dp).background(c.accent, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(44.dp))
            }
        }
        Text(
            "¡Pedido confirmado!",
            color = c.ink,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        if (order != null) {
            Text(order.shortId, color = c.muted, letterSpacing = 2.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            OrderCard(order)
        }
        MutedText("Te avisaremos cuando la marca envíe tu pedido. Gracias por impulsar el diseño local.")
        PrimaryButton("Ver mis pedidos", onClick = onOrders, accent = true)
        SecondaryButton("Seguir comprando", onClick = onShop)
    }
}

@Composable
fun OrdersScreen(onBack: () -> Unit, onShop: () -> Unit, viewModel: CartViewModel = hiltViewModel()) {
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    ScreenColumn {
        BackLink("Volver", onBack)
        Title("Mis pedidos")
        when {
            loading -> repeat(2) { Skeleton(Modifier.fillMaxWidth().height(120.dp), corner = 14.dp) }
            orders.isEmpty() -> {
                InfoCard("Aún no tienes pedidos. Cuando compres, aparecerán aquí.")
                PrimaryButton("Ir a la tienda", onClick = onShop, accent = true)
            }
            else -> orders.forEach { OrderCard(it) }
        }
    }
}

@Composable
private fun OrderCard(order: Order) {
    val c = MonzattiTheme.colors
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(order.shortId, color = c.ink, fontWeight = FontWeight.SemiBold)
                MutedText(order.createdAt)
            }
            Text(
                order.status.uppercase(),
                color = when (order.status.lowercase()) {
                    "cancelado" -> c.error
                    "pendiente" -> c.muted
                    else -> c.positive
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = c.line)
        order.lines.forEach { line ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text("${line.quantity} × ${line.name} · ${line.size}", color = c.ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(mxn(line.unitPrice * line.quantity), color = c.ink, fontSize = 13.sp)
            }
            MutedText(line.brand)
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = c.line)
        Row(Modifier.fillMaxWidth()) {
            Text("Total", color = c.ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(mxn(order.total), color = c.ink, fontWeight = FontWeight.Bold)
        }
    }
}
