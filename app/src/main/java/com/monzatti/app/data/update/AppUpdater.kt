package com.monzatti.app.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.monzatti.app.BuildConfig
import com.monzatti.app.domain.update.AvailableUpdate
import com.monzatti.app.domain.update.UpdatePolicy
import com.monzatti.app.domain.update.UpdateRepository
import com.monzatti.app.domain.update.UpdateState
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Servicio de actualizaciones para la APK repartida fuera de Play Store:
 * consulta app_releases → descarga el APK → verifica SHA-256, paquete y versión → lo instala con PackageInstaller.
 * Android siempre pide confirmar la instalación; la app no puede instalarse sola.
 */
@Singleton
class AppUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: UpdateRepository
) {
    val currentVersionCode: Int = BuildConfig.VERSION_CODE
    val currentVersionName: String = BuildConfig.VERSION_NAME
    val channel: String = BuildConfig.UPDATE_CHANNEL

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("monzatti_updates", Context.MODE_PRIVATE)
    private val updatesDir = File(context.cacheDir, "updates")

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var checkedOnLaunch = false
    private var job: Job? = null

    /** Revisión automática, una vez por proceso. */
    fun checkOnLaunch() {
        if (checkedOnLaunch) return
        checkedOnLaunch = true
        check(manual = false)
    }

    /** manual = la persona tocó «Buscar actualizaciones»: siempre muestra el resultado. */
    fun check(manual: Boolean) {
        if (_state.value.isBusy()) return
        job = scope.launch {
            if (manual) _state.value = UpdateState.Checking
            cleanOldDownloads()
            repository.releasesAfter(currentVersionCode, channel)
                .onSuccess { releases ->
                    val update = UpdatePolicy.pick(currentVersionCode, releases)
                    _state.value = when {
                        update == null -> if (manual) UpdateState.UpToDate else UpdateState.Idle
                        manual || UpdatePolicy.shouldPrompt(
                            update,
                            snoozedVersion = prefs.getInt(KEY_SNOOZED_VERSION, -1).takeIf { it > 0 },
                            snoozedAt = prefs.getLong(KEY_SNOOZED_AT, 0L),
                            now = System.currentTimeMillis()
                        ) -> UpdateState.Available(update)
                        else -> UpdateState.Idle
                    }
                }
                .onFailure { e ->
                    Log.w(TAG, "No se pudo consultar actualizaciones", e)
                    _state.value = if (manual) UpdateState.Failed(null, e.message ?: "No pudimos buscar actualizaciones.") else UpdateState.Idle
                }
        }
    }

    /** «Actualizar ahora». */
    fun startUpdate() {
        val update = _state.value.update() ?: return
        if (_state.value.isBusy()) return
        job = scope.launch {
            try {
                val apk = download(update)
                verify(apk, update)
                if (!canInstall()) {
                    _state.value = UpdateState.NeedsPermission(update)
                } else {
                    install(apk, update)
                }
            } catch (e: CancellationException) {
                _state.value = UpdateState.Available(update)
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Falló la actualización", e)
                _state.value = UpdateState.Failed(update, e.userMessage())
            }
        }
    }

    fun cancelDownload() {
        if (_state.value is UpdateState.Downloading) job?.cancel()
    }

    /** Abre el ajuste «Instalar apps desconocidas» de Monzatti. */
    fun openInstallPermissionSettings(from: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        if (from !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        from.startActivity(intent)
    }

    /** Al volver de Ajustes: si ya dio permiso, continúa con la instalación. */
    fun onResume() {
        val state = _state.value
        if (state is UpdateState.NeedsPermission && canInstall()) startUpdate()
    }

    /** «Más tarde» o cerrar un mensaje. Las obligatorias no se pueden posponer. */
    fun dismiss() {
        when (val state = _state.value) {
            is UpdateState.Available -> if (!state.update.mandatory) snooze(state.update)
            is UpdateState.NeedsPermission -> if (state.update.mandatory) {
                _state.value = UpdateState.Available(state.update)
            } else snooze(state.update)
            is UpdateState.Failed -> _state.value = if (state.update?.mandatory == true) {
                UpdateState.Available(state.update)
            } else UpdateState.Idle
            UpdateState.UpToDate -> _state.value = UpdateState.Idle
            else -> Unit
        }
    }

    // --- Resultado de la instalación (lo entrega UpdateInstallReceiver) ---

    internal fun onInstallResult(status: Int, message: String?) {
        val update = _state.value.update()
        _state.value = when (status) {
            PackageInstaller.STATUS_SUCCESS -> UpdateState.Idle // Android reinicia la app con la versión nueva.
            PackageInstaller.STATUS_FAILURE_ABORTED ->
                if (update != null) UpdateState.Available(update) else UpdateState.Idle
            else -> {
                Log.w(TAG, "Instalación fallida: $status $message")
                UpdateState.Failed(update, installErrorMessage(status))
            }
        }
    }

    // --- Pasos ---

    private fun snooze(update: AvailableUpdate) {
        prefs.edit()
            .putInt(KEY_SNOOZED_VERSION, update.release.versionCode)
            .putLong(KEY_SNOOZED_AT, System.currentTimeMillis())
            .apply()
        _state.value = UpdateState.Idle
    }

    private fun apkFile(update: AvailableUpdate) = File(updatesDir, "monzatti-${update.release.versionCode}.apk")

    private suspend fun download(update: AvailableUpdate): File = withContext(Dispatchers.IO) {
        val target = apkFile(update)
        // Si ya se descargó completo y es válido, no se vuelve a bajar.
        if (target.exists() && runCatching { verify(target, update) }.isSuccess) return@withContext target
        updatesDir.mkdirs()
        val part = File(updatesDir, target.name + ".part")
        _state.value = UpdateState.Downloading(update, null)
        // Las redes móviles cortan descargas largas: se reintenta y se continúa donde se quedó.
        var attempt = 1
        while (true) {
            try {
                downloadChunk(update, part)
                break
            } catch (e: IOException) {
                if (attempt >= DOWNLOAD_ATTEMPTS) throw e
                Log.w(TAG, "Descarga interrumpida (intento $attempt), se reanuda", e)
                attempt++
                delay(1_500L * attempt)
            }
        }
        target.delete()
        if (!part.renameTo(target)) throw IOException("No se pudo guardar la descarga")
        target
    }

    /** Descarga (o continúa con Range) hasta completar el archivo .part. */
    private suspend fun downloadChunk(update: AvailableUpdate, part: File) = withContext(Dispatchers.IO) {
        val already = if (part.exists()) part.length() else 0L
        val connection = (URL(update.release.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            if (already > 0) setRequestProperty("Range", "bytes=$already-")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            // 206 = el servidor continúa; 200 = manda todo de nuevo y se empieza desde cero.
            val resumed = code == HttpURLConnection.HTTP_PARTIAL && already > 0
            var done = if (resumed) already else 0L
            val total = connection.contentLengthLong.takeIf { it > 0 }?.plus(done)
            connection.inputStream.use { input ->
                java.io.FileOutputStream(part, resumed).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var lastShown = -1
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total != null) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != lastShown) {
                                lastShown = percent
                                _state.value = UpdateState.Downloading(update, percent / 100f)
                            }
                        }
                    }
                }
            }
            if (total != null && done < total) throw IOException("Descarga incompleta: $done de $total")
        } finally {
            connection.disconnect()
        }
    }

    private fun verify(apk: File, update: AvailableUpdate) {
        if (!UpdatePolicy.sha256Matches(update.release.sha256, sha256(apk))) {
            apk.delete()
            throw UpdateException("La descarga llegó dañada. Vuelve a intentarlo.")
        }
        val info = context.packageManager.getPackageArchiveInfo(apk.path, 0)
        val version = info?.let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong() }
        if (!UpdatePolicy.isExpectedPackage(context.packageName, info?.packageName, version, update.release)) {
            apk.delete()
            throw UpdateException("El archivo publicado no corresponde a Monzatti ${update.release.versionName}.")
        }
    }

    private fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    private suspend fun install(apk: File, update: AvailableUpdate) = withContext(Dispatchers.IO) {
        _state.value = UpdateState.Installing(update)
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("monzatti.apk", 0, apk.length()).use { output ->
                    apk.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }
                val intent = Intent(context, UpdateInstallReceiver::class.java).setPackage(context.packageName)
                // MUTABLE: el sistema agrega el estado y la pantalla de confirmación al intent.
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            installer.abandonSession(sessionId)
            throw e
        }
    }

    private fun cleanOldDownloads() {
        updatesDir.listFiles()?.forEach { file ->
            val version = file.name.removePrefix("monzatti-").substringBefore('.').toIntOrNull()
            if (version == null || version <= currentVersionCode) file.delete()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun installErrorMessage(status: Int): String = when (status) {
        PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
            "Esta versión está firmada con otra llave. Desinstala Monzatti e instala el APK nuevo a mano."
        PackageInstaller.STATUS_FAILURE_STORAGE -> "No hay espacio suficiente en el teléfono."
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "El teléfono bloqueó la instalación."
        else -> "No se pudo instalar la actualización. Vuelve a intentarlo."
    }

    private fun Exception.userMessage(): String = when (this) {
        is UpdateException -> message.orEmpty()
        is IOException -> "No pudimos descargar la actualización. Revisa tu conexión."
        else -> "No se pudo completar la actualización."
    }

    private class UpdateException(message: String) : Exception(message)

    private companion object {
        const val TAG = "MonzattiUpdate"
        const val KEY_SNOOZED_VERSION = "snoozed_version"
        const val KEY_SNOOZED_AT = "snoozed_at"
        const val DOWNLOAD_ATTEMPTS = 4
    }
}

private fun UpdateState.isBusy() =
    this is UpdateState.Checking || this is UpdateState.Downloading || this is UpdateState.Installing

private fun UpdateState.update(): AvailableUpdate? = when (this) {
    is UpdateState.Available -> update
    is UpdateState.Downloading -> update
    is UpdateState.NeedsPermission -> update
    is UpdateState.Installing -> update
    is UpdateState.Failed -> update
    else -> null
}

/** Recibe el resultado de PackageInstaller y, si hace falta, abre la pantalla de confirmación de Android. */
@AndroidEntryPoint
class UpdateInstallReceiver : BroadcastReceiver() {

    @Inject lateinit var updater: AppUpdater

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(confirm)
                return
            }
        }
        updater.onInstallResult(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }
}
