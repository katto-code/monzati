package com.monzatti.app.data.fake

import com.monzatti.app.data.local.GarmentImages
import com.monzatti.app.domain.model.Garment
import com.monzatti.app.domain.model.Measurements
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
import com.monzatti.app.domain.shop.BrandOrder
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

/** Identificador fijo del socio del modo demo, dueño de los productos de Francesa. */
const val DEMO_PARTNER_ID = "demo-socio"

@Singleton
class FakeAuthRepository @Inject constructor() : AuthRepository {

    private val user = MutableStateFlow<UserProfile?>(null)
    override val currentUser: StateFlow<UserProfile?> = user.asStateFlow()

    override suspend fun signIn(email: String, password: String, role: UserRole): Result<UserProfile> {
        if (!email.contains('@')) return Result.failure(IllegalArgumentException("Escribe un correo válido."))
        if (password.isBlank()) return Result.failure(IllegalArgumentException("Escribe tu contraseña."))
        val name = if (role == UserRole.SOCIO) "Mariana" else email.substringBefore('@').replaceFirstChar { it.uppercase() }
        val profile = UserProfile(
            // El socio del demo siempre es el mismo: es dueño de la marca Francesa (como socio@monzatti.test en Supabase).
            id = if (role == UserRole.SOCIO) DEMO_PARTNER_ID else "demo-${email.lowercase()}",
            fullName = name,
            email = email,
            role = role,
            gender = null,
            style = null
        )
        user.value = profile
        return Result.success(profile)
    }

    // Demo en memoria: la sesión vive mientras viva el proceso.
    override suspend fun restoreSession(): UserProfile? = user.value

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

    private var saved = Measurements(heightCm = 168.0)

    override suspend fun measurements(): Result<Measurements> = Result.success(saved)

    override suspend fun saveMeasurements(measurements: Measurements): Result<Unit> {
        saved = measurements
        return Result.success(Unit)
    }
}

@Singleton
class FakeClosetRepository @Inject constructor(private val images: GarmentImages) : ClosetRepository {

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

    override suspend fun add(name: String, category: String, size: String, color: String, imagePath: String?): Result<Unit> {
        val id = UUID.randomUUID().toString()
        val garment = Garment(
            id = id,
            name = name.ifBlank { "Prenda nueva" },
            emoji = emojiFor(category),
            category = category,
            size = size,
            detail = "$color · $size",
            color = color,
            imagePath = imagePath?.let { images.promote(it, id) }
        )
        items.update { it + garment }
        return Result.success(Unit)
    }

    override suspend fun toggleFavorite(id: String): Result<Unit> {
        items.update { list -> list.map { if (it.id == id) it.copy(favorite = !it.favorite) else it } }
        return Result.success(Unit)
    }

    override suspend fun update(id: String, name: String, category: String, size: String, color: String): Result<Unit> {
        items.update { list ->
            list.map {
                if (it.id != id) it
                else it.copy(name = name.ifBlank { it.name }, category = category, size = size, color = color, detail = "$color · $size", emoji = emojiFor(category))
            }
        }
        return Result.success(Unit)
    }

    override suspend fun delete(id: String): Result<Unit> {
        images.delete(id)
        items.update { list -> list.filterNot { it.id == id } }
        return Result.success(Unit)
    }
}

@Singleton
class FakeProductRepository @Inject constructor(private val auth: FakeAuthRepository) : ProductRepository {

    private val items = MutableStateFlow(
        listOf(
            // Marcas ficticias; precios en el rango de las marcas mexicanas analizadas ($1,500 a $5,000 MXN).
            Product("p1", "Francesa", "Blusa de seda", "👚", "Blusas", 2290, mapOf("XS" to 2, "S" to 5, "M" to 6, "L" to 4, "XL" to 1), "Rosa pastel", DEMO_PARTNER_ID),
            Product("p2", "Nómada", "Vestido midi", "👗", "Vestidos", 3490, mapOf("XS" to 0, "S" to 1, "M" to 2, "L" to 0, "XL" to 0), "Vino"),
            Product("p3", "Raíz", "Pantalón sastre", "👖", "Pantalones", 1890, mapOf("XS" to 0, "S" to 0, "M" to 0, "L" to 0, "XL" to 0), "Negro"),
            Product("p4", "Taller Norte", "Blazer camel", "🧥", "Chamarras", 4290, mapOf("S" to 3, "M" to 4, "L" to 2), "Camel"),
            Product("p5", "Cobalto", "Chamarra bomber", "🧥", "Chamarras", 2890, mapOf("S" to 2, "M" to 5, "L" to 5, "XL" to 2), "Verde oliva"),
            Product("p6", "Luna Roja", "Vestido de encaje", "👗", "Vestidos", 3890, mapOf("XS" to 1, "S" to 2, "M" to 2), "Negro"),
            Product("p7", "Francesa", "Blusa con moño", "👚", "Blusas", 1690, mapOf("S" to 4, "M" to 4, "L" to 3), "Marfil", DEMO_PARTNER_ID),
            Product("p8", "Urbe", "Playera oversize", "👕", "Blusas", 1590, mapOf("M" to 6, "L" to 6, "XL" to 4), "Naranja")
        )
    )
    override val products: StateFlow<List<Product>> = items.asStateFlow()

    /** Pedidos pagados en esta sesión de demo, para el panel de ventas. */
    private val sales = MutableStateFlow<List<Pair<Order, LocalDateTime>>>(emptyList())

    /** Pedidos que el socio ya marcó como enviados. */
    private val shipped = MutableStateFlow<Set<String>>(emptySet())

    private fun myBrand(): String? {
        val owner = auth.currentUser.value ?: return null
        return items.value.firstOrNull { it.ownerId == owner.id }?.brand
    }

    override suspend fun refresh(): Result<Unit> = Result.success(Unit)

    override suspend fun publish(product: NewProduct): Result<Unit> {
        if (product.name.isBlank()) return Result.failure(IllegalArgumentException("Escribe el nombre del producto."))
        if (product.price < 0) return Result.failure(IllegalArgumentException("El precio no puede ser negativo."))
        if (product.sizes.isEmpty()) return Result.failure(IllegalArgumentException("Elige al menos una talla."))
        val owner = auth.currentUser.value
        val new = Product(
            id = UUID.randomUUID().toString(),
            // Igual que en Supabase: si el socio ya tiene marca se usa esa; si no, una con su nombre.
            brand = items.value.firstOrNull { owner != null && it.ownerId == owner.id }?.brand
                ?: owner?.fullName?.trim().orEmpty().ifBlank { "Mi marca" },
            name = product.name.trim(),
            emoji = emojiFor(product.category),
            category = product.category,
            price = product.price,
            stockBySize = product.sizes.associateWith { product.stockPerSize },
            color = "Gris",
            ownerId = owner?.id
        )
        items.update { it + new }
        return Result.success(Unit)
    }

    override suspend fun salesSummary(): Result<SalesSummary> {
        // Solo lo que se compró en esta sesión de demo de la marca del socio.
        val orders = brandOrders().getOrThrow()
        val gross = orders.sumOf { it.total }
        val commission = ShopRules.commission(gross)
        return Result.success(
            SalesSummary(
                orders = orders.size,
                units = orders.sumOf { o -> o.lines.sumOf { it.quantity } },
                gross = gross,
                commission = commission,
                net = gross - commission
            )
        )
    }

    override suspend fun update(productId: String, price: Int, stockBySize: Map<String, Int>): Result<Unit> {
        if (price < 0) return Result.failure(IllegalArgumentException("El precio no puede ser negativo."))
        if (stockBySize.values.any { it < 0 }) return Result.failure(IllegalArgumentException("Las existencias no pueden ser negativas."))
        val owner = auth.currentUser.value?.id
        if (items.value.none { it.id == productId && it.ownerId == owner }) return Result.failure(IllegalStateException("Este producto no es de tu marca."))
        items.update { list -> list.map { if (it.id == productId) it.copy(price = price, stockBySize = it.stockBySize + stockBySize) else it } }
        return Result.success(Unit)
    }

    override suspend fun archive(productId: String): Result<Unit> {
        val owner = auth.currentUser.value?.id
        if (items.value.none { it.id == productId && it.ownerId == owner }) return Result.failure(IllegalStateException("Este producto no es de tu marca."))
        // El carrito demo ve existencias 0 al refrescar, así que ya no se puede pagar.
        items.update { list -> list.filterNot { it.id == productId } }
        return Result.success(Unit)
    }

    override suspend fun renameBrand(name: String): Result<Unit> {
        val clean = name.trim()
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("Escribe el nombre de tu marca."))
        if (clean.length > 60) return Result.failure(IllegalArgumentException("El nombre de la marca es muy largo."))
        val owner = auth.currentUser.value?.id ?: return Result.failure(IllegalStateException("Inicia sesión."))
        val old = myBrand()
        // En el demo las ventas se ligan a la marca por nombre: dos marcas con el mismo nombre se mezclarían.
        if (items.value.any { it.ownerId != owner && it.brand.equals(clean, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("Ya existe una marca con ese nombre."))
        }
        items.update { list -> list.map { if (it.ownerId == owner) it.copy(brand = clean) else it } }
        if (old != null) sales.update { list ->
            list.map { (o, at) -> o.copy(lines = o.lines.map { if (it.brand == old) it.copy(brand = clean) else it }) to at }
        }
        return Result.success(Unit)
    }

    override suspend fun brandOrders(): Result<List<BrandOrder>> {
        val brand = myBrand() ?: return Result.success(emptyList())
        return Result.success(
            sales.value.mapNotNull { (order, at) ->
                val lines = order.lines.filter { it.brand == brand }
                if (lines.isEmpty()) null
                else BrandOrder(order.id, at, if (order.id in shipped.value) "Enviado" else order.status, lines, order.id in shipped.value)
            }.sortedByDescending { it.createdAt }
        )
    }

    override suspend fun markShipped(orderId: String): Result<Unit> {
        if (brandOrders().getOrThrow().none { it.id == orderId && !it.shipped }) {
            return Result.failure(IllegalStateException("Este pedido no tiene prendas de tu marca por enviar."))
        }
        shipped.update { it + orderId }
        return Result.success(Unit)
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
        sales.update { it + (order to LocalDateTime.now()) }
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

    override suspend fun cancelCheckout(): Result<Unit> {
        reservationExpiresAt = null
        return Result.success(Unit)
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
