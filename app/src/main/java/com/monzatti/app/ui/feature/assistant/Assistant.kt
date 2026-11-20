package com.monzatti.app.ui.feature.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monzatti.app.core.theme.MonzattiTheme
import com.monzatti.app.domain.model.ChatMessage
import com.monzatti.app.ui.components.AppTextField
import com.monzatti.app.ui.components.ChoiceRow
import com.monzatti.app.ui.components.FieldLabel
import com.monzatti.app.ui.components.PrimaryButton
import com.monzatti.app.ui.components.ScreenColumn
import com.monzatti.app.ui.components.Title
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 4. Asistente de IA (simulado). RN-03: cuando sea real, las sugerencias deben usar al menos 70 % prendas del
 * closet y como máximo 30 % prendas de marcas aliadas.
 */
@HiltViewModel
class AssistantViewModel @Inject constructor() : ViewModel() {

    private val answers = listOf(
        "Combina tu blusa de seda con el pantalón sastre y tus botines. Sofisticado, sin esfuerzo.",
        "Prueba el vestido midi con los botines de piel: elegante y cómodo.",
        "Para algo casual: pantalón sastre con la blusa de seda."
    )

    private val _messages = MutableStateFlow(
        listOf(ChatMessage(false, "Hola, conozco las prendas de tu closet. ¿Para qué ocasión te vistes hoy?"))
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    fun ask(text: String) {
        val question = text.trim()
        if (question.isEmpty()) return
        _messages.update { list ->
            val withQuestion = list + ChatMessage(true, question)
            withQuestion + ChatMessage(false, answers[(withQuestion.size / 2) % answers.size])
        }
    }
}

@Composable
fun AssistantScreen(onTryOn: () -> Unit, viewModel: AssistantViewModel = hiltViewModel()) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    var question by rememberSaveable { mutableStateOf("") }
    val c = MonzattiTheme.colors

    ScreenColumn {
        FieldLabel("MONZATTI INTELLIGENCE")
        Title("Tu estilo, elevado.")
        messages.forEach { message ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start
            ) {
                Surface(
                    color = if (message.fromUser) c.primary else c.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(0.86f)
                ) {
                    Text(
                        message.text,
                        color = if (message.fromUser) c.onPrimary else c.ink,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp
                    )
                }
            }
        }
        ChoiceRow(listOf("Cena esta noche", "Otra combinación", "Look casual"), selected = "") { viewModel.ask(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppTextField(question, { question = it }, placeholder = "Pregunta sobre tu estilo…", modifier = Modifier.weight(1f))
            Button(
                onClick = { viewModel.ask(question); question = "" },
                colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary)
            ) { Text("➤") }
        }
        PrimaryButton("Ver en mi avatar", onClick = onTryOn, accent = true)
    }
}
