package com.monzatti.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monzatti.app.core.theme.MonzattiTheme

@Composable
fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
fun Brand() {
    Text("MONZATTI", color = MonzattiTheme.colors.brand, fontWeight = FontWeight.Bold, letterSpacing = 3.sp, fontSize = 12.sp)
}

@Composable
fun Title(text: String) {
    Text(text, color = MonzattiTheme.colors.ink, fontSize = 25.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
fun FieldLabel(text: String) {
    Text(text, color = MonzattiTheme.colors.muted, fontSize = 10.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.Medium)
}

@Composable
fun MutedText(text: String) {
    Text(text, color = MonzattiTheme.colors.muted, fontSize = 12.sp, lineHeight = 18.sp)
}

@Composable
fun ErrorText(text: String?) {
    if (!text.isNullOrBlank()) {
        Text(text, color = Color(0xFFD64545), fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun BackLink(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) {
        Text(text, color = MonzattiTheme.colors.muted)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, accent: Boolean = false, enabled: Boolean = true) {
    val c = MonzattiTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(11.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (accent) c.accent else c.primary,
            contentColor = if (accent) c.onAccent else c.onPrimary,
            disabledContainerColor = c.line,
            disabledContentColor = c.muted
        )
    ) {
        Text(text, fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Medium)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp)) {
        Text(text, color = MonzattiTheme.colors.ink)
    }
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String? = null,
    password: Boolean = false,
    number: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        placeholder = placeholder?.let { { Text(it, fontSize = 12.sp) } },
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                password -> KeyboardType.Password
                number -> KeyboardType.Number
                else -> KeyboardType.Text
            }
        )
    )
}

@Composable
fun InfoCard(text: String) {
    val c = MonzattiTheme.colors
    Card(colors = CardDefaults.cardColors(containerColor = c.surface), shape = RoundedCornerShape(12.dp)) {
        Text(text, modifier = Modifier.fillMaxWidth().padding(14.dp), fontSize = 12.sp, lineHeight = 19.sp, color = c.ink)
    }
}

@Composable
fun SurfaceCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MonzattiTheme.colors.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), content = content)
    }
}

@Composable
fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    SurfaceCard(modifier) {
        FieldLabel(label)
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = MonzattiTheme.colors.ink)
    }
}

/** Marco para la imagen de una prenda o producto (aún emojis en el prototipo). */
@Composable
fun ItemImage(emoji: String, height: Dp, emojiSize: Int = 38) {
    Box(
        modifier = Modifier.fillMaxWidth().height(height).background(MonzattiTheme.colors.surfaceVariant, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) { Text(emoji, fontSize = emojiSize.sp) }
}

@Composable
fun ChoiceRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { option -> ChoiceChip(option, selected == option, onClick = { onSelect(option) }) }
    }
}

@Composable
fun MultiChoiceRow(options: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { option -> ChoiceChip(option, option in selected, onClick = { onToggle(option) }) }
    }
}

@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = MonzattiTheme.colors
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, fontSize = 11.sp) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = c.surface,
            labelColor = c.ink,
            selectedContainerColor = c.accent,
            selectedLabelColor = c.onAccent
        )
    )
}

/** Franja con los colores de una paleta. */
@Composable
fun SwatchRow(colors: List<Color>, modifier: Modifier = Modifier, height: Dp = 22.dp) {
    Row(modifier.fillMaxWidth().height(height)) {
        colors.forEach { Box(Modifier.weight(1f).fillMaxSize().background(it)) }
    }
}

@Composable
fun Dot(color: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).background(color, CircleShape))
}
