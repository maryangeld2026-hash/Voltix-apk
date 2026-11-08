package com.nexo.escritorio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nexo.escritorio.DesktopActivity
import com.nexo.escritorio.NexoAccessibility
import com.nexo.escritorio.NexoPrefs
import com.nexo.escritorio.TouchpadView

@Composable
fun TouchpadScreen(onEnableAssistant: () -> Unit, onClose: () -> Unit) {
    val connected by NexoAccessibility.connected.collectAsState()
    val accent = LocalAccent.current
    var status by remember { mutableStateOf("") }
    var pointerOn by remember { mutableStateOf(false) }

    // Al abrir (o al activar el asistente) se muestra el puntero en el monitor.
    LaunchedEffect(connected) {
        val s = NexoAccessibility.get()
        if (connected && s != null) {
            val d = s.targetDisplay()
            pointerOn = d != null && s.startPointer(d)
            status = if (pointerOn) "Controlando: ${d?.name ?: "pantalla"}" else "No se pudo mostrar el puntero en esta pantalla"
        }
    }
    DisposableEffect(Unit) { onDispose { NexoAccessibility.get()?.stopPointer() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E17))
            .safeDrawingPadding()
            .padding(14.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Mouse, null, tint = accent, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Touchpad", color = Nexo.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    if (status.isNotEmpty()) Text(status, color = Nexo.textDim, fontSize = 12.sp)
                }
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, "Cerrar", tint = Nexo.text) }
            }
            Spacer(Modifier.height(12.dp))

            if (!connected || !NexoAccessibility.pointerSupported) {
                NeedAssistant(connected, onEnableAssistant)
                return@Column
            }

            // Superficie táctil
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(24.dp))
            ) {
                AndroidView(
                    factory = { ctx -> TouchpadView(ctx) { NexoAccessibility.get() } },
                    modifier = Modifier.fillMaxSize(),
                )
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Rounded.TouchApp, null, tint = Nexo.textFaint, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Un dedo: mover · Tocar: clic · Doble toque: doble clic\nMantener o tocar con dos dedos: menú · Dos dedos: desplazar",
                        color = Nexo.textFaint,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // Botones rápidos
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PadButton(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") { NexoAccessibility.back() }
                PadButton(Icons.Rounded.DesktopWindows, "Inicio") {
                    DesktopActivity.current?.get()?.onGlobalWinKey()
                }
                PadButton(Icons.Rounded.Mouse, "Clic der.") { NexoAccessibility.get()?.longPress() }
            }
            Spacer(Modifier.height(10.dp))

            // Escribir en el monitor
            var text by remember { mutableStateOf("") }
            val displayId = NexoAccessibility.get()?.pointerDisplay ?: -1
            val send = {
                val s = NexoAccessibility.get()
                if (text.isNotEmpty()) {
                    val ok = s?.typeText(displayId, text) == true
                    status = if (ok) "Texto enviado" else "Haz clic primero en un campo de texto del monitor"
                    if (ok) text = ""
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Nexo.text, fontSize = 15.sp),
                    cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Nexo.fieldBg)
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            if (text.isEmpty()) Text("Escribe aquí para el monitor…", color = Nexo.textFaint, fontSize = 15.sp)
                            inner()
                        }
                    },
                )
                Spacer(Modifier.width(6.dp))
                SmallPadButton(Icons.AutoMirrored.Rounded.Send, "Enviar") { send() }
                SmallPadButton(Icons.Rounded.Backspace, "Borrar") {
                    NexoAccessibility.get()?.backspace(displayId)
                }
                SmallPadButton(Icons.AutoMirrored.Rounded.KeyboardReturn, "Intro") {
                    NexoAccessibility.get()?.enter(displayId)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Velocidad", color = Nexo.textDim, fontSize = 12.sp)
                Spacer(Modifier.width(10.dp))
                Slider(
                    value = NexoPrefs.pointerSpeed,
                    onValueChange = { NexoPrefs.changePointerSpeed(it) },
                    valueRange = 0.6f..3.2f,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NeedAssistant(connected: Boolean, onEnable: () -> Unit) {
    val accent = LocalAccent.current
    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(20.dp), 8.dp, 0.8f)
            .padding(18.dp)
    ) {
        if (!NexoAccessibility.pointerSupported) {
            Text("El touchpad necesita Android 11 o superior.", color = Nexo.text, fontSize = 15.sp)
            return@Column
        }
        Text("Activa el asistente de Nexo", color = Nexo.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "El touchpad usa el servicio de accesibilidad de Nexo para mover el puntero y tocar en el monitor. " +
                "En Ajustes › Accesibilidad busca «Nexo Escritorio» y actívalo.",
            color = Nexo.textDim,
            fontSize = 13.sp,
        )
        if (!connected) {
            Spacer(Modifier.height(14.dp))
            Text(
                "Abrir accesibilidad",
                color = Nexo.onAccent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent)
                    .clickable(onClick = onEnable)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun RowScope.PadButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = Nexo.text, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = Nexo.textDim, fontSize = 12.sp)
    }
}

@Composable
private fun SmallPadButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 4.dp)
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Nexo.text, modifier = Modifier.size(20.dp))
    }
}
