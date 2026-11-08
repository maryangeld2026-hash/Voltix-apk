package com.nexo.escritorio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Paleta del escritorio: oscuro, cristal translúcido y un color de acento. */
object Nexo {
    val accents = listOf(
        Color(0xFF7C9CFF), // azul
        Color(0xFF4FD1C5), // turquesa
        Color(0xFFB794F6), // violeta
        Color(0xFFF6AD55), // ámbar
        Color(0xFFF687B3), // rosa
        Color(0xFF68D391), // verde
    )
    val accentNames = listOf("Azul", "Turquesa", "Violeta", "Ámbar", "Rosa", "Verde")

    val text = Color(0xFFF3F5FA)
    val textDim = Color(0xFFA9B0C3)
    val textFaint = Color(0xFF6E7590)
    val onAccent = Color(0xFF0B0E17)
    val fieldBg = Color.White.copy(alpha = 0.06f)
    val hover = Color.White.copy(alpha = 0.09f)
    val danger = Color(0xFFFF7A88)
    val success = Color(0xFF68D391)
}

val LocalAccent = compositionLocalOf { Nexo.accents[0] }

fun accentColor(index: Int): Color = Nexo.accents[index.coerceIn(0, Nexo.accents.lastIndex)]

@Composable
fun NexoTheme(accent: Color, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = Nexo.onAccent,
        secondary = accent,
        onSecondary = Nexo.onAccent,
        background = Color(0xFF0B0E17),
        onBackground = Nexo.text,
        surface = Color(0xFF161A26),
        onSurface = Nexo.text,
        surfaceVariant = Color(0xFF232838),
        onSurfaceVariant = Nexo.textDim,
        outline = Color(0xFF3A4157),
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalAccent provides accent, content = content)
    }
}

/** Efecto «cristal»: fondo translúcido, borde de luz y sombra suave. */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(18.dp),
    elevation: Dp = 24.dp,
    strength: Float = 0.80f,
): Modifier = this
    .shadow(elevation, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(
                Color(0xFF242A3B).copy(alpha = strength),
                Color(0xFF11141D).copy(alpha = (strength + 0.08f).coerceAtMost(1f)),
            )
        )
    )
    .border(
        1.dp,
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f))),
        shape,
    )

/** Logo de Nexo: cuatro ventanas. También es el botón de inicio. */
@Composable
fun NexoLogo(size: Dp, accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val gap = this.size.width * 0.12f
        val s = (this.size.width - gap) / 2f
        val r = CornerRadius(s * 0.30f)
        val light = lerp(accent, Color.White, 0.40f)
        val tiles = listOf(
            Offset(0f, 0f) to accent,
            Offset(s + gap, 0f) to light,
            Offset(0f, s + gap) to light,
            Offset(s + gap, s + gap) to accent,
        )
        tiles.forEach { (o, c) -> drawRoundRect(color = c, topLeft = o, size = Size(s, s), cornerRadius = r) }
    }
}
