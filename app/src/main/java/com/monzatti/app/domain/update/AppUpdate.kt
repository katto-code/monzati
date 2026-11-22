package com.monzatti.app.domain.update

/** Una versión publicada de la app (tabla app_releases). */
data class AppRelease(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String?,
    val notes: String,
    val mandatory: Boolean
)

/** Actualización que se ofrece: la versión más nueva, y si alguna intermedia era obligatoria. */
data class AvailableUpdate(
    val release: AppRelease,
    val mandatory: Boolean,
    /** Notas de todas las versiones que se instalan de golpe, de la más nueva a la más vieja. */
    val notes: List<Pair<String, String>>
)

interface UpdateRepository {
    /** Versiones publicadas del canal con version_code mayor al instalado. */
    suspend fun releasesAfter(versionCode: Int, channel: String): Result<List<AppRelease>>
}

object UpdatePolicy {
    /** Aviso automático al abrir: si se pospuso, no vuelve a aparecer hasta pasado este tiempo. */
    const val SNOOZE_MILLIS = 24 * 60 * 60 * 1000L

    fun pick(currentVersionCode: Int, releases: List<AppRelease>): AvailableUpdate? {
        val newer = releases.filter { it.versionCode > currentVersionCode }.sortedByDescending { it.versionCode }
        val latest = newer.firstOrNull() ?: return null
        return AvailableUpdate(
            release = latest,
            // Si te saltas una versión obligatoria, la nueva también lo es para ti.
            mandatory = newer.any { it.mandatory },
            notes = newer.filter { it.notes.isNotBlank() }.map { it.versionName to it.notes.trim() }
        )
    }

    /** ¿Mostrar el aviso sin que la persona lo pida? */
    fun shouldPrompt(update: AvailableUpdate, snoozedVersion: Int?, snoozedAt: Long, now: Long): Boolean =
        update.mandatory || snoozedVersion != update.release.versionCode || now - snoozedAt >= SNOOZE_MILLIS

    /** El APK descargado debe ser esta misma app y exactamente la versión anunciada. */
    fun isExpectedPackage(packageName: String, archivePackage: String?, archiveVersion: Long?, release: AppRelease): Boolean =
        archivePackage == packageName && archiveVersion == release.versionCode.toLong()

    fun sha256Matches(expected: String?, actual: String): Boolean =
        expected.isNullOrBlank() || expected.equals(actual, ignoreCase = true)
}

/** Estado del servicio de actualizaciones, para el diálogo y la pantalla «Acerca de». */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val update: AvailableUpdate) : UpdateState
    /** progress en 0..1, o null si el servidor no informa el tamaño. */
    data class Downloading(val update: AvailableUpdate, val progress: Float?) : UpdateState
    /** Android pide permitir «instalar apps desconocidas» para Monzatti. */
    data class NeedsPermission(val update: AvailableUpdate) : UpdateState
    data class Installing(val update: AvailableUpdate) : UpdateState
    data class Failed(val update: AvailableUpdate?, val message: String) : UpdateState
}
