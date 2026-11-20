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
import io.github.jan.supabase.postgrest.query.Columns
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

    // El checkout (pedidos, comisión y reservas de 10 min) está modelado en la base de datos pero aún no se usa:
    // la bolsa vive en memoria, igual que en el prototipo.
    private val bag = MutableStateFlow(0)
    override val bagCount: StateFlow<Int> = bag.asStateFlow()

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

    override suspend fun addToBag(productId: String, size: String): Result<Unit> = attempt {
        val product = items.value.firstOrNull { it.id == productId } ?: error("Producto no encontrado.")
        if (!product.isAvailable(size)) throw OutOfStockException(size)
        bag.update { it + 1 }
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
        state.value = MirrorProjection(item = item, projecting = true)
    }

    override suspend fun stop(): Result<Unit> = attempt {
        val userId = client.userIdOrThrow()
        client.from("mirror_sessions").update(MirrorStopUpdate()) {
            filter {
                eq("user_id", userId)
                eq("mirror_code", MirrorRepository.MIRROR_CODE)
            }
        }
        state.update { it.copy(projecting = false) }
    }
}
