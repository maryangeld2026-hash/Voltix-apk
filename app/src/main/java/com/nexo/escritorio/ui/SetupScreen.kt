package com.nexo.escritorio.ui

import android.view.Display
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexo.escritorio.Displays
import com.nexo.escritorio.MainActivity
import com.nexo.escritorio.NexoPrefs

data class SetupStatus(
    val developerOptions: Boolean?,
    val freeform: Boolean?,
    val forceDesktop: Boolean?,
    val resizable: Boolean?,
    val notifications: Boolean,
    val canWriteSettings: Boolean,
)

private const val GRANT_COMMAND =
    "adb shell pm grant com.nexo.escritorio android.permission.WRITE_SECURE_SETTINGS"

private const val MANUAL_COMMANDS = """adb shell settings put global enable_freeform_support 1
adb shell settings put global force_resizable_activities 1
adb shell settings put global force_desktop_mode_on_external_displays 1
adb reboot"""

/** Pantalla principal en el teléfono. */
@Composable
fun SetupScreen(tick: Int, display: Display?, host: MainActivity) {
    val status = remember(tick, display) { host.readStatus() }
    val accent = LocalAccent.current

    Box(Modifier.fillMaxSize()) {
        Wallpaper(0, null, animated = true, modifier = Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 620.dp).fillMaxWidth()) {
                // Encabezado
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NexoLogo(40.dp, accent)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Nexo Escritorio", color = Nexo.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text("Tu teléfono, convertido en un computador", color = Nexo.textDim, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(22.dp))

                HeroCard(display, host)
                Spacer(Modifier.height(16.dp))

                Card {
                    Text("Preparación (solo una vez)", color = Nexo.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Android ya sabe mostrar ventanas en un monitor; estos ajustes lo activan. No hace falta root.",
                        color = Nexo.textDim,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    Step(
                        1, "Activa las opciones de desarrollador",
                        "Ajustes › Acerca del teléfono › toca 7 veces «Número de compilación».",
                        status.developerOptions, "Abrir ajustes", host::openAboutPhone,
                    )
                    Step(
                        2, "Activa «Ventanas de formato libre»",
                        "En Opciones de desarrollador busca «Habilitar ventanas de formato libre» (Enable freeform windows). Así las apps se abren en ventanas que puedes mover y cambiar de tamaño.",
                        status.freeform, "Opciones de desarrollador", host::openDeveloperOptions,
                    )
                    Step(
                        3, "Activa «Forzar modo de escritorio»",
                        "También en Opciones de desarrollador (Force desktop mode). El monitor se comporta como un escritorio.",
                        status.forceDesktop, "Opciones de desarrollador", host::openDeveloperOptions,
                    )
                    Step(
                        4, "Recomendado: «Forzar actividades redimensionables»",
                        "Hace que casi todas las apps acepten abrirse en ventana (Force activities to be resizable).",
                        status.resizable, "Opciones de desarrollador", host::openDeveloperOptions,
                    )
                    Step(
                        5, "Reinicia el teléfono",
                        "Los pasos 2 a 4 se aplican después de reiniciar.",
                        null, null, null,
                    )
                    Step(
                        6, "Opcional: notificaciones en el escritorio",
                        "Para verlas y abrirlas desde el centro de control del escritorio.",
                        status.notifications, "Permitir", host::openNotificationAccess,
                    )
                }
                Spacer(Modifier.height(16.dp))

                Card {
                    Text("¿Tienes un computador? Actívalo todo con un clic", color = Nexo.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Conecta el teléfono al PC con la depuración USB activada y ejecuta este comando una sola vez. Después vuelve aquí y pulsa «Activar todo».",
                        color = Nexo.textDim,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    CodeBlock(GRANT_COMMAND) { host.copy(GRANT_COMMAND) }
                    Spacer(Modifier.height(12.dp))
                    if (status.canWriteSettings) {
                        PrimaryButton("Activar todo automáticamente", Modifier.fillMaxWidth()) { host.applyAllSettings() }
                    } else {
                        Text(
                            "Sin PC también funciona: usa la «Depuración inalámbrica» con una app como LADB o Shizuku para ejecutar el comando desde el propio teléfono.",
                            color = Nexo.textFaint,
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("O todo por ADB, sin usar la app:", color = Nexo.textDim, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    CodeBlock(MANUAL_COMMANDS) { host.copy(MANUAL_COMMANDS) }
                }
                Spacer(Modifier.height(16.dp))

                Card {
                    Text("Consejos", color = Nexo.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Tip(Icons.Rounded.Usb, "Si el monitor no muestra nada al conectar el cable, tu teléfono no tiene salida de video por USB‑C (DisplayPort Alt Mode). Prueba con otro adaptador o con transmisión inalámbrica.")
                    Tip(Icons.Rounded.Keyboard, "Pulsa la tecla Windows para abrir el inicio, o simplemente empieza a escribir para buscar una app.")
                    Tip(Icons.Rounded.Mouse, "Clic derecho en cualquier icono para abrirlo maximizado, a la izquierda, a la derecha o anclarlo a la barra.")
                    Tip(Icons.Rounded.DesktopWindows, "Añade el mosaico «Escritorio» a los ajustes rápidos para abrir el escritorio con un toque.")
                    Tip(Icons.Rounded.PhoneAndroid, "En Pixel y Samsung con escritorio propio, Nexo se abre a pantalla completa por encima de él.")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun HeroCard(display: Display?, host: MainActivity) {
    val accent = LocalAccent.current
    Card {
        if (display != null) {
            val size = remember(display) { Displays.sizeOf(display) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.DesktopWindows, null, tint = accent, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Monitor conectado", color = Nexo.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("${display.name} · ${size.x} × ${size.y}", color = Nexo.textDim, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Abrir escritorio en el monitor", Modifier.fillMaxWidth()) { host.openDesktopOnMonitor() }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PhoneAndroid, null, tint = Nexo.text, modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Rounded.ChevronRight, null, tint = Nexo.textFaint)
                Icon(Icons.Rounded.Usb, null, tint = accent, modifier = Modifier.size(26.dp))
                Icon(Icons.Rounded.ChevronRight, null, tint = Nexo.textFaint)
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Rounded.DesktopWindows, null, tint = Nexo.text, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text("Conecta un monitor", color = Nexo.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Usa un cable o adaptador USB‑C a HDMI. Conecta teclado y ratón por Bluetooth o a un hub USB. El escritorio se abrirá solo.",
                color = Nexo.textDim,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(14.dp))
            GhostButton("Ver una vista previa en este teléfono", Modifier.fillMaxWidth()) { host.previewHere() }
        }
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            "Abrir automáticamente al conectar",
            "Funciona mientras esta pantalla está abierta",
            NexoPrefs.autoOpen,
        ) { NexoPrefs.changeAutoOpen(it) }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(22.dp), 16.dp, 0.78f)
            .padding(18.dp),
        content = content,
    )
}

@Composable
private fun Step(
    number: Int,
    title: String,
    description: String,
    done: Boolean?,
    buttonLabel: String?,
    onButton: (() -> Unit)?,
) {
    val accent = LocalAccent.current
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (done == true) Nexo.success.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", color = if (done == true) Nexo.success else Nexo.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Nexo.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = Nexo.textDim, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(done, isInfo = buttonLabel == null)
                if (buttonLabel != null && onButton != null && done != true) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        buttonLabel,
                        color = accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onButton)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(done: Boolean?, isInfo: Boolean) {
    if (isInfo) return
    val (icon: ImageVector, label: String, color: Color) = when (done) {
        true -> Triple(Icons.Rounded.CheckCircle, "Listo", Nexo.success)
        false -> Triple(Icons.Rounded.RadioButtonUnchecked, "Pendiente", Nexo.textDim)
        null -> Triple(Icons.Rounded.HelpOutline, "Revísalo a mano", Nexo.textDim)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Tip(icon: ImageVector, text: String) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = LocalAccent.current, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, color = Nexo.textDim, fontSize = 13.sp)
    }
}

@Composable
private fun CodeBlock(code: String, onCopy: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            code,
            color = Nexo.text,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onCopy),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copiar", tint = Nexo.textDim, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun PrimaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(accent)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Nexo.onAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GhostButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Nexo.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
