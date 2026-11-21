package com.monzatti.app.domain.style

import com.monzatti.app.domain.model.Gender
import com.monzatti.app.domain.model.StylePalette

data class QuizOption(
    val style: StylePalette,
    val label: String,
    /** Nombre del drawable (sin extensión) en res/drawable-nodpi; null si la app dibuja la opción. */
    val imageName: String?
)

data class QuizQuestion(
    val number: Int,
    val title: String,
    val options: List<QuizOption>,
    /** La pregunta 5 no usa fotos: la app dibuja la paleta de cada estilo. */
    val showsPalette: Boolean = false
)

/**
 * Cuestionario de estilo: 5 preguntas, cada opción suma un punto a un estilo.
 * Las imágenes siguen el formato quiz_p<n>_<f|m|x>_<estilo>, donde x = compartida por ambos géneros.
 */
object StyleQuiz {

    private val tieOrder = StylePalette.selectable

    fun questions(gender: Gender): List<QuizQuestion> {
        val g = if (gender == Gender.MASCULINO) "m" else "f"
        val fem = gender == Gender.FEMENINO
        return listOf(
            question(
                1, "¿Con qué look te identificas más?", g,
                if (fem) "Vestido pastel con moño" else "Camisa holgada crema y pantalón claro",
                if (fem) "Vestido negro con encaje y botas" else "Abrigo largo negro y botas",
                "Hoodie oversize, cargo y tenis",
                if (fem) "Blazer camel, pantalón sastre y mocasines" else "Suéter de punto, chinos y mocasines"
            ),
            question(
                2, "Tu plan ideal de fin de semana", "x",
                "Picnic y café",
                "Concierto o museo de noche",
                "Skate con amigos",
                "Brunch en un club"
            ),
            question(
                3, "¿Qué zapatos usarías hoy?", g,
                if (fem) "Bailarinas con moño" else "Tenis blancos limpios",
                if (fem) "Botas de plataforma" else "Botas militares negras",
                if (fem) "Tenis chunky" else "Tenis chunky o de bota",
                if (fem) "Mocasines" else "Mocasines de piel"
            ),
            question(
                4, "Elige un accesorio", g,
                if (fem) "Moño y perlas" else "Collar de perlas",
                if (fem) "Cadenas y anillos plateados" else "Cadenas plateadas",
                "Gorra",
                if (fem) "Reloj clásico y collar dorado" else "Reloj clásico"
            ),
            QuizQuestion(
                number = 5,
                title = "¿Qué colores dominan tu closet?",
                options = listOf(
                    QuizOption(StylePalette.COQUETTE, "Pasteles", null),
                    QuizOption(StylePalette.GOTICA, "Negro y tonos oscuros", null),
                    QuizOption(StylePalette.STREETWEAR, "Neutros con un color fuerte", null),
                    QuizOption(StylePalette.OLD_MONEY, "Beige, marino y camel", null)
                ),
                showsPalette = true
            )
        )
    }

    /**
     * Gana el estilo con más puntos. En empate gana el elegido en la pregunta 1 si está entre los empatados;
     * si no, el primero de los empatados en el orden Coquette, Gótica, Streetwear, Old Money.
     */
    fun result(answers: List<StylePalette>): StylePalette {
        require(answers.isNotEmpty()) { "El cuestionario necesita al menos una respuesta." }
        val counts = answers.groupingBy { it }.eachCount()
        val max = counts.values.max()
        val tied = tieOrder.filter { counts[it] == max }
        val first = answers.first()
        return if (first in tied) first else tied.first()
    }

    fun imageName(question: Int, genderCode: String, style: StylePalette): String =
        "quiz_p${question}_${genderCode}_${styleCode(style)}"

    private fun styleCode(style: StylePalette): String = when (style) {
        StylePalette.OLD_MONEY -> "oldmoney"
        else -> style.key
    }

    private fun question(
        number: Int,
        title: String,
        genderCode: String,
        coquette: String,
        gotica: String,
        streetwear: String,
        oldMoney: String
    ) = QuizQuestion(
        number = number,
        title = title,
        options = listOf(
            StylePalette.COQUETTE to coquette,
            StylePalette.GOTICA to gotica,
            StylePalette.STREETWEAR to streetwear,
            StylePalette.OLD_MONEY to oldMoney
        ).map { (style, label) -> QuizOption(style, label, imageName(number, genderCode, style)) }
    )
}
