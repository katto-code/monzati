package com.monzatti.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.VisualTransformation
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
    Text(text, color = MonzattiTheme.colors.muted, fontSize = 12.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.Medium)
}

@Composable
fun MutedText(text: String) {
    Text(text, color = MonzattiTheme.colors.muted, fontSize = 13.sp, lineHeight = 19.sp)
}

@Composable
fun ErrorText(text: String?) {
    if (!text.isNullOrBlank()) {
        Text(text, color = MonzattiTheme.colors.error, fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium)
    }
}

/** Enlace para regresar; la flecha la pone el componente, el texto va sin «←». */
@Composable
fun BackLink(text: String, onClick: () -> Unit) {
    val c = MonzattiTheme.colors
    TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = c.muted)
    }
}

/** Ícono a la izquierda del texto de un botón. */
@Composable
private fun ButtonIcon(icon: ImageVector?) {
    if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    accent: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    val c = MonzattiTheme.colors
    val content = if (accent) c.onAccent else c.onPrimary
    Button(
        onClick = { if (!loading) onClick() },
        enabled = enabled,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(11.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (accent) c.accent else c.primary,
            contentColor = content,
            disabledContainerColor = c.line,
            disabledContentColor = c.muted
        )
    ) {
        AnimatedContent(targetState = loading, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "button") { busy ->
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), color = content, strokeWidth = 2.dp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ButtonIcon(icon)
                    Text(text, fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Medium)
                }
            }
        }
    }
}

/** Rectángulo con brillo animado, para mostrar contenido que está cargando. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, corner: Dp = 10.dp) {
    val c = MonzattiTheme.colors
    val transition = rememberInfiniteTransition(label = "skeleton")
    val shift by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "shift"
    )
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .drawBehind {
                val w = size.width
                drawRect(
                    Brush.linearGradient(
                        listOf(c.surfaceVariant, c.surface.copy(alpha = 0.9f), c.surfaceVariant),
                        start = Offset(w * shift - w, 0f),
                        end = Offset(w * shift, size.height)
                    )
                )
            }
    )
}

/** Cuadrícula de tarjetas vacías mientras carga un catálogo o closet. */
@Composable
fun SkeletonGrid(cards: Int = 4, cardHeight: Dp = 200.dp) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat((cards + 1) / 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Skeleton(Modifier.weight(1f).height(cardHeight), corner = 14.dp)
                Skeleton(Modifier.weight(1f).height(cardHeight), corner = 14.dp)
            }
        }
    }
}

/** Capa de carga a pantalla completa con un mensaje ("Procesando pago de prueba…"). */
@Composable
fun LoadingOverlay(visible: Boolean, message: String) {
    val c = MonzattiTheme.colors
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(c.background.copy(alpha = 0.88f))
                .pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Brand()
                CircularProgressIndicator(color = c.accent, trackColor = c.line, strokeWidth = 3.dp)
                Text(message, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** Selector − n + para cantidades. */
@Composable
fun QuantityStepper(quantity: Int, onChange: (Int) -> Unit, max: Int) {
    val c = MonzattiTheme.colors
    Row(
        Modifier.border(1.dp, c.line, CircleShape).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onChange(quantity - 1) }, enabled = quantity > 1) {
            Icon(Icons.Filled.Remove, contentDescription = "Quitar uno", tint = if (quantity > 1) c.ink else c.line)
        }
        AnimatedContent(targetState = quantity, label = "qty") {
            Text("$it", color = c.ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 4.dp))
        }
        IconButton(onClick = { onChange(quantity + 1) }, enabled = quantity < max) {
            Icon(Icons.Filled.Add, contentDescription = "Agregar uno", tint = if (quantity < max) c.ink else c.line)
        }
    }
}

/** Botón redondo con contador (carrito). */
@Composable
fun BadgeIconButton(icon: ImageVector, contentDescription: String, count: Int, onClick: () -> Unit) {
    val c = MonzattiTheme.colors
    Box(Modifier.size(48.dp).clip(CircleShape).background(c.surface).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = "$contentDescription, $count", tint = c.ink, modifier = Modifier.size(22.dp))
        AnimatedVisibility(
            visible = count > 0,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Box(Modifier.size(20.dp).background(c.accent, CircleShape), contentAlignment = Alignment.Center) {
                Text("$count", color = c.onAccent, fontSize = 12.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Mensaje breve que aparece y se oculta solo. */
@Composable
fun Toast(message: String?, actionLabel: String? = null, onAction: () -> Unit = {}, onDismiss: () -> Unit) {
    val c = MonzattiTheme.colors
    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(3500)
            onDismiss()
        }
    }
    AnimatedVisibility(visible = message != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Row(
            Modifier.fillMaxWidth().background(c.primary, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(message.orEmpty(), color = c.onPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            if (actionLabel != null) {
                TextButton(onClick = onAction) { Text(actionLabel, color = c.accent, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
fun SecondaryButton(text: String, icon: ImageVector? = null, onClick: () -> Unit) {
    val c = MonzattiTheme.colors
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(11.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.ink)
    ) {
        ButtonIcon(icon)
        Text(text, color = c.ink)
    }
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String? = null,
    password: Boolean = false,
    number: Boolean = false,
    email: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        placeholder = placeholder?.let { { Text(it, fontSize = 13.sp) } },
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (password) {
            {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (visible) "Ocultar contraseña" else "Mostrar contraseña"
                    )
                }
            }
        } else null,
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                password -> KeyboardType.Password
                number -> KeyboardType.Number
                email -> KeyboardType.Email
                else -> KeyboardType.Text
            },
            autoCorrectEnabled = !(password || email)
        )
    )
}

@Composable
fun InfoCard(text: String, icon: ImageVector? = null) {
    val c = MonzattiTheme.colors
    Card(colors = CardDefaults.cardColors(containerColor = c.surface), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = c.ink, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, fontSize = 13.sp, lineHeight = 20.sp, color = c.ink)
        }
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

/** Marco para la imagen de una prenda o producto (el emoji es solo el marcador cuando no hay foto). */
@Composable
fun ItemImage(emoji: String, height: Dp, emojiSize: Int = 38, imagePath: String? = null) {
    // Las tarjetas miden ≤ 200dp: 512 px basta y evita cargar fotos de 12 MP por cada celda.
    val bitmap = rememberImageFile(imagePath, maxPx = 512)
    Box(
        modifier = Modifier.fillMaxWidth().height(height).background(MonzattiTheme.colors.surfaceVariant, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(6.dp))
        } else {
            Text(emoji, fontSize = emojiSize.sp)
        }
    }
}

/** Carga (una vez) una imagen local en segundo plano, reducida para que su lado mayor no pase mucho de [maxPx]. */
@Composable
fun rememberImageFile(path: String?, maxPx: Int = 1024): ImageBitmap? {
    val state = produceState<ImageBitmap?>(initialValue = null, path, maxPx) {
        value = path?.let {
            withContext(Dispatchers.IO) { decodeSampled(it, maxPx)?.asImageBitmap() }
        }
    }
    return state.value
}

internal fun sampleSizeFor(width: Int, height: Int, maxPx: Int): Int {
    var sample = 1
    while (maxOf(width, height) / (sample * 2) >= maxPx) sample *= 2
    return sample
}

private fun decodeSampled(path: String, maxPx: Int): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0) return null
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxPx) }
    return BitmapFactory.decodeFile(path, options)
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
        label = { Text(text, fontSize = 13.sp) },
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
