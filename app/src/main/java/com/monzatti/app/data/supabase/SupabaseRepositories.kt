package com.monzatti.app.data.supabase

import com.monzatti.app.data.fake.emojiFor
import com.monzatti.app.domain.model.Garment
import com.monzatti.app.domain.model.Gender
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
import com.monzatti.app.domain.tryon.TryOnItem
import com.monzatti.app.domain.tryon.TryOnSource
import com.monzatti.app.domain.repository.ProductRepository
import com.monzatti.app.domain.repository.ProfileRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order as Order_
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.channel
import com.monzatti.app.data.fake.OrderDateFormat
import com.monzatti.app.domain.shop.CartLine
import com.monzatti.app.domain.shop.CartRepository
import com.monzatti.app.domain.shop.Order
import com.monzatti.app.domain.shop.OrderLine
import com.monzatti.app.domain.shop.SalesSummary
import com.monzatti.app.domain.shop.ShopRules
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Como runCatching, pero sin tragarse la cancelación de corrutinas. */
private inline fun <T> attempt(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

private fun SupabaseClient.userIdOrThrow(): String =
    auth.currentUserOrNull()?.id ?: error("Inicia sesión para continuar.")

@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val client: SupabaseClient
) : AuthRepository {

    private val user = MutableStateFlow<UserProfile?>(null)
    override val currentUser: StateFlow<UserProfile?> = user.asStateFlow()

    override suspend fun signIn(email: String, password: String, role: UserRole): Result<UserProfile> = attempt {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        val id = client.userIdOrThrow()
        val dto = client.from("profiles").select { filter { eq("id", id) } }.decodeSingle<ProfileDto>()
        dto.toProfile(email.trim()).also { user.value = it }
    }

    override suspend fun signUp(request: SignUpRequest): Result<UserProfile> = attempt {
        require(PasswordRules.isValid(request.password)) { PasswordRules.DESCRIPTION }
        // El trigger handle_new_user crea la fila en profiles con estos metadatos.
        client.auth.signUpWith(Email) {
            email = request.email.trim()
            password = request.password
            data = buildJsonObject {
                put("full_name", request.fullName.trim())
                put("role", request.role.key)
                put("gender", request.gender.key)
            }
        }
        // Si el proyecto pide confirmar el correo no habrá sesión todavía; el perfil local sirve para el flujo.
        val id = client.auth.currentUserOrNull()?.id ?: ""
        UserProfile(id, request.fullName.trim(), request.email.trim(), request.role, request.gender, null)
            .also { if (id.isNotEmpty()) user.value = it }
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = attempt {
        client.auth.resetPasswordForEmail(email.trim())
    }

    override suspend fun signOut() {
        attempt { client.auth.signOut() }
        user.value = null
    }

    fun updateLocalStyle(style: StylePalette) {
        user.update { it?.copy(style = style) }
    }
}

private fun ProfileDto.toProfile(email: String) = UserProfile(
    id = id,
    fullName = fullName.orEmpty(),
    email = email,
    role = UserRole.fromKey(role),
    gender = Gender.fromKey(gender),
    style = StylePalette.fromKeyOrNull(stylePalette)
)

@Singleton
class SupabaseProfileRepository @Inject constructor(
    private val client: SupabaseClient,
    private val auth: SupabaseAuthRepository
) : ProfileRepository {
    override suspend fun updateStyle(userId: String, style: StylePalette): Result<Unit> = attempt {
        client.from("profiles").update(StyleUpdate(style.key)) { filter { eq("id", userId) } }
        auth.updateLocalStyle(style)
    }
}

@Singleton
class SupabaseClosetRepository @Inject constructor(
    private val client: SupabaseClient
) : ClosetRepository {

    private val items = MutableStateFlow<List<Garment>>(emptyList())
    override val garments: StateFlow<List<Garment>> = items.asStateFlow()

    override suspend fun refresh(): Result<Unit> = attempt {
        // RLS solo devuelve las prendas de la persona con sesión.
        items.value = client.from("garments").select().decodeList<GarmentDto>().map { dto ->
            Garment(
                id = dto.id,
                name = dto.name,
                emoji = dto.emoji ?: emojiFor(dto.category),
                category = dto.category,
                size = dto.size.orEmpty(),
                detail = listOfNotNull(dto.color, dto.size).joinToString(" · "),
                favorite = dto.isFavorite,
                color = dto.color.orEmpty()
            )
        }
    }

    override suspend fun add(name: String, category: String, size: String, color: String): Result<Unit> = attempt {
        val insert = GarmentInsert(client.userIdOrThrow(), name.ifBlank { "Prenda nueva" }, category, size, color, emojiFor(category))
        client.from("garments").insert(insert)
        refresh().getOrThrow()
    }

    override suspend fun toggleFavorite(id: String): Result<Unit> = attempt {
        val current = items.value.firstOrNull { it.id == id } ?: return@attempt
        client.from("garments").update(FavoriteUpdate(!current.favorite)) { filter { eq("id", id) } }
        items.update { list -> list.map { if (it.id == id) it.copy(favorite = !it.favorite) else it } }
    }
}

@Singleton
class SupabaseProductRepository @Inject constructor(
    private val client: SupabaseClient
) : ProductRepository {

    private val items = MutableStateFlow<List<Product>>(emptyList())
    override val products: StateFlow<List<Product>> = items.asStateFlow()

    override suspend fun refresh(): Result<Unit> = attempt {
        val columns = Columns.raw("id, name, emoji, category, color, price_mxn, brands(id, name), product_variants(size, stock)")
        items.value = client.from("products")
            .select(columns) { filter { eq("is_active", true) } }
            .decodeList<ProductDto>()
            .map { dto ->
                Product(
                    id = dto.id,
                    brand = dto.brands?.name.orEmpty(),
                    name = dto.name,
                    emoji = dto.emoji ?: "🧥",
                    category = dto.category.orEmpty(),
                    price = dto.priceMxn.toInt(),
                    stockBySize = dto.variants.associate { it.size to it.stock },
                    color = dto.color.orEmpty()
                )
            }
    }

    override suspend fun publish(product: NewProduct): Result<Unit> = attempt {
        require(product.name.isNotBlank()) { "Escribe el nombre del producto." }
        require(product.sizes.isNotEmpty()) { "Elige al menos una talla." }
        val ownerId = client.userIdOrThrow()
        val brandId = client.from("brands").select { filter { eq("owner_id", ownerId) } }
            .decodeList<BrandDto>().firstOrNull()?.id
            ?: client.from("brands").insert(BrandInsert(ownerId, "Tu marca")) { select() }.decodeSingle<BrandDto>().id
        val productId = client.from("products")
            .insert(ProductInsert(brandId, product.name.trim(), "🧥", "Nuevo", product.price)) { select(Columns.list("id")) }
            .decodeSingle<IdDto>().id
        client.from("product_variants").insert(product.sizes.map { VariantInsert(productId, it, product.stockPerSize) })
        refresh().getOrThrow()
    }

    override suspend fun salesSummary(): Result<SalesSummary> = attempt {
        val row = client.postgrest.rpc("brand_sales_summary").decodeList<SalesSummaryDto>().firstOrNull()
        SalesSummary(
            orders = row?.ordersCount?.toInt() ?: 0,
            units = row?.units?.toInt() ?: 0,
            gross = row?.grossMxn?.toInt() ?: 0,
            commission = row?.commissionMxn?.toInt() ?: 0,
            net = row?.netMxn?.toInt() ?: 0
        )
    }
}

/** Carrito en la tabla cart_items; el pago usa las funciones start_checkout y confirm_order (RN-01, RN-02). */
@Singleton
class SupabaseCartRepository @Inject constructor(
    private val client: SupabaseClient
) : CartRepository {

    private val cart = MutableStateFlow<List<CartLine>>(emptyList())
    override val lines: StateFlow<List<CartLine>> = cart.asStateFlow()

    private val history = MutableStateFlow<List<Order>>(emptyList())
    override val orders: StateFlow<List<Order>> = history.asStateFlow()

    override suspend fun refresh(): Result<Unit> = attempt {
        val columns = Columns.raw(
            "id, quantity, variant_id, product_variants(size, stock, products(id, name, emoji, color, price_mxn, brands(name)))"
        )
        cart.value = client.from("cart_items").select(columns).decodeList<CartItemDto>().mapNotNull { dto ->
            val variant = dto.variant ?: return@mapNotNull null
            val product = variant.product ?: return@mapNotNull null
            CartLine(
                id = dto.id,
                productId = product.id,
                name = product.name,
                brand = product.brands?.name.orEmpty(),
                emoji = product.emoji ?: "🧥",
                color = product.color.orEmpty(),
                size = variant.size,
                unitPrice = product.priceMxn.toInt(),
                quantity = dto.quantity,
                stock = variant.stock
            )
        }
    }

    override suspend fun add(productId: String, size: String, quantity: Int): Result<Unit> = attempt {
        val variant = client.from("product_variants")
            .select(Columns.list("id", "stock")) {
                filter {
                    eq("product_id", productId)
                    eq("size", size)
                }
            }
            .decodeList<VariantStockDto>().firstOrNull() ?: throw OutOfStockException(size)
        if (variant.stock <= 0) throw OutOfStockException(size)
        refresh().getOrThrow()
        val existing = cart.value.firstOrNull { it.productId == productId && it.size == size }
        val wanted = (existing?.quantity ?: 0) + quantity
        if (wanted > variant.stock) error("Solo quedan ${variant.stock} en talla $size.")
        if (existing != null) {
            client.from("cart_items").update(QuantityUpdate(ShopRules.clampQuantity(wanted, variant.stock))) {
                filter { eq("id", existing.id) }
            }
        } else {
            client.from("cart_items").insert(CartInsert(variant.id, ShopRules.clampQuantity(quantity, variant.stock)))
        }
        refresh().getOrThrow()
    }

    override suspend fun setQuantity(lineId: String, quantity: Int): Result<Unit> = attempt {
        val line = cart.value.firstOrNull { it.id == lineId } ?: return@attempt
        val clamped = ShopRules.clampQuantity(quantity, line.stock)
        cart.update { list -> list.map { if (it.id == lineId) it.copy(quantity = clamped) else it } }
        client.from("cart_items").update(QuantityUpdate(clamped)) { filter { eq("id", lineId) } }
    }

    override suspend fun remove(lineId: String): Result<Unit> = attempt {
        cart.update { list -> list.filterNot { it.id == lineId } }
        client.from("cart_items").delete { filter { eq("id", lineId) } }
    }

    override suspend fun startCheckout(): Result<Long> = attempt {
        val expires = client.postgrest.rpc("start_checkout").decodeAs<String>()
        OffsetDateTime.parse(expires).toInstant().toEpochMilli()
    }

    override suspend fun confirmOrder(paymentRef: String, shippingAddress: String): Result<Order> = attempt {
        val params = buildJsonObject {
            put("p_payment_ref", paymentRef)
            put("p_shipping_address", shippingAddress)
        }
        val orderId = client.postgrest.rpc("confirm_order", params).decodeAs<String>()
        cart.value = emptyList()
        refreshOrders().getOrThrow()
        history.value.firstOrNull { it.id == orderId } ?: error("No encontramos tu pedido.")
    }

    override suspend fun refreshOrders(): Result<Unit> = attempt {
        val columns = Columns.raw(
            "id, created_at, status, subtotal_mxn, commission_mxn, total_mxn, " +
                "order_items(product_name, brand_name, size, quantity, unit_price_mxn)"
        )
        history.value = client.from("orders")
            .select(columns) { order("created_at", Order_.DESCENDING) }
            .decodeList<OrderDto>()
            .map { dto ->
                Order(
                    id = dto.id,
                    createdAt = OffsetDateTime.parse(dto.createdAt)
                        .atZoneSameInstant(ZoneId.systemDefault())
                        .format(OrderDateFormat),
                    status = dto.status.replaceFirstChar { it.uppercase() },
                    lines = dto.items.map { OrderLine(it.productName, it.brandName, it.size, it.quantity, it.unitPriceMxn.toInt()) },
                    subtotal = dto.subtotalMxn.toInt(),
                    commission = dto.commissionMxn.toInt(),
                    total = dto.totalMxn.toInt()
                )
            }
    }
}

/**
 * RF-02: escribe en mirror_sessions lo que se proyecta. El espejo (otra pantalla) escucha esa fila
 * con Supabase Realtime y redibuja el atuendo.
 */
@Singleton
class SupabaseMirrorRepository @Inject constructor(
    private val client: SupabaseClient
) : MirrorRepository {

    private val state = MutableStateFlow(MirrorProjection())
    override val projection: StateFlow<MirrorProjection> = state.asStateFlow()

    override suspend fun project(item: TryOnItem): Result<Unit> = attempt {
        val row = MirrorSessionUpsert(
            userId = client.userIdOrThrow(),
            mirrorCode = MirrorRepository.MIRROR_CODE,
            garmentIds = if (item.source == TryOnSource.CLOSET) listOf(item.id) else emptyList(),
            productIds = if (item.source == TryOnSource.SHOP) listOf(item.id) else emptyList(),
            isProjecting = true
        )
        client.from("mirror_sessions").upsert(row) { onConflict = "user_id,mirror_code" }
        broadcast(item, projecting = true)
        state.value = MirrorProjection(item = item, projecting = true)
    }

    private var channel: RealtimeChannel? = null

    /** Avisa al espejo web (mirror/) por Supabase Realtime: llega en menos de un segundo (RF-02). */
    private suspend fun broadcast(item: TryOnItem?, projecting: Boolean) {
        val ch = channel ?: client.channel("mirror:${MirrorRepository.MIRROR_CODE}").also {
            it.subscribe(blockUntilSubscribed = true)
            channel = it
        }
        ch.broadcast(
            event = "projection",
            message = buildJsonObject {
                put("projecting", projecting)
                if (item != null) {
                    put("id", item.id)
                    put("name", item.name)
                    put("brand", item.subtitle)
                    put("kind", item.kind.name)
                    put("color", "#%06X".format(item.color and 0xFFFFFF))
                }
            }
        )
    }

    override suspend fun stop(): Result<Unit> = attempt {
        val userId = client.userIdOrThrow()
        client.from("mirror_sessions").update(MirrorStopUpdate()) {
            filter {
                eq("user_id", userId)
                eq("mirror_code", MirrorRepository.MIRROR_CODE)
            }
        }
        broadcast(null, projecting = false)
        state.update { it.copy(projecting = false) }
    }
}
