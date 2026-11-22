package com.monzatti.app.data.local

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fotos de prendas digitalizadas (fondo transparente) guardadas en el almacenamiento privado de la app.
 * Se nombran con el id de la prenda para encontrarlas aunque los datos vengan de Supabase.
 */
@Singleton
class GarmentImages @Inject constructor(@ApplicationContext context: Context) {

    // Al arrancar ningún borrador está en uso: se borran los que quedaron de sesiones anteriores.
    private val dir = File(context.filesDir, "garments").apply {
        mkdirs()
        listFiles { f -> f.name.startsWith(DRAFT_PREFIX) }?.forEach { it.delete() }
    }

    fun fileFor(id: String): File = File(dir, "$id.png")

    fun pathIfExists(id: String): String? = fileFor(id).takeIf { it.exists() }?.absolutePath

    /** Guarda recortando el borde transparente, así la prenda llena la imagen (mejor ajuste en el probador). */
    fun save(id: String, bitmap: Bitmap): String {
        val cropped = cropTransparent(bitmap)
        val file = fileFor(id)
        FileOutputStream(file).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file.absolutePath
    }

    /** Guarda en un archivo temporal mientras la persona revisa la prenda antes de guardarla. */
    fun saveDraft(bitmap: Bitmap): String = save("$DRAFT_PREFIX${System.currentTimeMillis()}", bitmap)

    /** Copia un borrador al nombre definitivo de la prenda; el borrador se queda para reintentar si falla el guardado. */
    fun promote(draftPath: String, id: String): String {
        val target = fileFor(id)
        File(draftPath).copyTo(target, overwrite = true)
        return target.absolutePath
    }

    /** Guarda una foto descargada de Storage. */
    fun saveBytes(id: String, bytes: ByteArray): String = fileFor(id).apply { writeBytes(bytes) }.absolutePath

    fun discardDraft(path: String?) {
        path?.let(::File)?.takeIf { it.name.startsWith(DRAFT_PREFIX) }?.delete()
    }

    fun delete(id: String) {
        fileFor(id).delete()
    }

    companion object {
        private const val DRAFT_PREFIX = "draft-"

        fun cropTransparent(bitmap: Bitmap): Bitmap {
            if (!bitmap.hasAlpha()) return bitmap
            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            var minX = w; var minY = h; var maxX = -1; var maxY = -1
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    if ((pixels[row + x] ushr 24) > 16) {
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                }
            }
            if (maxX < 0) return bitmap
            return Bitmap.createBitmap(bitmap, minX, minY, maxX - minX + 1, maxY - minY + 1)
        }
    }
}
