package com.nexo.escritorio.ui

import android.view.Display
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexo.escritorio.Displays
import com.nexo.escritorio.MainActivity
import com.nexo.escritorio.NexoAccessibility
import com.nexo.escritorio.NexoPrefs

data class SetupStatus(
    val developerOptions: Boolean?,
    val freeform: Boolean?,
    val forceDesktop: Boolean?,
    val resizable: Boolean?,
    val notifications: Boolean,
    val canWriteSettings: Boolean,
    val overlay: Boolean,
    val assistant: Boolean,
    val isXiaomi: Boolean,
    val phoneIp: String?,
    val shizukuInstalled: Boolean,
    val shizukuRunning: Boolean,
    val shizukuReady: Boolean,
)

private const val PKG = "com.nexo.escritorio"

private const val GRANT_COMMAND =
    "adb shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS"

private const val MANUAL_COMMANDS = """adb shell settings put global enable_freeform_support 1
adb shell settings put global force_resizable_activities 1
adb shell settings put global force_desktop_mode_on_external_displays 1
adb reboot"""

private const val SHIZUKU_ADB =
    "adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh"

private const val SCRCPY_USB = "scrcpy --new-display=1920x1080/160 --start-app=$PKG"

private enum class Conn { CABLE, WIRELESS, PC, PHONE }

/** Pantalla principal en el teléfono. */
@Composable
fun SetupScreen(tick: Int, display: Display?, host: MainActivity) {
    val status = remember(tick, display) { host.readStatus() }
    val accent = LocalAccent.current
    var open by remember { mutableStateOf<Conn?>(null) }
    val toggle: (Conn) -> Unit = { c -> open = if (open == c) null else c }

    Box(Modifier.fillMaxSize()) {
        Wallpaper(0, null, animated = true, modifier = Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NexoLogo(40.dp, accent)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Nexo Escritorio", color = Nexo.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text("Tu teléfono, convertido en un computador", color = Nexo.textDim, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(20.dp))

                HeroCard(display, host)
                Spacer(Modifier.height(22.dp))

                Text("Formas de conexión", color = Nexo.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Elige la que tengas a mano. Puedes usar varias.", color = Nexo.textDim, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))

                ConnectionCard(
                    icon = Icons.Rounded.Usb,
                    title = "Cable USB‑C a HDMI",
                    badge = "Teléfonos con salida de video",
                    summary = "La mejor experiencia: escritorio a pantalla completa y el teléfono libre.",
                    expanded = open == Conn.CABLE,
                    onToggle = { toggle(Conn.CABLE) },
                ) {
                    MiniStep(1, "Conecta un cable o hub USB‑C a HDMI (con «DisplayPort Alt Mode») al monitor o TV.")
                    MiniStep(2, "Conecta teclado y ratón por Bluetooth o al hub.")
                    MiniStep(3, "El escritorio aparece solo en la pantalla.")
                    Note("¿La pantalla se queda en negro? Tu teléfono no saca video por cable (pasa en Redmi, Redmi Note y muchos de gama media). Usa la conexión inalámbrica o la del computador.")
                }

                ConnectionCard(
                    icon = Icons.Rounded.Cast,
                    title = "Inalámbrica (Miracast)",
                    badge = "Smart TV · Fire TV · PC con Windows",
                    summary = "Sin cables, a una TV o a un portátil con Windows («Proyectar en este PC»).",
                    expanded = open == Conn.WIRELESS,
                    onToggle = { toggle(Conn.WIRELESS) },
                ) {
                    MiniStep(1, "En la TV activa «Duplicar pantalla» (Screen mirroring). En Windows abre «Proyectar en este PC» o la app «Conectar».")
                    MiniStep(2, "Pulsa «Buscar pantallas» y elige tu TV o PC.")
                    MiniStep(3, "Si la TV muestra el escritorio de Nexo, ¡listo! Si solo copia la pantalla del teléfono, pulsa «Escritorio en este teléfono».")
                    Spacer(Modifier.height(10.dp))
                    FlowRow2 {
                        PrimaryButton("Buscar pantallas") { host.openCast() }
                        GhostButton("Escritorio en este teléfono") { host.previewHere() }
                    }
                    Note("Por Wi‑Fi hay un poco de retraso: perfecto para trabajar, navegar y ver videos; para juegos rápidos es mejor el cable o el computador.")
                }

                ConnectionCard(
                    icon = Icons.Rounded.Laptop,
                    title = "Computador o portátil (scrcpy)",
                    badge = "Casi cualquier teléfono · Android 10+",
                    summary = "Crea una pantalla nueva en tu PC con el escritorio de Nexo, aunque tu teléfono no saque video. Incluye sonido.",
                    expanded = open == Conn.PC,
                    onToggle = { toggle(Conn.PC) },
                ) {
                    MiniStep(1, "Instala scrcpy en el computador (gratis, Windows, Mac o Linux). Búscalo como «scrcpy» en GitHub.")
                    MiniStep(2, "En el teléfono: Opciones de desarrollador › activa «Depuración USB». Conecta el teléfono al PC con un cable USB normal.")
                    MiniStep(3, "En el PC, abre una terminal en la carpeta de scrcpy y ejecuta:")
                    CodeBlock(SCRCPY_USB) { host.copy(SCRCPY_USB) }
                    Spacer(Modifier.height(6.dp))
                    MiniStep(4, "Se abre una ventana con el escritorio de Nexo. Usa el teclado y el ratón del PC. Pulsa F11 o añade -f para pantalla completa.")
                    Spacer(Modifier.height(8.dp))
                    Text("Sin cable (por Wi‑Fi):", color = Nexo.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    val ip = status.phoneIp ?: "IP-DEL-TELEFONO"
                    val wifi = "adb tcpip 5555\nscrcpy --tcpip=$ip:5555 --new-display=1920x1080/160 --start-app=$PKG"
                    CodeBlock(wifi) { host.copy(wifi) }
                    Note(
                        "La primera línea se ejecuta una vez con el cable puesto; después ya puedes desconectarlo." +
                            if (status.phoneIp != null) " La IP de tu teléfono es $ip." else " Conéctate a una red Wi‑Fi para ver tu IP aquí."
                    )
                    Note("Para jugar con un mando conectado al PC, añade --gamepad=uhid al comando.")
                }

                ConnectionCard(
                    icon = Icons.Rounded.PhoneAndroid,
                    title = "Escritorio en este teléfono",
                    badge = "Cualquier teléfono",
                    summary = "El escritorio se muestra en el teléfono en horizontal. Duplica la pantalla en la TV (Chromecast, «Transmitir», Miracast) o úsalo con teclado y ratón Bluetooth.",
                    expanded = open == Conn.PHONE,
                    onToggle = { toggle(Conn.PHONE) },
                ) {
                    MiniStep(1, "Conecta teclado y ratón Bluetooth (opcional).")
                    MiniStep(2, "Pulsa «Abrir escritorio aquí». El teléfono gira a horizontal.")
                    MiniStep(3, "Para verlo en grande, duplica la pantalla en la TV con «Transmitir» o Google Home.")
                    Spacer(Modifier.height(10.dp))
                    FlowRow2 {
                        PrimaryButton("Abrir escritorio aquí") { host.previewHere() }
                        GhostButton("Transmitir pantalla") { host.openCast() }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.TouchApp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Usa el teléfono como touchpad", color = Nexo.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("¿Sin ratón? Mueve el puntero del monitor y escribe desde el teléfono.", color = Nexo.textDim, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Abrir touchpad", Modifier.fillMaxWidth()) { host.openTouchpad() }
                }

                Spacer(Modifier.height(14.dp))
                WindowControlsCard(status, host)

                Spacer(Modifier.height(22.dp))
                Card {
                    Text("Preparación (solo una vez)", color = Nexo.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Activa lo que quieras usar. Los pasos 1 a 5 hacen que las apps se abran en ventanas.",
                        color = Nexo.textDim,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Step(
                        1, "Activa las opciones de desarrollador",
                        "Ajustes › Acerca del teléfono › toca 7 veces «Número de compilación» (en Xiaomi: «Versión de MIUI/HyperOS»).",
                        status.developerOptions, "Abrir ajustes", host::openAboutPhone,
                    )
                    Step(
                        2, "Activa «Ventanas de formato libre»",
                        "En Opciones de desarrollador (Enable freeform windows). Las apps se abren en ventanas que puedes mover y cambiar de tamaño.",
                        status.freeform, "Opciones de desarrollador", host::openDeveloperOptions,
                    )
                    Step(
                        3, "Activa «Forzar modo de escritorio»",
                        "También en Opciones de desarrollador (Force desktop mode).",
                        status.forceDesktop, "Opciones de desarrollador", host::openDeveloperOptions,
                    )
                    Step(
                        4, "Activa «Forzar actividades redimensionables»",
                        "Casi todas las apps aceptarán abrirse en ventana (Force activities to be resizable).",
                        status.resizable, "Opciones de desarrollador", host::openDeveloperOptions,
                    )
                    Step(5, "Reinicia el teléfono", "Los pasos 2 a 4 se aplican después de reiniciar.", null, null, null)
                    Step(
                        6, "Barra de tareas siempre visible",
                        "Permite «Mostrar sobre otras apps» para que la barra quede encima de las ventanas, como en Windows.",
                        status.overlay, "Permitir", host::openOverlayPermission,
                    )
                    Step(
                        7, "Asistente de Nexo (recomendado)",
                        "En Accesibilidad activa «Nexo Escritorio». Muestra las apps abiertas de verdad en la barra, hace que la tecla Windows funcione en cualquier app y activa el touchpad, la captura y el bloqueo.",
                        status.assistant, "Abrir accesibilidad", host::openAccessibility,
                    )
                    if (!status.assistant) {
                        Note("¿Aparece «Ajuste restringido»? Ve a Información de la app › menú ⋮ › «Permitir ajustes restringidos» y vuelve a intentarlo.")
                        Text(
                            "Abrir información de la app",
                            color = accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .padding(start = 34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { host.openAppDetails() }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                    Step(
                        8, "Notificaciones en el escritorio (opcional)",
                        "Para verlas y abrirlas desde el centro de control.",
                        status.notifications, "Permitir", host::openNotificationAccess,
                    )
                    if (status.isXiaomi) {
                        Step(
                            9, "Permiso extra de Xiaomi",
                            "En Otros permisos activa «Mostrar ventanas emergentes mientras se ejecuta en segundo plano» y «Mostrar ventanas emergentes». Sin esto, Xiaomi bloquea abrir apps desde la barra.",
                            null, "Abrir permisos", host::openXiaomiPermissions,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                Card {
                    Text("¿Tienes un computador? Activa los pasos 2‑4 de una vez", color = Nexo.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Con la depuración USB activada, ejecuta este comando una sola vez y pulsa «Activar todo».",
                        color = Nexo.textDim,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    CodeBlock(GRANT_COMMAND) { host.copy(GRANT_COMMAND) }
                    Spacer(Modifier.height(10.dp))
                    if (status.canWriteSettings) {
                        PrimaryButton("Activar todo automáticamente", Modifier.fillMaxWidth()) { host.applyAllSettings() }
                    } else {
                        Note("Sin PC también funciona: usa la «Depuración inalámbrica» con una app como LADB o Shizuku para ejecutar el comando desde el propio teléfono.")
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("O todo por ADB, sin usar la app:", color = Nexo.textDim, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    CodeBlock(MANUAL_COMMANDS) { host.copy(MANUAL_COMMANDS) }
                }
                Spacer(Modifier.height(16.dp))

                Card {
                    Text("Consejos", color = Nexo.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Tip(Icons.Rounded.Keyboard, "Tecla Windows: abre el inicio. Empieza a escribir para buscar cualquier app. Win+D muestra el escritorio.")
                    Tip(Icons.Rounded.Mouse, "Clic derecho sobre una app: abrir en ventana, maximizada, a un lado, a pantalla completa o anclarla. Dentro de una app, el clic derecho o Esc vuelven atrás.")
                    Tip(Icons.Rounded.SportsEsports, "Los juegos aparecen en su propia sección y se abren a pantalla completa. Los mandos Bluetooth o USB funcionan directamente.")
                    Tip(Icons.Rounded.ScreenShare, "Añade el mosaico «Escritorio» a los ajustes rápidos para abrir el escritorio con un toque.")
                    Tip(Icons.Rounded.DesktopWindows, "En Pixel y Samsung con escritorio propio, Nexo se abre a pantalla completa por encima de él.")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Controles de ventana completos con Shizuku (sin root). */
@Composable
private fun WindowControlsCard(status: SetupStatus, host: MainActivity) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.DesktopWindows)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Ventanas como en un PC", color = Nexo.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Botones de minimizar, maximizar y cerrar en cada ventana, minimizar desde la barra, Win + flechas y ventanas que se reacomodan solas al cambiar de pantalla.",
                    color = Nexo.textDim,
                    fontSize = 13.sp,
                )
            }
        }
        val assistOk = status.assistant && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
        Spacer(Modifier.height(8.dp))
        StatusLine(
            status.shizukuReady || assistOk,
            when {
                status.shizukuReady -> "Activo con Shizuku (modo más rápido)"
                assistOk -> "Activo con el asistente de Nexo"
                else -> "Elige una de las dos opciones de abajo"
            },
        )
        if (!status.shizukuReady) {
            Spacer(Modifier.height(12.dp))
            Text("Opción 1 · Asistente de Nexo (sin instalar nada)", color = Nexo.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Activa «Nexo Escritorio» en Accesibilidad. Usa los botones de Android y mueve las ventanas por ti. " +
                    "Es un poco más lento y se nota el movimiento. Necesita Android 11 o superior.",
                color = Nexo.textDim,
                fontSize = 13.sp,
            )
            if (!assistOk) {
                Spacer(Modifier.height(8.dp))
                PrimaryButton("Activar el asistente") { host.openAccessibility() }
            }
            Spacer(Modifier.height(14.dp))
            Text("Opción 2 · Shizuku (más rápido y preciso)", color = Nexo.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            MiniStep(1, "Instala Shizuku desde la tienda (gratis, sin root).")
            MiniStep(2, "Abre Shizuku y pulsa «Iniciar» en «Iniciar mediante depuración inalámbrica» (Android 11+, con Wi‑Fi). Se empareja una sola vez.")
            MiniStep(3, "Vuelve aquí y pulsa «Dar permiso a Nexo».")
            Spacer(Modifier.height(10.dp))
            FlowRow2 {
                PrimaryButton(if (status.shizukuInstalled) "Abrir Shizuku" else "Instalar Shizuku") { host.openShizuku() }
                GhostButton("Dar permiso a Nexo") { host.requestShizuku() }
            }
            Note("Con un PC también puedes iniciarlo por cable con este comando:")
            Spacer(Modifier.height(6.dp))
            CodeBlock(SHIZUKU_ADB) { host.copy(SHIZUKU_ADB) }
            Note("Shizuku se detiene al reiniciar el teléfono: ábrelo y pulsa «Iniciar» otra vez. Si tienes las dos opciones, Nexo usa Shizuku. Sin ninguna, quedan los botones propios de Android en cada ventana.")
        }
    }
}

@Composable
private fun StatusLine(ok: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            null,
            tint = if (ok) Nexo.success else Nexo.textDim,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (ok) Nexo.success else Nexo.textDim, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun HeroCard(display: Display?, host: MainActivity) {
    val accent = LocalAccent.current
    Card {
        if (display != null) {
            val size = remember(display) { Displays.sizeOf(display) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.DesktopWindows)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Pantalla conectada", color = Nexo.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("${display.name} · ${size.x} × ${size.y}", color = Nexo.textDim, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Abrir escritorio en la pantalla", Modifier.fillMaxWidth()) { host.openDesktopOnMonitor() }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PhoneAndroid, null, tint = Nexo.text, modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Rounded.Usb, null, tint = accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.Cast, null, tint = accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.Laptop, null, tint = accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Rounded.DesktopWindows, null, tint = Nexo.text, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text("Conecta una pantalla", color = Nexo.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Por cable, por Wi‑Fi o a través de tu computador. Cuando se detecte, el escritorio se abrirá solo.",
                color = Nexo.textDim,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(14.dp))
            GhostButton("Probar el escritorio en este teléfono", Modifier.fillMaxWidth()) { host.previewHere() }
        }
        Spacer(Modifier.height(6.dp))
        SwitchRow(
            "Abrir automáticamente al conectar",
            "También con la app cerrada si el asistente o las notificaciones de Nexo están activos",
            NexoPrefs.autoOpen,
        ) { NexoPrefs.changeAutoOpen(it) }
    }
}

@Composable
private fun ConnectionCard(
    icon: ImageVector,
    title: String,
    badge: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Column(
        Modifier
            .padding(bottom = 10.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(20.dp), 12.dp, 0.78f)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Nexo.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(badge, color = LocalAccent.current, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(summary, color = Nexo.textDim, fontSize = 13.sp)
            }
            Icon(
                Icons.Rounded.ExpandMore, null, tint = Nexo.textDim,
                modifier = Modifier.size(24.dp).rotate(rotation),
            )
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), content = content)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow2(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun IconBadge(icon: ImageVector) {
    val accent = LocalAccent.current
    Box(
        Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun MiniStep(n: Int, text: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("$n", color = Nexo.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, color = Nexo.textDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Note(text: String) {
    Text(text, color = Nexo.textFaint, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
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
            .padding(vertical = 13.dp, horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Nexo.onAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
            .padding(vertical = 12.dp, horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Nexo.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
