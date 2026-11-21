package com.monzatti.app.ui.feature.style

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monzatti.app.domain.model.Gender
import com.monzatti.app.domain.model.StylePalette
import com.monzatti.app.domain.model.UserProfile
import com.monzatti.app.domain.repository.AuthRepository
import com.monzatti.app.domain.repository.StyleRepository
import com.monzatti.app.domain.style.QuizQuestion
import com.monzatti.app.domain.style.StyleQuiz
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuizUiState(
    /** Null hasta saber el género (del perfil o preguntándolo al inicio del cuestionario). */
    val gender: Gender? = null,
    val questions: List<QuizQuestion> = emptyList(),
    val answers: List<StylePalette> = emptyList()
) {
    val currentIndex: Int get() = answers.size
    val current: QuizQuestion? get() = questions.getOrNull(currentIndex)
    val finished: Boolean get() = questions.isNotEmpty() && answers.size == questions.size
}

@HiltViewModel
class StyleViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val styles: StyleRepository
) : ViewModel() {

    val user: StateFlow<UserProfile?> = auth.currentUser

    private val _quiz = MutableStateFlow(QuizUiState())
    val quiz: StateFlow<QuizUiState> = _quiz.asStateFlow()

    init {
        auth.currentUser.value?.gender?.let(::chooseGender)
    }

    fun chooseGender(gender: Gender) {
        _quiz.value = QuizUiState(gender = gender, questions = StyleQuiz.questions(gender))
    }

    fun answer(style: StylePalette) {
        _quiz.update { if (it.finished) it else it.copy(answers = it.answers + style) }
    }

    fun back(): Boolean {
        val state = _quiz.value
        return when {
            state.answers.isNotEmpty() -> { _quiz.update { it.copy(answers = it.answers.dropLast(1)) }; true }
            else -> false
        }
    }

    fun result(): StylePalette = StyleQuiz.result(_quiz.value.answers)

    fun apply(style: StylePalette, onDone: () -> Unit) {
        viewModelScope.launch {
            styles.setStyle(style)
            onDone()
        }
    }
}
