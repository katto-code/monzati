package com.monzatti.app.ui.navigation

import kotlinx.serialization.Serializable

// 1. Login / autenticación
@Serializable object LoginRoute
@Serializable object CreateAccountRoute
@Serializable object RecoveryRoute
@Serializable object AboutRoute
@Serializable object ObjectiveRoute

// Estilo de la app
@Serializable object StyleChoiceRoute
@Serializable object StylePickerRoute
@Serializable object StyleQuizRoute
@Serializable data class StyleResultRoute(val styleKey: String)

// Usuario final: menú principal
@Serializable object ClosetRoute
@Serializable object AddGarmentRoute
@Serializable object ShopRoute
@Serializable data class ProductRoute(val productId: String)
@Serializable object AssistantRoute
@Serializable object FittingRoomRoute
@Serializable object MirrorRoute
@Serializable data class TryOnRoute(val startItemId: String? = null)

// 7. App para empresarios
@Serializable object SalesRoute
@Serializable object NewProductRoute
@Serializable object InventoryRoute
