package com.nexo.escritorio.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexo.escritorio.DesktopActions
import com.nexo.escritorio.Jarvis
import kotlinx.coroutines.delay

/** La última respuesta de Jarvis a lo que le preguntaste desde el escritorio (se cierra sola en 45 s). */
@Composable
fun JarvisCard(modifier: Modifier, actions: DesktopActions) {
    val r by Jarvis.respuesta.collectAsState()
    val accent = LocalAccent.current
    LaunchedEffect(r?.t) {
        if (r != null) {
            delay(45_000)
            Jarvis.cerrarRespuesta()
        }
    }
    AnimatedVisibility(
        visible = r != null,
        modifier = modifier,
        enter = fadeIn(tween(160)) + slideInVertically(tween(240)) { it / 4 },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 5 },
    ) {
        val x = r ?: return@AnimatedVisibility
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .glass(RoundedCornerShape(20.dp), 28.dp, 0.9f)
                .blockPointer()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(accent, lerp(accent, Color.White, 0.4f)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Nexo.onAccent, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Jarvis", color = Nexo.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (x.pregunta.isNotBlank()) {
                        Text("«${x.pregunta}»", color = Nexo.textDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                CardButton(Icons.Rounded.Mic, "Seguir hablando") { Jarvis.cerrarRespuesta(); Jarvis.hablar(actions) }
                CardButton(Icons.Rounded.Close, "Cerrar") { Jarvis.cerrarRespuesta() }
            }
            Spacer(Modifier.size(10.dp))
            Box(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                Text(limpiarMd(x.texto), color = Nexo.text, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun CardButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Nexo.text, modifier = Modifier.size(18.dp))
    }
}

/** Quita el formato de Markdown y LaTeX más común (la tarjeta muestra texto plano). */
private fun limpiarMd(t: String): String =
    t.replace(Regex("(?m)^\\|\\s*-{3,}.*$\\n?"), "")                         // tablas: sin la línea de guiones
        .replace(Regex("(?m)^\\|(.+)\\|\\s*$")) { m -> m.groupValues[1].split("|").joinToString("  —  ") { it.trim() } }
        .replace("`", "")
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        .replace(Regex("(?m)^#{1,6}\\s*"), "")
        .replace(Regex("(?m)^[-*]\\s+"), "• ")
        .replace(Regex("\\$([^$]+)\\$"), "$1")
        .replace(Regex("<<[^>]*>>"), "")
        .trim()
