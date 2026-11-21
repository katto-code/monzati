package com.monzatti.app.data.remote

import com.monzatti.app.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cliente del servidor de fotos con IA (tryon-server/). La dirección y la clave vienen de local.properties:
 * TRYON_SERVER_URL y TRYON_SERVER_TOKEN. Sin ellas, la función se muestra como "no configurada".
 */
@Singleton
class AiTryOnClient @Inject constructor() {

    private val baseUrl = BuildConfig.TRYON_SERVER_URL.trimEnd('/')
    private val token = BuildConfig.TRYON_SERVER_TOKEN

    val isConfigured: Boolean get() = baseUrl.isNotBlank() && token.isNotBlank()

    /** category: upper | lower | overall. Devuelve el JPEG generado. */
    suspend fun generate(personJpeg: ByteArray, garmentPng: ByteArray, category: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                check(isConfigured) { "El servidor de IA no está configurado." }
                val boundary = "monzatti-" + UUID.randomUUID()
                val connection = (URL("$baseUrl/tryon").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 10_000
                    readTimeout = 150_000
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    setRequestProperty("X-Monzatti-Token", token)
                }
                DataOutputStream(connection.outputStream).use { out ->
                    fun field(name: String, value: String) {
                        out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
                    }
                    fun file(name: String, filename: String, type: String, bytes: ByteArray) {
                        out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"; filename=\"$filename\"\r\n")
                        out.writeBytes("Content-Type: $type\r\n\r\n")
                        out.write(bytes)
                        out.writeBytes("\r\n")
                    }
                    file("person", "person.jpg", "image/jpeg", personJpeg)
                    file("garment", "garment.png", "image/png", garmentPng)
                    field("category", category)
                    out.writeBytes("--$boundary--\r\n")
                }
                val code = connection.responseCode
                if (code != 200) {
                    val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    error(
                        when (code) {
                            401 -> "La clave del servidor de IA no coincide."
                            429 -> "El servidor de IA está ocupado. Intenta en unos segundos."
                            503 -> "El modelo de IA todavía está cargando."
                            else -> "El servidor de IA respondió $code. $detail".trim()
                        }
                    )
                }
                connection.inputStream.use { input ->
                    ByteArrayOutputStream().also { input.copyTo(it) }.toByteArray()
                }
            }.recoverCatching { e ->
                throw if (e is java.io.IOException) IllegalStateException("No se pudo conectar con el servidor de IA.", e) else e
            }
        }
}
