package com.monzatti.app.data.fake

import com.monzatti.app.domain.model.Garment
import com.monzatti.app.domain.model.NewProduct
import com.monzatti.app.domain.model.OutOfStockException
import com.monzatti.app.domain.model.PasswordRules
import com.monzatti.app.domain.model.Product
import com.monzatti.app.domain.model.SignUpRequest
import com.monzatti.app.domain.model.StylePalette
import com.monzatti.app.domain.model.UserProfile
import com.monzatti.app.domain.model.UserRole
import com.monzatti.app.domain.repository.AuthRepository
import com.monzatti.app.domain.repository.ClosetRepository
import com.monzatti.app.domain.repository.MirrorProjection
import com.monzatti.app.domain.repository.MirrorRepository
import com.monzatti.app.domain.shop.CartLine
import com.monzatti.app.domain.shop.CartRepository
import com.monzatti.app.domain.shop.Order
import com.monzatti.app.domain.shop.OrderLine
import com.monzatti.app.domain.shop.SalesSummary
import com.monzatti.app.domain.shop.ShopRules
import com.monzatti.app.domain.tryon.TryOnItem
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import com.monzatti.app.domain.repository.ProductRepository
import com.monzatti.app.domain.repository.ProfileRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/*
 * Implementaciones en memoria. Se usan cuando no hay claves de Supabase en local.properties,
 * así la app funciona como demo sin servidor.
 */

@Singleton
class FakeAuthRepository @Inject constructor() : AuthRepository {

    private val user = MutableStateFlow<UserProfile?>(null)
    override val currentUser: StateFlow<UserProfile?> = user.asStateFlow()

    override suspend fun signIn(email: String, password: String, role: UserRole): Result<UserProfile> {
        if (!email.contains('@')) return Result.failure(IllegalArgumentException("Escribe un correo válido."))
        if (password.isBlank()) return Result.failure(IllegalArgumentException("Escribe tu contraseña."))
        val name = if (role == UserRole.SOCIO) "Mariana" else email.substringBefore('@').replaceFirstChar { it.uppercase() }
        val profile = UserProfile(
            id = "demo-${email.lowercase()}",
            fullName = name,
            email = email,
            role = role,
            gender = null,
            style = null
        )
        user.value = profile
        return Result.success(profile)
    }

    override suspend fun signUp(request: SignUpRequest): Result<UserProfile> {
        if (request.fullName.isBlank()) return Result.failure(IllegalArgumentException("Escribe tu nombre."))
        if (!request.email.contains('@')) return Result.failure(IllegalArgumentException("Escribe un correo válido."))
        if (!PasswordRules.isValid(request.password)) return Result.failure(IllegalArgumentException(PasswordRules.DESCRIPTION))
        val profile = UserProfile(
            id = "demo-${UUID.randomUUID()}",
            fullName = request.fullName.trim(),
            email = request.email.trim(),
            role = request.role,
            gender = request.gender,
            style = null
        )
        user.value = profile
        return Result.success(profile)
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> =
        if (email.contains('@')) Result.success(Unit) else Result.failure(IllegalArgumentException("Escribe un correo válido."))

    override suspend fun signOut() {
        user.value = null
    }

    fun updateStyle(style: StylePalette) {
        user.update { it?.copy(style = style) }
    }
}

@Singleton
class FakeProfileRepository @Inject constructor(private val auth: FakeAuthRepository) : ProfileRepository {
    override suspend fun updateStyle(userId: String, style: StylePalette): Result<Unit> {
        auth.updateStyle(style)
        return Result.success(Unit)
    }
}

@Singleton
class FakeClosetRepository @Inject constructor() : ClosetRepository {

    private val items = MutableStateFlow(
        listOf(
            Garment("g1", "Blusa de seda", "👚", "Blusas", "M", "Marfil · M", color = "Marfil"),
            Garment("g2", "Pantalón sastre", "👖", "Pantalones", "38", "Negro · 38", color = "Negro"),
            Garment("g3", "Vestido midi", "👗", "Vestidos", "M", "Negro · M", color = "Negro"),
            Garment("g4", "Botines de piel", "👢", "Zapatos", "37", "Negro · 37", color = "Negro"),
            Garment("g5", "Chamarra de mezclilla", "🧥", "Chamarras", "M", "Mezclilla · M", color = "Mezclilla")
        )
    )
    override val garments: StateFlow<List<Garment>> = items.asStateFlow()

    override suspend fun refresh(): Result<Unit> = Result.success(Unit)

    override suspend fun add(name: String, category: String, size: String, color: String): Result<Unit> {
        val garment = Garment(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Prenda nueva" },
            emoji = emojiFor(category),
            category = category,
            size = size,
            detail = "$color · $size",
            color = color
        )
        items.update { it + garment }
        return Result.success(Unit)
    }

    override suspend fun toggleFavorite(id: String): Result<Unit> {
        items.update { list -> list.map { if (it.id == id) it.copy(favorite = !it.favorite) else it } }
        return Result.success(Unit)
    }
}

@Singleton
class FakeProductRepository @Inject constructor() : ProductRepository {

    private val items = MutableStateFlow(
        listOf(
            // Marcas ficticias; precios en el rango de las marcas mexicanas analizadas ($1,500 a $5,000 MXN).
            Product("p1", "Francesa", "Blusa de seda", "👚", "Blusas", 2290, mapOf("XS" to 2, "S" to 5, "M" to 6, "L" to 4, "XL" to 1), "Rosa pastel"),
            Product("p2", "Nómada", "Vestido midi", "👗", "Vestidos", 3490, mapOf("XS" to 0, "S" to 1, "M" to 2, "L" to 0, "XL" to 0), "Vino"),
            Product("p3", "Raíz", "Pantalón sastre", "👖", "Pantalones", 1890, mapOf("XS" to 0, "S" to 0, "M" to 0, "L" to 0, "XL" to 0), "Negro"),
            Product("p4", "Taller Norte", "Blazer camel", "🧥", "Chamarras", 4290, mapOf("S" to 3, "M" to 4, "L" to 2), "Camel"),
            Product("p5", "Cobalto", "Chamarra bomber", "🧥", "Chamarras", 2890, mapOf("S" to 2, "M" to 5, "L" to 5, "XL" to 2), "Verde oliva"),
            Product("p6", "Luna Roja", "Vestido de encaje", "👗", "Vestidos", 3890, mapOf("XS" to 1, "S" to 2, "M" to 2), "Negro"),
            Product("p7", "Francesa", "Blusa con moño", "👚", "Blusas", 1690, mapOf("S" to 4, "M" to 4, "L" to 3), "Marfil"),
            Product("p8", "Urbe", "Playera oversize", "👕", "Blusas", 1590, mapOf("M" to 6, "L" to 6, "XL" to 4), "Naranja")
        )
    )
    override val products: StateFlow<List<Product>> = items.asStateFlow()

    /** Pedidos pagados en esta sesión de demo, para el panel de ventas. */
    private val sales = MutableStateFlow<List<Order>>(emptyList())

    override suspend fun refresh(): Result<Unit> = Result.success(Unit)

    override suspend fun publish(product: NewProduct): Result<Unit> {
        if (product.name.isBlank()) return Result.failure(IllegalArgumentException("Escribe el nombre del producto."))
        if (product.price < 0) return Result.failure(IllegalArgumentException("El precio no puede ser negativo."))
        if (product.sizes.isEmpty()) return Result.failure(IllegalArgumentException("Elige al menos una talla."))
        val new = Product(
            id = UUID.randomUUID().toString(),
            brand = "Tu marca",
            name = product.name.trim(),
            emoji = "🧥",
            category = "Nuevo",
            price = product.price,
            stockBySize = product.sizes.associateWith { product.stockPerSize },
            color = "Gris"
        )
        items.update { it + new }
        return Result.success(Unit)
    }

    override suspend fun salesSummary(): Result<SalesSummary> {
        // Cifras base de la demo + lo que se compre durante la presentación.
        val orders = sales.value
        val gross = 48_250 + orders.sumOf { it.subtotal }
        val commission = ShopRules.commission(gross)
        return Result.success(
            SalesSummary(
                orders = 32 + orders.size,
                units = 41 + orders.sumOf { it.units },
                gross = gross,
                commission = commission,
                net = gross - commission
            )
        )
    }

    fun stockOf(productId: String, size: String): Int =
        items.value.firstOrNull { it.id == productId }?.stockBySize?.get(size) ?: 0

    fun decrementStock(productId: String, size: String, quantity: Int) {
        items.update { list ->
            list.map { p ->
                if (p.id != productId) p
                else p.copy(stockBySize = p.stockBySize + (size to ((p.stockBySize[size] ?: 0) - quantity).coerceAtLeast(0)))
            }
        }
    }

    fun recordSale(order: Order) {
        sales.update { it + order }
    }
}

/** Carrito y pedidos en memoria, con las mismas reglas que Supabase (RF-01, RN-01, RN-02). */
@Singleton
class FakeCartRepository @Inject constructor(private val catalog: FakeProductRepository) : CartRepository {

    private val cart = MutableStateFlow<List<CartLine>>(emptyList())
    override val lines: StateFlow<List<CartLine>> = cart.asStateFlow()

    private val history = MutableStateFlow<List<Order>>(emptyList())
    override val orders: StateFlow<List<Order>> = history.asStateFlow()

    private var reservationExpiresAt: Long? = null

    /** Retardo simulado de red; en pruebas se pone en 0. */
    var simulatedDelayMs: Long = 700

    override suspend fun refresh(): Result<Unit> {
        // Actualiza existencias por si cambiaron (otra compra, el socio editó inventario).
        cart.update { list -> list.map { it.copy(stock = catalog.stockOf(it.productId, it.size)) } }
        return Result.success(Unit)
    }

    override suspend fun add(productId: String, size: String, quantity: Int): Result<Unit> {
        val product = catalog.products.value.firstOrNull { it.id == productId }
            ?: return Result.failure(IllegalArgumentException("Producto no encontrado."))
        val stock = product.stockBySize[size] ?: 0
        if (stock <= 0) return Result.failure(OutOfStockException(size))
        val existing = cart.value.firstOrNull { it.productId == productId && it.size == size }
        val wanted = (existing?.quantity ?: 0) + quantity
        if (wanted > stock) return Result.failure(IllegalStateException("Solo quedan $stock en talla $size."))
        if (existing != null) {
            cart.update { list ->
                list.map { if (it.id == existing.id) it.copy(quantity = ShopRules.clampQuantity(wanted, stock), stock = stock) else it }
            }
        } else {
            cart.update {
                it + CartLine(
                    id = UUID.randomUUID().toString(),
                    productId = product.id,
                    name = product.name,
                    brand = product.brand,
                    emoji = product.emoji,
                    color = product.color,
                    size = size,
                    unitPrice = product.price,
                    quantity = ShopRules.clampQuantity(quantity, stock),
                    stock = stock
                )
            }
        }
        reservationExpiresAt = null
        return Result.success(Unit)
    }

    override suspend fun setQuantity(lineId: String, quantity: Int): Result<Unit> {
        cart.update { list -> list.map { if (it.id == lineId) it.copy(quantity = ShopRules.clampQuantity(quantity, it.stock)) else it } }
        reservationExpiresAt = null
        return Result.success(Unit)
    }

    override suspend fun remove(lineId: String): Result<Unit> {
        cart.update { list -> list.filterNot { it.id == lineId } }
        reservationExpiresAt = null
        return Result.success(Unit)
    }

    override suspend fun startCheckout(): Result<Long> {
        delay(simulatedDelayMs)
        val current = cart.value
        if (current.isEmpty()) return Result.failure(IllegalStateException("Tu carrito está vacío."))
        current.forEach { line ->
            if (catalog.stockOf(line.productId, line.size) < line.quantity) {
                return Result.failure(IllegalStateException("Ya no hay existencias de ${line.name} talla ${line.size}."))
            }
        }
        val expires = System.currentTimeMillis() + ShopRules.RESERVATION_MINUTES * 60_000L
        reservationExpiresAt = expires
        return Result.success(expires)
    }

    override suspend fun confirmOrder(paymentRef: String, shippingAddress: String): Result<Order> {
        delay(simulatedDelayMs * 2)
        val expires = reservationExpiresAt
        if (expires == null || System.currentTimeMillis() > expires) {
            return Result.failure(IllegalStateException("Tu reserva expiró. Vuelve a intentar el pago."))
        }
        val current = cart.value
        if (current.isEmpty()) return Result.failure(IllegalStateException("Tu carrito está vacío."))
        current.forEach { catalog.decrementStock(it.productId, it.size, it.quantity) }
        val subtotal = ShopRules.subtotal(current)
        val order = Order(
            id = UUID.randomUUID().toString(),
            createdAt = LocalDateTime.now().format(OrderDateFormat),
            status = "Pagado",
            lines = current.map { OrderLine(it.name, it.brand, it.size, it.quantity, it.unitPrice) },
            subtotal = subtotal,
            commission = ShopRules.commission(subtotal),
            total = subtotal + ShopRules.shipping(current)
        )
        history.update { listOf(order) + it }
        catalog.recordSale(order)
        cart.value = emptyList()
        reservationExpiresAt = null
        return Result.success(order)
    }

    override suspend fun refreshOrders(): Result<Unit> = Result.success(Unit)
}

internal val OrderDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale("es", "MX"))

@Singleton
class FakeMirrorRepository @Inject constructor() : MirrorRepository {
    private val state = MutableStateFlow(MirrorProjection())
    override val projection: StateFlow<MirrorProjection> = state.asStateFlow()

    override suspend fun project(item: TryOnItem): Result<Unit> {
        state.value = MirrorProjection(item = item, projecting = true)
        return Result.success(Unit)
    }

    override suspend fun stop(): Result<Unit> {
        state.update { it.copy(projecting = false) }
        return Result.success(Unit)
    }
}

internal fun emojiFor(category: String): String = when (category) {
    "Chamarras" -> "🧥"
    "Pantalones" -> "👖"
    "Vestidos" -> "👗"
    "Zapatos" -> "👢"
    else -> "👚"
}
