package com.monzatti.app.data.update

import com.monzatti.app.data.supabase.friendlyError
import com.monzatti.app.domain.update.AppRelease
import com.monzatti.app.domain.update.UpdateRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppReleaseDto(
    @SerialName("version_code") val versionCode: Int,
    @SerialName("version_name") val versionName: String,
    @SerialName("apk_url") val apkUrl: String,
    val sha256: String? = null,
    val notes: String = "",
    val mandatory: Boolean = false
)

/** Lee la tabla app_releases. Funciona sin sesión (la consulta usa la clave anónima). */
@Singleton
class SupabaseUpdateRepository @Inject constructor(private val client: SupabaseClient) : UpdateRepository {
    override suspend fun releasesAfter(versionCode: Int, channel: String): Result<List<AppRelease>> = try {
        val rows = client.from("app_releases")
            .select(Columns.list("version_code", "version_name", "apk_url", "sha256", "notes", "mandatory")) {
                filter {
                    eq("channel", channel)
                    eq("published", true)
                    gt("version_code", versionCode)
                }
            }
            .decodeList<AppReleaseDto>()
        Result.success(rows.map { AppRelease(it.versionCode, it.versionName, it.apkUrl, it.sha256, it.notes, it.mandatory) })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(friendlyError(e))
    }
}

/** Modo demo (sin Supabase): nunca hay actualizaciones. */
@Singleton
class NoUpdatesRepository @Inject constructor() : UpdateRepository {
    override suspend fun releasesAfter(versionCode: Int, channel: String): Result<List<AppRelease>> =
        Result.success(emptyList())
}
