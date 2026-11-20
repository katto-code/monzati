package com.monzatti.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.monzatti.app.core.theme.MonzattiTheme
import com.monzatti.app.core.theme.colors
import com.monzatti.app.domain.model.StylePalette
import com.monzatti.app.domain.repository.StyleRepository
import com.monzatti.app.ui.navigation.MonzattiNavHost
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MainViewModel @Inject constructor(styles: StyleRepository) : ViewModel() {
    private val _ready = MutableStateFlow(false)
    /** El splash se mantiene hasta conocer el estilo guardado, para no parpadear de colores. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    val style: StateFlow<StylePalette> = styles.style
        .onEach { _ready.value = true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StylePalette.MONZATTI)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { !viewModel.ready.value }
        super.onCreate(savedInstanceState)
        setContent {
            val style by viewModel.style.collectAsStateWithLifecycle()
            val colors = style.colors()
            // Barras del sistema acordes al estilo (claras u oscuras).
            DisposableEffect(style) {
                val bar = colors.background.toArgb()
                val systemStyle = if (colors.isDark) SystemBarStyle.dark(bar) else SystemBarStyle.light(bar, bar)
                enableEdgeToEdge(statusBarStyle = systemStyle, navigationBarStyle = systemStyle)
                onDispose { }
            }
            MonzattiTheme(style) {
                MonzattiNavHost()
            }
        }
    }
}
