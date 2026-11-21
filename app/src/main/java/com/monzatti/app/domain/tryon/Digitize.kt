package com.monzatti.app.domain.tryon

/** Sugerencias al digitalizar una prenda a partir de las etiquetas de ML Kit (en inglés). */
object GarmentSuggestions {

    private val rules = listOf(
        "Vestidos" to listOf("dress", "gown", "skirt"),
        "Chamarras" to listOf("jacket", "coat", "outerwear", "blazer", "hoodie", "sweater", "cardigan"),
        "Pantalones" to listOf("jeans", "pants", "trousers", "shorts", "denim"),
        "Zapatos" to listOf("shoe", "sneaker", "boot", "footwear", "sandal", "heel"),
        "Blusas" to listOf("shirt", "t-shirt", "blouse", "top", "sleeve", "polo")
    )

    /** labels: (texto, confianza). Devuelve la categoría de Monzatti más probable o null. */
    fun category(labels: List<Pair<String, Float>>): String? {
        var best: String? = null
        var bestScore = 0f
        for ((text, confidence) in labels) {
            val t = text.lowercase()
            for ((category, words) in rules) {
                if (words.any { t.contains(it) } && confidence > bestScore) {
                    best = category
                    bestScore = confidence
                }
            }
        }
        return best
    }

    /** Nombre sugerido: "Chamarra mezclilla", "Blusa rosa pastel"… */
    fun name(category: String, color: String): String {
        val noun = when (category) {
            "Vestidos" -> "Vestido"
            "Chamarras" -> "Chamarra"
            "Pantalones" -> "Pantalón"
            "Zapatos" -> "Zapatos"
            else -> "Blusa"
        }
        return if (color.isBlank()) noun else "$noun ${color.lowercase()}"
    }
}
