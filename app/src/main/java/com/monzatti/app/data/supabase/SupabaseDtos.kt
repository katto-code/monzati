package com.monzatti.app.data.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* Filas de las tablas de supabase/migrations/20261119000000_init.sql */

@Serializable
data class ProfileDto(
    val id: String,
    @SerialName("full_name") val fullName: String? = null,
    val role: String? = null,
    val gender: String? = null,
    @SerialName("style_palette") val stylePalette: String? = null
)

@Serializable
data class StyleUpdate(@SerialName("style_palette") val stylePalette: String)

@Serializable
data class GarmentDto(
    val id: String,
    val name: String,
    val category: String,
    val size: String? = null,
    val color: String? = null,
    val emoji: String? = null,
    @SerialName("is_favorite") val isFavorite: Boolean = false
)

@Serializable
data class GarmentInsert(
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    val category: String,
    val size: String,
    val color: String,
    val emoji: String
)

@Serializable
data class FavoriteUpdate(@SerialName("is_favorite") val isFavorite: Boolean)

@Serializable
data class BrandDto(val id: String, val name: String)

@Serializable
data class BrandInsert(@SerialName("owner_id") val ownerId: String, val name: String)

@Serializable
data class VariantDto(val size: String, val stock: Int)

@Serializable
data class ProductDto(
    val id: String,
    val name: String,
    val emoji: String? = null,
    val category: String? = null,
    val color: String? = null,
    @SerialName("price_mxn") val priceMxn: Double,
    val brands: BrandDto? = null,
    @SerialName("product_variants") val variants: List<VariantDto> = emptyList()
)

@Serializable
data class ProductInsert(
    @SerialName("brand_id") val brandId: String,
    val name: String,
    val emoji: String,
    val category: String,
    @SerialName("price_mxn") val priceMxn: Int
)

@Serializable
data class IdDto(val id: String)

@Serializable
data class VariantInsert(
    @SerialName("product_id") val productId: String,
    val size: String,
    val stock: Int
)

@Serializable
data class MirrorSessionUpsert(
    @SerialName("user_id") val userId: String,
    @SerialName("mirror_code") val mirrorCode: String,
    @SerialName("garment_ids") val garmentIds: List<String>,
    @SerialName("product_ids") val productIds: List<String>,
    @SerialName("is_projecting") val isProjecting: Boolean
)

@Serializable
data class MirrorStopUpdate(@SerialName("is_projecting") val isProjecting: Boolean = false)
