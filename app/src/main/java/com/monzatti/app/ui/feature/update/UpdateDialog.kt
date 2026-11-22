package com.monzatti.app.ui.feature.update

import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.monzatti.app.core.theme.MonzattiTheme
import com.monzatti.app.data.update.AppUpdater
import com.monzatti.app.domain.update.AvailableUpdate
import com.monzatti.app.domain.update.UpdateState
import com.monzatti.app.ui.components.Brand
import com.monzatti.app.ui.components.ErrorText
import com.monzatti.app.ui.components.FieldLabel
import com.monzatti.app.ui.components.MutedText
import com.monzatti.app.ui.components.PrimaryButton
import com.monzatti.app.ui.components.Title
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class UpdateViewModel @Inject constructor(private val updater: AppUpdater) : ViewModel() {
    val state: StateFlow<UpdateState> = updater.state
    val versionLabel = "${updater.currentVersionName} (${updater.currentVersionCode})"
    val channel = updater.channel

    fun checkOnLaunch() = updater.checkOnLaunch()
    fun checkNow() = updater.check(manual = true)
    fun update() = updater.startUpdate()
    fun cancel() = updater.cancelDownload()
    fun dismiss() = updater.dismiss()
    fun onResume() = updater.onResume()
    fun openPermission(context: Context) = updater.openInstallPermissionSettings(context)
}

/** Aviso global de actualización; vive sobre toda la navegación. */
@Composable
fun UpdateHost(viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.checkOnLaunch() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    val update = when (val s = state) {
        is UpdateState.Available -> s.update
        is UpdateState.Downloading -> s.update
        is UpdateState.NeedsPermission -> s.update
        is UpdateState.Installing -> s.update
        is UpdateState.Failed -> s.update
        else -> null
    } ?: return

    val context = LocalContext.current
    val busy = state is UpdateState.Downloading || state is UpdateState.Installing
    Dialog(
        onDismissRequest = { if (!busy && !update.mandatory) viewModel.dismiss() },
        properties = DialogProperties(dismissOnBackPress = !update.mandatory, dismissOnClickOutside = false)
    ) {
        UpdateCard(
            update = update,
            state = state,
            currentVersion = viewModel.versionLabel,
            onUpdate = viewModel::update,
            onCancel = viewModel::cancel,
            onLater = viewModel::dismiss,
            onPermission = { viewModel.openPermission(context) }
        )
    }
}

@Composable
private fun UpdateCard(
    update: AvailableUpdate,
    state: UpdateState,
    currentVersion: String,
    onUpdate: () -> Unit,
    onCancel: () -> Unit,
    onLater: () -> Unit,
    onPermission: () -> Unit
) {
    val c = MonzattiTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.background)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Brand()
        FieldLabel(if (update.mandatory) "ACTUALIZACIÓN NECESARIA" else "NUEVA VERSIÓN DISPONIBLE")
        Title("Monzatti ${update.release.versionName}")
        MutedText(
            if (update.mandatory) "Esta versión es necesaria para seguir usando la app. Tienes la $currentVersion."
            else "Tienes la $currentVersion."
        )

        if (update.notes.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.surface)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                update.notes.forEach { (version, notes) ->
                    if (update.notes.size > 1) Text(version, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(notes, color = c.ink, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }

        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "update"
        ) { s ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (s) {
                    is UpdateState.Downloading -> {
                        val progress = s.progress
                        if (progress == null) {
                            LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = c.accent, trackColor = c.line)
                        } else {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = c.accent,
                                trackColor = c.line
                            )
                        }
                        Row {
                            MutedText(if (progress == null) "Descargando…" else "Descargando… ${(progress * 100).toInt()} %")
                        }
                        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancelar", color = c.muted) }
                    }
                    is UpdateState.Installing -> {
                        PrimaryButton("Instalando", onClick = {}, accent = true, loading = true)
                        MutedText("Confirma la instalación en la ventana de Android. Monzatti se reiniciará con la versión nueva.")
                    }
                    is UpdateState.NeedsPermission -> {
                        MutedText("Para instalarla, Android necesita que permitas a Monzatti instalar apps. Activa el permiso y vuelve aquí.")
                        PrimaryButton("Permitir instalación", onClick = onPermission, accent = true)
                        if (!update.mandatory) LaterButton(onLater)
                    }
                    is UpdateState.Failed -> {
                        ErrorText(s.message)
                        PrimaryButton("Reintentar", onClick = onUpdate, accent = true)
                        if (!update.mandatory) LaterButton(onLater)
                    }
                    else -> {
                        PrimaryButton("Actualizar ahora", onClick = onUpdate, accent = true)
                        if (!update.mandatory) LaterButton(onLater)
                    }
                }
            }
        }
    }
}

@Composable
private fun LaterButton(onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text("Más tarde", color = MonzattiTheme.colors.muted)
    }
}

/** Tarjeta de versión con «Buscar actualizaciones», para la pantalla «¿Qué es Monzatti?». */
@Composable
fun VersionCard(viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = MonzattiTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FieldLabel("VERSIÓN")
        Text("Monzatti ${viewModel.versionLabel}", color = c.ink, fontWeight = FontWeight.Medium)
        if (viewModel.channel != "estable") MutedText("Canal de ${viewModel.channel}")
        when (val s = state) {
            UpdateState.UpToDate -> Text("Ya tienes la versión más reciente.", color = c.positive, fontSize = 12.sp)
            is UpdateState.Failed -> if (s.update == null) ErrorText(s.message)
            else -> Unit
        }
        TextButton(
            onClick = viewModel::checkNow,
            enabled = state !is UpdateState.Checking,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
        ) {
            Text(if (state is UpdateState.Checking) "Buscando…" else "Buscar actualizaciones", color = c.accent, fontWeight = FontWeight.SemiBold)
        }
    }
}
