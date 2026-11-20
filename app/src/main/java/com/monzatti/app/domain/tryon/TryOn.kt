package com.monzatti.app.domain.tryon

import com.monzatti.app.domain.model.Garment
import com.monzatti.app.domain.model.Product

/** Cómo se dibuja una prenda sobre el cuerpo en el probador con cámara. */
enum class GarmentKind {
    /** Blusa o playera: manga corta, de hombros a cadera. */
    BLOUSE,

    /** Chamarra, blazer o abrigo: manga larga, abierta al frente, un poco más larga. */
    JACKET,

    /** Vestido: torso y falda hasta las rodillas. */
    DRESS;

    companion object {
        /** Null si la prenda aún no se puede probar con cámara (pantalones, zapatos). */
        fun from(category: String, name: String): GarmentKind? {
            val text = "$category $name".lowercase()
            return when {
                listOf("vestido").any { it in text } -> DRESS
                listOf("chamarra", "blazer", "saco", "abrigo", "chaqueta", "sudadera", "hoodie").any { it in text } -> JACKET
                listOf("blusa", "playera", "camisa", "top", "suéter", "sueter", "polo").any { it in text } -> BLOUSE
                else -> null
            }
        }
    }
}

enum class TryOnSource { SHOP, CLOSET }

/** Una prenda lista para probarse: viene de la tienda o del closet. */
data class TryOnItem(
    val id: String,
    val name: String,
    val subtitle: String,
    val kind: GarmentKind,
    /** Color ARGB. */
    val color: Long,
    val source: TryOnSource,
    val price: Int? = null
)

fun Product.toTryOnItem(): TryOnItem? = GarmentKind.from(category, name)?.let { kind ->
    TryOnItem(id, name, brand, kind, GarmentColors.resolve(color), TryOnSource.SHOP, price)
}

fun Garment.toTryOnItem(): TryOnItem? = GarmentKind.from(category, name)?.let { kind ->
    TryOnItem(id, name, "Mi closet", kind, GarmentColors.resolve(color), TryOnSource.CLOSET)
}

/** Colores de prenda por nombre, para dibujarlas y para elegirlos al registrar una prenda. */
object GarmentColors {

    val named: Map<String, Long> = linkedMapOf(
        "Negro" to 0xFF1C1C1C,
        "Blanco" to 0xFFF5F5F2,
        "Marfil" to 0xFFF0E6D2,
        "Beige" to 0xFFD8C3A5,
        "Camel" to 0xFFC19A6B,
        "Café" to 0xFF6F4E37,
        "Rosa pastel" to 0xFFF4C2C2,
        "Rosa palo" to 0xFFDCAE96,
        "Rojo" to 0xFFB3261E,
        "Vino" to 0xFF5A0F1A,
        "Morado" to 0xFF4B2A5E,
        "Azul marino" to 0xFF1B263B,
        "Mezclilla" to 0xFF5B7C99,
        "Verde oliva" to 0xFF556B2F,
        "Gris" to 0xFF8E8E8E,
        "Naranja" to 0xFFFF5500,
        "Mostaza" to 0xFFC9A227
    )

    private const val FALLBACK = 0xFF8E8E8E

    /** Acepta un nombre de la lista (sin importar mayúsculas) o un hexadecimal #RRGGBB. */
    fun resolve(value: String): Long {
        val text = value.trim()
        if (text.startsWith("#") && text.length == 7) {
            text.substring(1).toLongOrNull(16)?.let { return 0xFF000000 or it }
        }
        return named.entries.firstOrNull { it.key.equals(text, ignoreCase = true) }?.value ?: FALLBACK
    }
}
