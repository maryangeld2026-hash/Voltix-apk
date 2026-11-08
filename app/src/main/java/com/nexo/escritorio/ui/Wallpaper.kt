package com.nexo.escritorio.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class WallTheme(val name: String, val base: List<Color>, val glows: List<Color>)

val WALLPAPERS = listOf(
    WallTheme("Aurora", listOf(Color(0xFF0A0F26), Color(0xFF141A3C)), listOf(Color(0xFF5B5BF7), Color(0xFF22D3EE), Color(0xFFA855F7))),
    WallTheme("Atardecer", listOf(Color(0xFF1A0B1E), Color(0xFF2A0F24)), listOf(Color(0xFFFF7A59), Color(0xFFFF3D7F), Color(0xFF7B2FF7))),
    WallTheme("Océano", listOf(Color(0xFF031722), Color(0xFF06283D)), listOf(Color(0xFF0EA5E9), Color(0xFF14B8A6), Color(0xFF1E40AF))),
    WallTheme("Bosque", listOf(Color(0xFF06130E), Color(0xFF0D1F17)), listOf(Color(0xFF10B981), Color(0xFF84CC16), Color(0xFF0F766E))),
    WallTheme("Grafito", listOf(Color(0xFF0D0E12), Color(0xFF17191F)), listOf(Color(0xFF475569), Color(0xFF64748B), Color(0xFF334155))),
    WallTheme("Lava", listOf(Color(0xFF140805), Color(0xFF1F0B06)), listOf(Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFFACC15))),
)

private val GLOW_POSITIONS = listOf(Offset(0.18f, 0.24f), Offset(0.84f, 0.30f), Offset(0.52f, 0.88f))

/**
 * Fondo de pantalla: degradados con luces suaves que se mueven muy despacio,
 * o la foto que eligió el usuario.
 */
@Composable
fun Wallpaper(index: Int, custom: ImageBitmap?, animated: Boolean, modifier: Modifier = Modifier) {
    if (index == -1 && custom != null) {
        Box(modifier) {
            Image(custom, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)))
        }
        return
    }
    val theme = WALLPAPERS[index.coerceIn(0, WALLPAPERS.lastIndex)]
    val phase: State<Float> = if (animated) {
        rememberInfiniteTransition(label = "wallpaper").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Reverse),
            label = "phase",
        )
    } else {
        remember { mutableFloatStateOf(0.35f) }
    }
    Canvas(modifier) {
        val p = phase.value
        drawRect(Brush.linearGradient(theme.base, start = Offset.Zero, end = Offset(size.width, size.height)))
        theme.glows.forEachIndexed { i, color ->
            val angle = (p * 2f * PI.toFloat()) + i * 2.1f
            val pos = GLOW_POSITIONS[i % GLOW_POSITIONS.size]
            val center = Offset(
                size.width * (pos.x + 0.07f * sin(angle)),
                size.height * (pos.y + 0.06f * cos(angle * 0.8f)),
            )
            val radius = size.minDimension * (0.62f + 0.10f * i)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(color.copy(alpha = 0.55f), color.copy(alpha = 0.18f), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
        // Viñeta suave para que los textos se lean bien
        drawRect(
            Brush.radialGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                center = center,
                radius = size.maxDimension * 0.75f,
            )
        )
    }
}
