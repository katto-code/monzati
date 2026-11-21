package com.monzatti.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.monzatti.app.core.theme.MonzattiTheme
import com.monzatti.app.domain.model.UserProfile
import com.monzatti.app.domain.model.UserRole
import com.monzatti.app.domain.repository.AuthRepository
import com.monzatti.app.ui.feature.about.AboutScreen
import com.monzatti.app.ui.feature.about.ObjectiveScreen
import com.monzatti.app.ui.feature.assistant.AssistantScreen
import com.monzatti.app.ui.feature.auth.CreateAccountScreen
import com.monzatti.app.ui.feature.auth.LoginScreen
import com.monzatti.app.ui.feature.auth.RecoveryScreen
import com.monzatti.app.ui.feature.business.InventoryScreen
import com.monzatti.app.ui.feature.business.NewProductScreen
import com.monzatti.app.ui.feature.business.SalesScreen
import com.monzatti.app.ui.feature.closet.DigitizeScreen
import com.monzatti.app.ui.feature.closet.ClosetScreen
import com.monzatti.app.ui.feature.fittingroom.FittingRoomScreen
import com.monzatti.app.ui.feature.fittingroom.MirrorScreen
import com.monzatti.app.ui.feature.shop.ProductScreen
import com.monzatti.app.ui.feature.shop.ShopScreen
import com.monzatti.app.ui.feature.shop.CartScreen
import com.monzatti.app.ui.feature.shop.CheckoutScreen
import com.monzatti.app.ui.feature.shop.OrderConfirmedScreen
import com.monzatti.app.ui.feature.shop.OrdersScreen
import com.monzatti.app.ui.feature.style.StyleChoiceScreen
import com.monzatti.app.ui.feature.style.StylePickerScreen
import com.monzatti.app.ui.feature.style.StyleQuizScreen
import com.monzatti.app.ui.feature.style.StyleResultScreen
import com.monzatti.app.ui.feature.tryon.TryOnScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class SessionViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            auth.signOut()
            onDone()
        }
    }
}

private data class Tab(val route: Any, val label: String, val icon: String)

private val userTabs = listOf(
    Tab(ClosetRoute, "Closet", "▦"),
    Tab(ShopRoute, "Tienda", "👜"),
    Tab(AssistantRoute, "Asistente", "✦"),
    Tab(FittingRoomRoute, "Medidas", "◎")
)

private val businessTabs = listOf(
    Tab(SalesRoute, "Ventas", "▤"),
    Tab(NewProductRoute, "Alta", "＋"),
    Tab(InventoryRoute, "Inventario", "▣")
)

private fun NavDestination?.isOn(route: Any): Boolean =
    this?.hierarchy?.any { it.hasRoute(route::class) } == true

private fun homeFor(role: UserRole): Any = if (role == UserRole.SOCIO) SalesRoute else ClosetRoute

/** Reinicia la pila de navegación en [route] (después de entrar, salir o aplicar un estilo). */
private fun NavHostController.resetTo(route: Any) = navigate(route) {
    popUpTo(graph.id) { inclusive = true }
    launchSingleTop = true
}

private fun NavHostController.switchTab(route: Any, root: Any) = navigate(route) {
    popUpTo(root) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun MonzattiNavHost(session: SessionViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val tabs = when {
        userTabs.any { destination.isOn(it.route) } -> userTabs
        businessTabs.any { destination.isOn(it.route) } -> businessTabs
        else -> emptyList()
    }
    val signOut: () -> Unit = { session.signOut { nav.resetTo(LoginRoute) } }
    val goHome: (UserProfile) -> Unit = { nav.resetTo(homeFor(it.role)) }

    Scaffold(
        containerColor = MonzattiTheme.colors.background,
        bottomBar = {
            if (tabs.isNotEmpty()) {
                val c = MonzattiTheme.colors
                NavigationBar(containerColor = c.surface) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = destination.isOn(tab.route),
                            onClick = { nav.switchTab(tab.route, tabs.first().route) },
                            icon = { Text(tab.icon, fontSize = 18.sp) },
                            label = { Text(tab.label, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = c.onAccent,
                                selectedTextColor = c.ink,
                                indicatorColor = c.accent,
                                unselectedIconColor = c.muted,
                                unselectedTextColor = c.muted
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = LoginRoute,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(260)) + slideInHorizontally(tween(300)) { it / 10 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(260)) + slideInHorizontally(tween(300)) { -it / 10 } },
            popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(300)) { it / 10 } }
        ) {
            // 1. Login / autenticación
            composable<LoginRoute> {
                LoginScreen(
                    onLoggedIn = goHome,
                    onCreate = { nav.navigate(CreateAccountRoute) },
                    onRecover = { nav.navigate(RecoveryRoute) },
                    onAbout = { nav.navigate(AboutRoute) }
                )
            }
            composable<CreateAccountRoute> {
                CreateAccountScreen(
                    onBack = { nav.popBackStack() },
                    onCreated = { nav.navigate(StyleChoiceRoute) { popUpTo<LoginRoute>() } }
                )
            }
            composable<RecoveryRoute> { RecoveryScreen(onBack = { nav.popBackStack() }) }
            composable<AboutRoute> { AboutScreen(onBack = { nav.popBackStack() }, onNext = { nav.navigate(ObjectiveRoute) }) }
            composable<ObjectiveRoute> {
                ObjectiveScreen(onBack = { nav.popBackStack() }, onOpen = { nav.popBackStack(LoginRoute, inclusive = false) })
            }

            // Estilo de la app
            composable<StyleChoiceRoute> {
                StyleChoiceScreen(
                    onPick = { nav.navigate(StylePickerRoute) },
                    onQuiz = { nav.navigate(StyleQuizRoute) },
                    onSkipped = { nav.resetTo(homeFor(it)) }
                )
            }
            composable<StylePickerRoute> {
                StylePickerScreen(
                    onBack = { nav.popBackStack() },
                    onSelect = { nav.navigate(StyleResultRoute(it.key)) }
                )
            }
            composable<StyleQuizRoute> {
                StyleQuizScreen(
                    onExit = { nav.popBackStack() },
                    onFinished = { nav.navigate(StyleResultRoute(it.key)) }
                )
            }
            composable<StyleResultRoute> { backStack ->
                StyleResultScreen(
                    styleKey = backStack.toRoute<StyleResultRoute>().styleKey,
                    onApplied = { nav.resetTo(homeFor(it)) },
                    onChooseOther = { nav.navigate(StylePickerRoute) { popUpTo<StyleChoiceRoute>() } }
                )
            }

            // Usuario final
            composable<ClosetRoute> {
                ClosetScreen(
                    onAdd = { nav.navigate(AddGarmentRoute) },
                    onTryOn = { nav.navigate(TryOnRoute(it)) },
                    onChangeStyle = { nav.navigate(StyleChoiceRoute) },
                    onSignOut = signOut
                )
            }
            composable<AddGarmentRoute> { DigitizeScreen(onDone = { nav.popBackStack() }) }
            composable<ShopRoute> {
                ShopScreen(
                    onProduct = { nav.navigate(ProductRoute(it)) },
                    onTryOn = { nav.navigate(TryOnRoute(it)) },
                    onCart = { nav.navigate(CartRoute) },
                    onOrders = { nav.navigate(OrdersRoute) }
                )
            }
            composable<ProductRoute> { backStack ->
                ProductScreen(
                    productId = backStack.toRoute<ProductRoute>().productId,
                    onBack = { nav.popBackStack() },
                    onTryOn = { nav.navigate(TryOnRoute(it)) },
                    onCart = { nav.navigate(CartRoute) }
                )
            }
            composable<CartRoute> {
                CartScreen(
                    onBack = { nav.popBackStack() },
                    onCheckout = { nav.navigate(CheckoutRoute) },
                    onShop = { nav.switchTab(ShopRoute, ClosetRoute) }
                )
            }
            composable<CheckoutRoute> {
                CheckoutScreen(
                    onBack = { nav.popBackStack() },
                    onDone = { id ->
                        nav.navigate(OrderConfirmedRoute(id)) { popUpTo<ShopRoute>() }
                    }
                )
            }
            composable<OrderConfirmedRoute> { backStack ->
                OrderConfirmedScreen(
                    orderId = backStack.toRoute<OrderConfirmedRoute>().orderId,
                    onOrders = { nav.navigate(OrdersRoute) { popUpTo<ShopRoute>() } },
                    onShop = { nav.switchTab(ShopRoute, ClosetRoute) }
                )
            }
            composable<OrdersRoute> {
                OrdersScreen(onBack = { nav.popBackStack() }, onShop = { nav.switchTab(ShopRoute, ClosetRoute) })
            }
            composable<AssistantRoute> { AssistantScreen(onTryOn = { nav.navigate(TryOnRoute()) }) }
            composable<FittingRoomRoute> {
                FittingRoomScreen(onCamera = { nav.navigate(TryOnRoute()) }, onMirror = { nav.navigate(MirrorRoute) })
            }
            composable<TryOnRoute> {
                TryOnScreen(onBack = { nav.popBackStack() }, onOpenMirror = { nav.navigate(MirrorRoute) })
            }
            composable<MirrorRoute> {
                MirrorScreen(onBack = { nav.popBackStack() }, onCamera = { nav.navigate(TryOnRoute()) { launchSingleTop = true } })
            }

            // 7. App para empresarios
            composable<SalesRoute> {
                SalesScreen(
                    onAddProduct = { nav.switchTab(NewProductRoute, SalesRoute) },
                    onChangeStyle = { nav.navigate(StyleChoiceRoute) },
                    onSignOut = signOut
                )
            }
            composable<NewProductRoute> { NewProductScreen(onPublished = { nav.switchTab(InventoryRoute, SalesRoute) }) }
            composable<InventoryRoute> { InventoryScreen() }
        }
    }
}
