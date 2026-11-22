package com.monzatti.app.domain.repository

import com.monzatti.app.domain.model.Garment
import com.monzatti.app.domain.model.Measurements
import com.monzatti.app.domain.model.NewProduct
import com.monzatti.app.domain.model.Product
import com.monzatti.app.domain.model.SignUpRequest
import com.monzatti.app.domain.model.StylePalette
import com.monzatti.app.domain.model.UserProfile
import com.monzatti.app.domain.model.UserRole
import com.monzatti.app.domain.shop.BrandOrder
import com.monzatti.app.domain.shop.SalesSummary
import com.monzatti.app.domain.tryon.TryOnItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val currentUser: StateFlow<UserProfile?>
    suspend fun signIn(email: String, password: String, role: UserRole): Result<UserProfile>
    suspend fun signUp(request: SignUpRequest): Result<UserProfile>
    /** Sesión guardada de una apertura anterior, o null si hay que iniciar sesión. */
    suspend fun restoreSession(): UserProfile?
    suspend fun sendPasswordReset(email: String): Result<Unit>
    suspend fun signOut()
}

interface ProfileRepository {
    suspend fun updateStyle(userId: String, style: StylePalette): Result<Unit>
    /** Medidas del perfil con sesión (tabla profiles); alimentan el avatar y la talla sugerida (RF-03). */
    suspend fun measurements(): Result<Measurements>
    suspend fun saveMeasurements(measurements: Measurements): Result<Unit>
}

interface StyleRepository {
    val style: Flow<StylePalette>
    suspend fun setStyle(style: StylePalette)
}

interface ClosetRepository {
    val garments: StateFlow<List<Garment>>
    suspend fun refresh(): Result<Unit>
    suspend fun add(name: String, category: String, size: String, color: String, imagePath: String? = null): Result<Unit>
    suspend fun toggleFavorite(id: String): Result<Unit>
    suspend fun update(id: String, name: String, category: String, size: String, color: String): Result<Unit>
    /** Borra la prenda, su foto local y su respaldo en Storage. */
    suspend fun delete(id: String): Result<Unit>
}

/** Lo que muestra el espejo inteligente (RF-02). */
data class MirrorProjection(
    val item: TryOnItem? = null,
    val projecting: Boolean = false,
    /** True si el último aviso salió por Realtime; la app no sabe si el espejo está encendido. */
    val live: Boolean = false
)

interface MirrorRepository {
    val projection: StateFlow<MirrorProjection>
    suspend fun project(item: TryOnItem): Result<Unit>
    suspend fun stop(): Result<Unit>

    companion object {
        const val MIRROR_CODE = "MONZATTI-MIRROR-01"
    }
}

interface ProductRepository {
    val products: StateFlow<List<Product>>
    suspend fun refresh(): Result<Unit>
    suspend fun publish(product: NewProduct): Result<Unit>

    /** Ventas de las marcas del socio con sesión. */
    suspend fun salesSummary(): Result<SalesSummary>

    /** Precio y existencias por talla de un producto del socio. */
    suspend fun update(productId: String, price: Int, stockBySize: Map<String, Int>): Result<Unit>

    /** Baja del producto: deja de verse en la tienda y sale de los carritos (los pedidos se conservan). */
    suspend fun archive(productId: String): Result<Unit>

    /** Cambia el nombre de la marca del socio (la crea si aún no tiene). */
    suspend fun renameBrand(name: String): Result<Unit>

    /** Pedidos pagados con prendas de la marca del socio, del más reciente al más antiguo. */
    suspend fun brandOrders(): Result<List<BrandOrder>>

    /** Marca como enviadas las prendas de la marca del socio en ese pedido. */
    suspend fun markShipped(orderId: String): Result<Unit>
}
