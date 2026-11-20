package com.monzatti.app.domain.model

enum class Gender(val key: String, val label: String) {
    FEMENINO("femenino", "Femenino"),
    MASCULINO("masculino", "Masculino");

    companion object {
        fun fromKey(key: String?): Gender? = entries.firstOrNull { it.key == key }
    }
}

enum class UserRole(val key: String, val label: String) {
    USUARIO("usuario", "Usuario final"),
    SOCIO("socio", "Socio comercial"),
    ADMIN("admin", "Administrador");

    companion object {
        val selectable = listOf(USUARIO, SOCIO)
        fun fromKey(key: String?): UserRole = entries.firstOrNull { it.key == key } ?: USUARIO
    }
}

/** Estilo visual de la app. MONZATTI es el predeterminado; los otros cuatro salen del cuestionario. */
enum class StylePalette(val key: String) {
    MONZATTI("monzatti"),
    COQUETTE("coquette"),
    GOTICA("gotica"),
    STREETWEAR("streetwear"),
    OLD_MONEY("old_money");

    fun displayName(gender: Gender?): String = when (this) {
        MONZATTI -> "Monzatti"
        COQUETTE -> if (gender == Gender.MASCULINO) "Romántico" else "Coquette"
        GOTICA -> if (gender == Gender.MASCULINO) "Gótico" else "Gótica"
        STREETWEAR -> "Streetwear"
        OLD_MONEY -> "Old Money"
    }

    val tagline: String
        get() = when (this) {
            MONZATTI -> "Negro, crema y dorado. La esencia Monzatti."
            COQUETTE -> "Paleta romántica y pastel: tonos suaves y delicados."
            GOTICA -> "Paleta oscura y dramática: negro, tonos joya y plata."
            STREETWEAR -> "Paleta urbana y de alto contraste: neutros con color vibrante."
            OLD_MONEY -> "Paleta atemporal y sobria: neutros cálidos y tonos profundos."
        }

    companion object {
        /** Los cuatro estilos que se pueden elegir o resultar del cuestionario, en orden de desempate. */
        val selectable = listOf(COQUETTE, GOTICA, STREETWEAR, OLD_MONEY)
        fun fromKey(key: String?): StylePalette = entries.firstOrNull { it.key == key } ?: MONZATTI
        fun fromKeyOrNull(key: String?): StylePalette? = entries.firstOrNull { it.key == key }
    }
}

data class UserProfile(
    val id: String,
    val fullName: String,
    val email: String,
    val role: UserRole,
    val gender: Gender?,
    val style: StylePalette?
) {
    val firstName: String get() = fullName.trim().substringBefore(' ').ifBlank { "Hola" }
}

data class Garment(
    val id: String,
    val name: String,
    val emoji: String,
    val category: String,
    val size: String,
    val detail: String,
    val favorite: Boolean = false,
    /** Nombre del color ("Negro", "Rosa palo"…) o hexadecimal ("#F4C2C2"). */
    val color: String = "",
    /** Foto digitalizada con fondo recortado (PNG local), si existe. */
    val imagePath: String? = null
)

data class Product(
    val id: String,
    val brand: String,
    val name: String,
    val emoji: String,
    val category: String,
    val price: Int,
    val stockBySize: Map<String, Int>,
    val color: String = ""
) {
    val totalStock: Int get() = stockBySize.values.sum()
    val isLowStock: Boolean get() = totalStock in 1..LOW_STOCK_LIMIT

    /** RF-01: no se puede comprar una talla con existencias en 0. */
    fun isAvailable(size: String): Boolean = (stockBySize[size] ?: 0) > 0

    companion object {
        const val LOW_STOCK_LIMIT = 5
    }
}

data class ChatMessage(val fromUser: Boolean, val text: String)

data class SignUpRequest(
    val fullName: String,
    val email: String,
    val password: String,
    val role: UserRole,
    val gender: Gender
)

data class NewProduct(
    val name: String,
    val price: Int,
    val sizes: List<String>,
    val stockPerSize: Int
)

class OutOfStockException(size: String) : IllegalStateException("La talla $size está agotada.")

val GarmentSizes = listOf("XS", "S", "M", "L", "XL")
val GarmentCategories = listOf("Blusas", "Chamarras", "Vestidos", "Pantalones", "Zapatos")

object PasswordRules {
    const val DESCRIPTION = "Mínimo 8 caracteres, una mayúscula y un número."

    fun isValid(password: String): Boolean =
        password.length >= 8 && password.any { it.isUpperCase() } && password.any { it.isDigit() }
}
