package com.voltix.guardian.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Neon {
    val bg0 = Color(0xFF060A16)
    val bg1 = Color(0xFF0B1226)
    val cyan = Color(0xFF00E5FF)
    val violet = Color(0xFF8B5CF6)
    val lime = Color(0xFFB6FF3B)
    val amber = Color(0xFFFFB020)
    val red = Color(0xFFFF3B5C)
    val text = Color(0xFFE8EEFF)
    val dim = Color(0xFF8E9BC0)
}

@Composable
fun VoltixTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Neon.cyan,
            secondary = Neon.violet,
            background = Neon.bg0,
            surface = Neon.bg1,
            onSurface = Neon.text,
            onBackground = Neon.text
        ),
        content = content
    )
}

@Composable
fun ScreenBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Neon.bg1, Neon.bg0)))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.width * 0.85f
            val c1 = Offset(size.width * 0.92f, size.height * 0.06f)
            drawCircle(
                brush = Brush.radialGradient(listOf(Neon.violet.copy(alpha = 0.28f), Color.Transparent), center = c1, radius = r),
                radius = r,
                center = c1
            )
            val c2 = Offset(size.width * 0.05f, size.height * 0.95f)
            drawCircle(
                brush = Brush.radialGradient(listOf(Neon.cyan.copy(alpha = 0.20f), Color.Transparent), center = c2, radius = r),
                radius = r,
                center = c2
            )
        }
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accent: Color = Neon.cyan,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    var m = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(Color.White.copy(alpha = 0.05f))
        .border(
            1.dp,
            Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), Color.White.copy(alpha = 0.04f))),
            shape
        )
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(16.dp), content = content)
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.55f), shape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = Neon.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, color = Neon.dim, fontSize = 13.sp)
    }
}

@Composable
fun StatTile(label: String, value: String, sub: String, accent: Color, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier, accent = accent) {
        Text(label.uppercase(), color = Neon.dim, fontSize = 11.sp, letterSpacing = 1.5.sp)
        Text(value, color = accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(sub, color = Neon.dim, fontSize = 12.sp)
    }
}

@Composable
fun BatteryGauge(level: Int, charging: Boolean, size: Dp = 250.dp) {
    val animated by animateFloatAsState(
        targetValue = level.coerceIn(0, 100) / 100f,
        animationSpec = tween(900),
        label = "level"
    )
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseAlpha"
    )
    val color = when {
        charging -> Neon.lime
        level <= 15 -> Neon.red
        level <= 30 -> Neon.amber
        else -> Neon.cyan
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(16.dp)) {
            val stroke = 22.dp.toPx()
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            drawArc(
                color = Color.White.copy(alpha = 0.08f),
                startAngle = 135f, sweepAngle = 270f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            val glow = if (charging) 0.12f + 0.2f * pulse else 0.16f
            drawArc(
                color = color.copy(alpha = glow),
                startAngle = 135f, sweepAngle = 270f * animated, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(stroke * 1.7f, cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = 135f, sweepAngle = 270f * animated, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$level%", color = Neon.text, fontSize = 58.sp, fontWeight = FontWeight.Bold)
            Text(
                if (charging) "⚡ CARGANDO" else "EN USO",
                color = color, fontSize = 13.sp, letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun LineChart(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier.fillMaxWidth().height(110.dp),
    minV: Float? = null,
    maxV: Float? = null
) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val step = (values.size / 120) + 1
        val pts = values.filterIndexed { i, _ -> i % step == 0 || i == values.size - 1 }
        if (pts.size < 2) return@Canvas
        val lo = minV ?: pts.min()
        val hi = maxV ?: pts.max()
        val range = (hi - lo).coerceAtLeast(1f)
        val path = Path()
        pts.forEachIndexed { i, v ->
            val x = size.width * i / (pts.size - 1)
            val y = size.height - ((v - lo) / range).coerceIn(0f, 1f) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val fill = Path()
        fill.addPath(path)
        fill.lineTo(size.width, size.height)
        fill.lineTo(0f, size.height)
        fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), Color.Transparent)))
        drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
    }
}
