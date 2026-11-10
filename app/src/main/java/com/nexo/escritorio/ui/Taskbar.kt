package com.nexo.escritorio.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.nexo.escritorio.AppEntry
import com.nexo.escritorio.DesktopActions
import com.nexo.escritorio.NexoPrefs
import java.time.LocalDateTime

/** Barra de tareas flotante: inicio, apps ancladas y abiertas, y la bandeja del sistema. */
@Composable
fun Taskbar(
    modifier: Modifier,
    byKey: Map<String, AppEntry>,
    running: List<String>,
    minimized: List<String>,
    onActivate: (AppEntry) -> Unit,
    ui: DesktopUi,
    actions: DesktopActions,
    now: LocalDateTime,
    battery: BatteryInfo,
    net: NetKind,
    notifCount: Int,
    onAppMenu: (AppEntry, Offset) -> Unit,
) {
    val pins = NexoPrefs.taskbarPins
    val shown = (pins + running.filterNot { it in pins }).mapNotNull { byKey[it] }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .height(56.dp)
            .glass(RoundedCornerShape(18.dp), 28.dp, 0.82f)
    ) {
        val centerMax = (maxWidth - 440.dp).coerceAtLeast(160.dp)

        Row(
            Modifier
                .align(Alignment.Center)
                .widthIn(max = centerMax)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            StartButton(open = ui.startOpen) { ui.toggleStart() }
            BarIconButton(Icons.Rounded.Search, "Buscar") { ui.toggleStart() }
            if (shown.isNotEmpty()) {
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .width(1.dp)
                        .height(26.dp)
                        .background(Color.White.copy(alpha = 0.12f))
                )
            }
            shown.forEach { app ->
                TaskbarApp(
                    app = app,
                    isOpen = app.key in running,
                    isMinimized = app.key in minimized,
                    onClick = {
                        ui.closeAll()
                        onActivate(app)
                    },
                    onMenu = { pos ->
                        ui.closeAll()
                        onAppMenu(app, pos)
                    },
                )
            }
        }

        Row(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TrayButton(onClick = { ui.togglePanel() }) {
                Icon(netIcon(net), contentDescription = "Red", tint = Nexo.text, modifier = Modifier.size(18.dp))
                if (battery.level >= 0) {
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        batteryIcon(battery),
                        contentDescription = "Batería",
                        tint = if (battery.level <= 15 && !battery.charging) Nexo.danger else Nexo.text,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("${battery.level}%", color = Nexo.text, fontSize = 12.sp)
                }
            }
            TrayButton(onClick = { ui.togglePanel() }) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatTime(now, NexoPrefs.clock24), color = Nexo.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(formatDateShort(now), color = Nexo.textDim, fontSize = 11.sp)
                }
            }
            TrayButton(onClick = { ui.togglePanel() }) {
                Box {
                    Icon(
                        if (notifCount > 0) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsNone,
                        contentDescription = "Notificaciones",
                        tint = Nexo.text,
                        modifier = Modifier.size(20.dp),
                    )
                    if (notifCount > 0) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(LocalAccent.current)
                        )
                    }
                }
            }
            ShowDesktopButton {
                ui.closeAll()
                actions.showDesktop()
            }
        }
    }
}

/** Franja al final de la barra: trae el escritorio al frente (como en Windows). */
@Composable
private fun ShowDesktopButton(onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        Modifier
            .padding(start = 4.dp)
            .width(10.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (hovered) Color.White.copy(alpha = 0.16f) else Color.Transparent)
            .hoverable(source)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(1.dp)
                .height(22.dp)
                .background(Color.White.copy(alpha = 0.18f))
        )
        if (hovered) HoverTooltip("Mostrar escritorio")
    }
}

private fun netIcon(net: NetKind): ImageVector = when (net) {
    NetKind.WIFI -> Icons.Rounded.Wifi
    NetKind.CELL -> Icons.Rounded.SignalCellularAlt
    NetKind.ETHERNET -> Icons.Rounded.SettingsEthernet
    NetKind.NONE -> Icons.Rounded.WifiOff
}

private fun batteryIcon(b: BatteryInfo): ImageVector = when {
    b.charging -> Icons.Rounded.BatteryChargingFull
    b.level <= 15 -> Icons.Rounded.BatteryAlert
    b.level >= 90 -> Icons.Rounded.BatteryFull
    else -> Icons.Rounded.BatteryStd
}

@Composable
private fun StartButton(open: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val accent = LocalAccent.current
    val scale by animateFloatAsState(
        when {
            pressed -> 0.86f
            hovered || open -> 1.08f
            else -> 1f
        },
        label = "start",
    )
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (hovered || open) Color.White.copy(alpha = 0.10f) else Color.Transparent)
            .hoverable(source)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        NexoLogo(22.dp, accent, Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
        if (hovered && !open) HoverTooltip("Inicio")
    }
}

@Composable
private fun BarIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (hovered) Color.White.copy(alpha = 0.10f) else Color.Transparent)
            .hoverable(source)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Nexo.text, modifier = Modifier.size(21.dp))
        if (hovered) HoverTooltip(label)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskbarApp(
    app: AppEntry,
    isOpen: Boolean,
    isMinimized: Boolean,
    onClick: () -> Unit,
    onMenu: (Offset) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val accent = LocalAccent.current
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val scale by animateFloatAsState(
        when {
            pressed -> 0.86f
            hovered -> 1.10f
            else -> 1f
        },
        label = "icon",
    )
    val indicatorW by animateDpAsState(if (hovered) 18.dp else 6.dp, label = "indicator")
    Box(
        Modifier
            .size(48.dp)
            .onGloballyPositioned { holder[0] = it }
            .clip(RoundedCornerShape(12.dp))
            .background(if (hovered) Color.White.copy(alpha = 0.10f) else Color.Transparent)
            .hoverable(source)
            .combinedClickable(
                interactionSource = source,
                indication = null,
                onClick = onClick,
                onLongClick = { onMenu(holder.centerInRoot()) },
            )
            .contextMenuTrigger(onMenu),
        contentAlignment = Alignment.Center,
    ) {
        AppIconImage(
            app, 30.dp,
            Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (isMinimized) 0.7f else 1f
            },
        )
        if (isOpen) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .width(indicatorW)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(if (isMinimized) Nexo.textDim.copy(alpha = 0.6f) else accent)
            )
        }
        if (hovered) HoverTooltip(if (isMinimized) "${app.label} (minimizada)" else app.label)
    }
}

@Composable
private fun TrayButton(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Row(
        Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (hovered) Color.White.copy(alpha = 0.10f) else Color.Transparent)
            .hoverable(source)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** false en la barra superpuesta (allí las ventanitas emergentes no se pueden mostrar). */
val LocalTooltips = compositionLocalOf { true }

/** Etiqueta que aparece encima del elemento al pasar el ratón. */
@Composable
fun HoverTooltip(text: String) {
    if (!LocalTooltips.current) return
    val density = LocalDensity.current
    val offsetY = with(density) { -46.dp.roundToPx() }
    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, offsetY),
        properties = PopupProperties(focusable = false),
    ) {
        Text(
            text,
            color = Nexo.text,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier
                .glass(RoundedCornerShape(8.dp), 8.dp, 0.95f)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
