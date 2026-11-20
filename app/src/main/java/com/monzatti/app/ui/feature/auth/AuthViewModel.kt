package com.monzatti.app.ui.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monzatti.app.core.config.SupabaseConfig
import com.monzatti.app.domain.model.SignUpRequest
import com.monzatti.app.domain.model.UserProfile
import com.monzatti.app.domain.model.UserRole
import com.monzatti.app.domain.repository.AuthRepository
import com.monzatti.app.domain.repository.StyleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val resetSent: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val styles: StyleRepository,
    config: SupabaseConfig
) : ViewModel() {

    /** Hay claves de Supabase: se puede alternar al modo demo (útil si falla internet en la presentación). */
    val canToggleDemo: Boolean = config.hasKeys
    val isDemo: Boolean = !config.isConfigured

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun signIn(email: String, password: String, role: UserRole, onSuccess: (UserProfile) -> Unit) = run {
        auth.signIn(email, password, role).onSuccess { profile ->
            // El estilo guardado en el perfil gana sobre el del dispositivo.
            profile.style?.let { styles.setStyle(it) }
            onSuccess(profile)
        }
    }

    fun signUp(request: SignUpRequest, onSuccess: (UserProfile) -> Unit) = run {
        auth.signUp(request).onSuccess(onSuccess)
    }

    fun sendReset(email: String) = run {
        auth.sendPasswordReset(email).onSuccess { _state.update { it.copy(resetSent = true) } }
    }

    private fun run(block: suspend () -> Result<*>) {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = block()
            _state.update {
                it.copy(loading = false, error = result.exceptionOrNull()?.let { e -> e.message ?: "Algo salió mal. Intenta de nuevo." })
            }
        }
    }
}
