package com.monzatti.app.ui.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monzatti.app.core.theme.MonzattiTheme
import com.monzatti.app.domain.model.Gender
import com.monzatti.app.domain.model.PasswordRules
import com.monzatti.app.domain.model.SignUpRequest
import com.monzatti.app.domain.model.UserProfile
import com.monzatti.app.domain.model.UserRole
import com.monzatti.app.ui.components.AppTextField
import com.monzatti.app.ui.components.BackLink
import com.monzatti.app.ui.components.Brand
import com.monzatti.app.ui.components.ChoiceChip
import com.monzatti.app.ui.components.ErrorText
import com.monzatti.app.ui.components.FieldLabel
import com.monzatti.app.ui.components.InfoCard
import com.monzatti.app.ui.components.MutedText
import com.monzatti.app.ui.components.PrimaryButton
import com.monzatti.app.ui.components.ScreenColumn
import com.monzatti.app.ui.components.SecondaryButton
import com.monzatti.app.ui.components.Title

@Composable
fun LoginScreen(
    onLoggedIn: (UserProfile) -> Unit,
    onCreate: () -> Unit,
    onRecover: () -> Unit,
    onAbout: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var role by rememberSaveable { mutableStateOf(UserRole.USUARIO) }
    var email by rememberSaveable { mutableStateOf("hola@ejemplo.com") }
    var password by rememberSaveable { mutableStateOf("12345678") }

    ScreenColumn {
        Brand()
        Spacer(Modifier.height(20.dp))
        Title("Vuelve a ti.")
        MutedText("Tu closet, tu espejo y tu próxima versión.")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UserRole.selectable.forEach { ChoiceChip(it.label, selected = role == it, onClick = { role = it }) }
        }
        FieldLabel("CORREO ELECTRÓNICO")
        AppTextField(email, { email = it })
        FieldLabel("CONTRASEÑA")
        AppTextField(password, { password = it }, password = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onRecover) { Text("¿Olvidaste tu contraseña?", color = MonzattiTheme.colors.muted) }
        }
        ErrorText(state.error)
        PrimaryButton(
            if (state.loading) "Entrando…" else "Entrar a mi universo  →",
            onClick = { viewModel.signIn(email, password, role, onLoggedIn) },
            enabled = !state.loading
        )
        SecondaryButton("Crear cuenta", onClick = onCreate)
        TextButton(onClick = onAbout, modifier = Modifier.fillMaxWidth()) {
            Text("Conoce el proyecto Monzatti", color = MonzattiTheme.colors.muted)
        }
        MutedText("Al continuar, aceptas los Términos y el Aviso de privacidad.")
    }
}

@Composable
fun CreateAccountScreen(
    onBack: () -> Unit,
    onCreated: (UserProfile) -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var role by rememberSaveable { mutableStateOf(UserRole.USUARIO) }
    var gender by rememberSaveable { mutableStateOf<Gender?>(null) }
    var name by rememberSaveable { mutableStateOf("Valentina García") }
    var email by rememberSaveable { mutableStateOf("valentina@ejemplo.com") }
    var password by rememberSaveable { mutableStateOf("Monzatti1") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    ScreenColumn {
        BackLink("← Volver", onBack)
        Title("Un estilo único. Una cuenta tuya.")
        FieldLabel("TIPO DE CUENTA")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UserRole.selectable.forEach { ChoiceChip(it.label, selected = role == it, onClick = { role = it }) }
        }
        FieldLabel("NOMBRE COMPLETO")
        AppTextField(name, { name = it })
        FieldLabel("¿CÓMO TE IDENTIFICAS?")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Gender.entries.forEach { ChoiceChip(it.label, selected = gender == it, onClick = { gender = it }) }
        }
        FieldLabel("CORREO")
        AppTextField(email, { email = it })
        FieldLabel("CONTRASEÑA")
        AppTextField(password, { password = it }, password = true)
        MutedText(PasswordRules.DESCRIPTION)
        ErrorText(localError ?: state.error)
        PrimaryButton(
            if (state.loading) "Creando…" else "Crear mi cuenta",
            onClick = {
                val g = gender
                if (g == null) {
                    localError = "Elige femenino o masculino para personalizar tu experiencia."
                } else {
                    localError = null
                    viewModel.signUp(SignUpRequest(name, email, password, role, g), onCreated)
                }
            },
            enabled = !state.loading
        )
    }
}

@Composable
fun RecoveryScreen(onBack: () -> Unit, viewModel: AuthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("valentina@ejemplo.com") }
    ScreenColumn {
        BackLink("← Volver", onBack)
        Spacer(Modifier.height(24.dp))
        Title("Tu universo te espera.")
        MutedText("Ingresa tu correo y te enviaremos un enlace para crear una nueva contraseña.")
        FieldLabel("CORREO ELECTRÓNICO")
        AppTextField(email, { email = it })
        ErrorText(state.error)
        PrimaryButton("Enviar enlace", onClick = { viewModel.sendReset(email) }, enabled = !state.loading)
        if (state.resetSent) InfoCard("✓  Enlace enviado. Es válido por 30 minutos. Revisa tu carpeta de spam.")
    }
}
