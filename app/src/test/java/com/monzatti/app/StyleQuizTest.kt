package com.monzatti.app

import com.monzatti.app.domain.model.Gender
import com.monzatti.app.domain.model.StylePalette
import com.monzatti.app.domain.model.StylePalette.COQUETTE
import com.monzatti.app.domain.model.StylePalette.GOTICA
import com.monzatti.app.domain.model.StylePalette.OLD_MONEY
import com.monzatti.app.domain.model.StylePalette.STREETWEAR
import com.monzatti.app.domain.style.StyleQuiz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StyleQuizTest {

    @Test
    fun eachStyleWinsWhenChosenEverywhere() {
        StylePalette.selectable.forEach { style ->
            assertEquals(style, StyleQuiz.result(List(5) { style }))
        }
    }

    @Test
    fun majorityWins() {
        assertEquals(GOTICA, StyleQuiz.result(listOf(COQUETTE, GOTICA, GOTICA, STREETWEAR, GOTICA)))
    }

    @Test
    fun tieIsBrokenByFirstAnswer() {
        assertEquals(OLD_MONEY, StyleQuiz.result(listOf(OLD_MONEY, COQUETTE, OLD_MONEY, COQUETTE, STREETWEAR)))
    }

    @Test
    fun tieWithoutFirstAnswerUsesFixedOrder() {
        // Streetwear (pregunta 1) tiene 1 punto; Gótica y Old Money empatan con 2 -> gana Gótica por orden.
        assertEquals(GOTICA, StyleQuiz.result(listOf(STREETWEAR, OLD_MONEY, GOTICA, OLD_MONEY, GOTICA)))
    }

    @Test
    fun everyQuestionHasOneOptionPerStyle() {
        Gender.entries.forEach { gender ->
            val questions = StyleQuiz.questions(gender)
            assertEquals(5, questions.size)
            questions.forEach { q ->
                assertEquals(StylePalette.selectable.toSet(), q.options.map { it.style }.toSet())
            }
        }
    }

    @Test
    fun imageNamesFollowConvention() {
        val fem = StyleQuiz.questions(Gender.FEMENINO)
        val masc = StyleQuiz.questions(Gender.MASCULINO)
        assertEquals("quiz_p1_f_oldmoney", fem[0].options.first { it.style == OLD_MONEY }.imageName)
        assertEquals("quiz_p1_m_coquette", masc[0].options.first { it.style == COQUETTE }.imageName)
        assertEquals("quiz_p2_x_gotica", masc[1].options.first { it.style == GOTICA }.imageName)
        assertTrue(fem[4].showsPalette)
        fem[4].options.forEach { assertNull(it.imageName) }
        // 28 fotos en total: p1, p3, p4 por género (24) + p2 compartida (4).
        val names = (fem + masc).flatMap { q -> q.options.mapNotNull { it.imageName } }.toSet()
        assertEquals(28, names.size)
    }
}
