package com.monzatti.app.ui.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import com.monzatti.app.core.config.DemoMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    onPrivacy: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var role by rememberSaveable { mutableStateOf(UserRole.USUARIO) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { viewModel.restore(onLoggedIn) }

    ScreenColumn {
        Brand()
        Spacer(Modifier.height(20.dp))
        Title("Vuelve a ti.")
        MutedText("Tu closet, tu espejo y tu próxima versión.")
        Spacer(Modifier.height(8.dp))
        // Con Supabase el tipo de cuenta sale del perfil; elegirlo solo tiene sentido en el modo demo.
        if (viewModel.isDemo) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UserRole.selectable.forEach { ChoiceChip(it.label, selected = role == it, onClick = { role = it }) }
            }
        }
        FieldLabel("CORREO ELECTRÓNICO")
        AppTextField(email, { email = it }, placeholder = "tucorreo@ejemplo.com", email = true)
        FieldLabel("CONTRASEÑA")
        AppTextField(password, { password = it }, password = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onRecover) { Text("¿Olvidaste tu contraseña?", color = MonzattiTheme.colors.muted) }
        }
        ErrorText(state.error)
        PrimaryButton(
            "Entrar a mi universo",
            onClick = { viewModel.signIn(email, password, role, onLoggedIn) },
            loading = state.loading
        )
        SecondaryButton("Crear cuenta", onClick = onCreate)
        TextButton(onClick = onAbout, modifier = Modifier.fillMaxWidth()) {
            Text("Conoce el proyecto Monzatti", color = MonzattiTheme.colors.muted)
        }
        PrivacyLink(onPrivacy)
        if (viewModel.canToggleDemo) {
            val activity = LocalContext.current as? Activity
            TextButton(onClick = { activity?.let { DemoMode.setAndRestart(it, !viewModel.isDemo) } }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (viewModel.isDemo) "Modo demo activo · Volver a conectar con Supabase" else "Usar modo demo (sin conexión)",
                    color = MonzattiTheme.colors.muted,
                    fontSize = 13.sp
                )
            }
        } else {
            MutedText("Modo demo · datos de ejemplo")
        }
    }
}

@Composable
fun CreateAccountScreen(
    onBack: () -> Unit,
    onCreated: (UserProfile) -> Unit,
    onPrivacy: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var role by rememberSaveable { mutableStateOf(UserRole.USUARIO) }
    var genderChoice by rememberSaveable { mutableStateOf(-1) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    ScreenColumn {
        BackLink("Volver", onBack)
        Title("Un estilo único. Una cuenta tuya.")
        FieldLabel("TIPO DE CUENTA")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UserRole.selectable.forEach { ChoiceChip(it.label, selected = role == it, onClick = { role = it }) }
        }
        FieldLabel("NOMBRE COMPLETO")
        AppTextField(name, { name = it })
        FieldLabel("¿CÓMO TE IDENTIFICAS?")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GenderChoices.forEachIndexed { i, (label, _) -> ChoiceChip(label, selected = genderChoice == i, onClick = { genderChoice = i }) }
        }
        FieldLabel("CORREO")
        AppTextField(email, { email = it }, placeholder = "tucorreo@ejemplo.com", email = true)
        FieldLabel("CONTRASEÑA")
        AppTextField(password, { password = it }, password = true)
        MutedText(PasswordRules.DESCRIPTION)
        ErrorText(localError ?: state.error)
        PrimaryButton(
            "Crear mi cuenta",
            onClick = {
                if (genderChoice < 0) {
                    localError = "Elige una opción en «¿Cómo te identificas?»."
                } else {
                    localError = null
                    viewModel.signUp(SignUpRequest(name, email, password, role, GenderChoices[genderChoice].second), onCreated)
                }
            },
            loading = state.loading
        )
        PrivacyLink(onPrivacy, action = "crear tu cuenta")
    }
}

@Composable
fun RecoveryScreen(onBack: () -> Unit, viewModel: AuthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    ScreenColumn {
        BackLink("Volver", onBack)
        Spacer(Modifier.height(24.dp))
        Title("Tu universo te espera.")
        MutedText("Ingresa tu correo y te enviaremos un enlace para crear una nueva contraseña.")
        FieldLabel("CORREO ELECTRÓNICO")
        AppTextField(email, { email = it }, placeholder = "tucorreo@ejemplo.com", email = true)
        ErrorText(state.error)
        PrimaryButton("Enviar enlace", onClick = { viewModel.sendReset(email) }, loading = state.loading)
        if (state.resetSent) InfoCard("Enlace enviado. Es válido por 30 minutos. Revisa tu carpeta de spam.", icon = Icons.Filled.CheckCircle)
    }
}

/** El género solo adapta nombres y fotos del cuestionario; null = prefiere no decirlo. */
private val GenderChoices: List<Pair<String, Gender?>> =
    Gender.entries.map { it.label to it } + ("Prefiero no decirlo" to null)

@Composable
private fun PrivacyLink(onPrivacy: () -> Unit, action: String = "continuar") {
    TextButton(onClick = onPrivacy, modifier = Modifier.fillMaxWidth()) {
        Text(
            "Al $action, aceptas el Aviso de privacidad. Léelo aquí.",
            color = MonzattiTheme.colors.muted,
            fontSize = 13.sp,
            textDecoration = TextDecoration.Underline
        )
    }
}
