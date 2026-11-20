package com.monzatti.app.domain.repository

import com.monzatti.app.domain.model.Garment
import com.monzatti.app.domain.model.NewProduct
import com.monzatti.app.domain.model.Product
import com.monzatti.app.domain.model.SignUpRequest
import com.monzatti.app.domain.model.StylePalette
import com.monzatti.app.domain.model.UserProfile
import com.monzatti.app.domain.model.UserRole
import com.monzatti.app.domain.shop.SalesSummary
import com.monzatti.app.domain.tryon.TryOnItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val currentUser: StateFlow<UserProfile?>
    suspend fun signIn(email: String, password: String, role: UserRole): Result<UserProfile>
    suspend fun signUp(request: SignUpRequest): Result<UserProfile>
    suspend fun sendPasswordReset(email: String): Result<Unit>
    suspend fun signOut()
}

interface ProfileRepository {
    suspend fun updateStyle(userId: String, style: StylePalette): Result<Unit>
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
}

/** Lo que muestra el espejo inteligente (RF-02). */
data class MirrorProjection(
    val item: TryOnItem? = null,
    val projecting: Boolean = false
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
}
