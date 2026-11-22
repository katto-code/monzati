package com.monzatti.app.ui.feature.style

import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.graphics.vector.ImageVector

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monzatti.app.core.theme.MonzattiTheme
import com.monzatti.app.core.theme.colors
import com.monzatti.app.domain.model.Gender
import com.monzatti.app.domain.model.StylePalette
import com.monzatti.app.domain.model.UserRole
import com.monzatti.app.domain.style.QuizOption
import com.monzatti.app.domain.style.QuizQuestion
import com.monzatti.app.ui.components.BackLink
import com.monzatti.app.ui.components.Brand
import com.monzatti.app.ui.components.FieldLabel
import com.monzatti.app.ui.components.MutedText
import com.monzatti.app.ui.components.PrimaryButton
import com.monzatti.app.ui.components.ScreenColumn
import com.monzatti.app.ui.components.SecondaryButton
import com.monzatti.app.ui.components.SurfaceCard
import com.monzatti.app.ui.components.SwatchRow
import com.monzatti.app.ui.components.Title

/** "Tu app, a tu estilo": elegir directamente, hacer el cuestionario u omitir. */
@Composable
fun StyleChoiceScreen(
    onPick: () -> Unit,
    onQuiz: () -> Unit,
    onSkipped: (UserRole) -> Unit,
    viewModel: StyleViewModel = hiltViewModel()
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    ScreenColumn {
        Brand()
        Spacer(Modifier.height(12.dp))
        Title("Tu app, a tu estilo.")
        MutedText("Monzatti se viste de tus colores. Elige cómo quieres descubrirlos.")
        ChoiceCard(Icons.Outlined.Palette, "Elige el estilo de tu app", "Mira las cuatro paletas y quédate con la tuya.", onPick)
        ChoiceCard(Icons.Outlined.Quiz, "Descubre tu estilo", "Responde 5 preguntas con imágenes y te decimos cuál eres.", onQuiz)
        Spacer(Modifier.height(4.dp))
        SecondaryButton("Omitir por ahora (queda Monzatti)") {
            viewModel.apply(StylePalette.MONZATTI) { onSkipped(user?.role ?: UserRole.USUARIO) }
        }
    }
}

@Composable
private fun ChoiceCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val c = MonzattiTheme.colors
    SurfaceCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(44.dp).height(44.dp).background(c.accent, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, contentDescription = null, tint = c.onAccent) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = c.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                MutedText(subtitle)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.muted)
        }
    }
}

/** Selector directo: cada tarjeta se pinta con su propia paleta. */
@Composable
fun StylePickerScreen(onBack: () -> Unit, onSelect: (StylePalette) -> Unit, viewModel: StyleViewModel = hiltViewModel()) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    ScreenColumn {
        BackLink("Volver", onBack)
        Title("Elige tu estilo")
        MutedText("Toca una paleta para verla en tu app.")
        StylePalette.selectable.forEach { palette ->
            StylePreviewCard(palette, user?.gender, Modifier.clickable { onSelect(palette) })
        }
    }
}

/** Vista previa de la app pintada con una paleta. */
@Composable
fun StylePreviewCard(palette: StylePalette, gender: Gender?, modifier: Modifier = Modifier) {
    MonzattiTheme(palette, animate = false) {
        val c = MonzattiTheme.colors
        Column(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(c.background)
                .border(1.dp, c.line, RoundedCornerShape(16.dp))
        ) {
            SwatchRow(palette.colors().swatches, height = 14.dp)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Brand()
                Text(palette.displayName(gender), color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                MutedText(palette.tagline)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniButton("Mi closet", c.primary, c.onPrimary, Modifier.weight(1f))
                    MiniButton("Probar", c.accent, c.onAccent, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("👚", "👖", "👗").forEach {
                        Box(
                            Modifier.weight(1f).height(54.dp).background(c.surfaceVariant, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) { Text(it, fontSize = 24.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniButton(text: String, container: Color, content: Color, modifier: Modifier) {
    Box(
        modifier.height(36.dp).background(container, RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center
    ) { Text(text, color = content, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun StyleQuizScreen(onExit: () -> Unit, onFinished: (StylePalette) -> Unit, viewModel: StyleViewModel = hiltViewModel()) {
    val state by viewModel.quiz.collectAsStateWithLifecycle()
    val goBack = { if (!viewModel.back()) onExit() }
    BackHandler(onBack = goBack)

    ScreenColumn {
        BackLink("Volver", goBack)
        val gender = state.gender
        val question = state.current
        when {
            gender == null -> GenderStep(viewModel::chooseGender)
            question != null -> {
                FieldLabel("PREGUNTA ${question.number} DE ${state.questions.size}")
                LinearProgressIndicator(
                    progress = { state.currentIndex.toFloat() / state.questions.size },
                    modifier = Modifier.fillMaxWidth(),
                    color = MonzattiTheme.colors.accent,
                    trackColor = MonzattiTheme.colors.line
                )
                Title(question.title)
                QuestionGrid(question, gender) { style ->
                    viewModel.answer(style)
                    if (viewModel.quiz.value.finished) onFinished(viewModel.result())
                }
            }
            state.finished -> {
                Title("¡Listo!")
                MutedText("Ya respondiste todo el cuestionario.")
                PrimaryButton("Ver mi resultado", onClick = { onFinished(viewModel.result()) }, accent = true)
            }
        }
    }
}

@Composable
private fun GenderStep(onChoose: (Gender) -> Unit) {
    Title("Antes de empezar")
    MutedText("¿Cómo te identificas? Así te mostramos imágenes que van contigo.")
    Gender.entries.forEach { gender ->
        PrimaryButton(gender.label, onClick = { onChoose(gender) }, accent = gender == Gender.FEMENINO)
    }
}

@Composable
private fun QuestionGrid(question: QuizQuestion, gender: Gender, onAnswer: (StylePalette) -> Unit) {
    val options = remember(question.number, gender) { question.options.shuffled() }
    options.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            pair.forEach { option ->
                Column(
                    Modifier.weight(1f).clickable { onAnswer(option.style) },
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (question.showsPalette) PaletteTile(option.style) else QuizImage(option)
                    Text(
                        option.label,
                        color = MonzattiTheme.colors.ink,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/** Foto del cuestionario; si aún no existe en res/drawable-nodpi, una tarjeta con el color del estilo. */
@SuppressLint("DiscouragedApi")
@Composable
private fun QuizImage(option: QuizOption) {
    val context = LocalContext.current
    val resId = remember(option.imageName) {
        option.imageName?.let { context.resources.getIdentifier(it, "drawable", context.packageName) } ?: 0
    }
    val shape = RoundedCornerShape(14.dp)
    if (resId != 0) {
        Image(
            painter = painterResource(resId),
            contentDescription = option.label,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(shape)
        )
    } else {
        val palette = option.style.colors()
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(shape).background(palette.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                option.label,
                color = palette.ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
private fun PaletteTile(style: StylePalette) {
    Column(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp))) {
        style.colors().swatches.forEach { Box(Modifier.weight(1f).fillMaxSize().background(it)) }
    }
}

@Composable
fun StyleResultScreen(
    styleKey: String,
    onApplied: (UserRole) -> Unit,
    onChooseOther: () -> Unit,
    viewModel: StyleViewModel = hiltViewModel()
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val palette = StylePalette.fromKey(styleKey)
    ScreenColumn {
        FieldLabel("TU RESULTADO")
        Title("Tu estilo es ${palette.displayName(user?.gender)}.")
        MutedText("Así se verá tu Monzatti. Puedes cambiarlo cuando quieras.")
        StylePreviewCard(palette, user?.gender)
        PrimaryButton("Aplicar estilo", onClick = {
            viewModel.apply(palette) { onApplied(user?.role ?: UserRole.USUARIO) }
        }, accent = true)
        SecondaryButton("Elegir otro", onClick = onChooseOther)
    }
}
