package com.nexo.escritorio.ui

import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexo.escritorio.AppEntry
import com.nexo.escritorio.DesktopActions
import com.nexo.escritorio.NexoPrefs
import com.nexo.escritorio.NotifItem
import com.nexo.escritorio.NotificationService
import java.time.LocalDateTime
import android.provider.Settings as AndroidSettings

private data class QuickTileSpec(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val active: Boolean,
    val onClick: () -> Unit,
)

/** Centro de control: accesos rápidos y notificaciones. */
@Composable
fun ControlPanel(
    ui: DesktopUi,
    actions: DesktopActions,
    now: LocalDateTime,
    battery: BatteryInfo,
    net: NetKind,
    byPkg: Map<String, AppEntry>,
    maxH: Dp,
) {
    val context = LocalContext.current
    val notifs by NotificationService.items.collectAsState()
    val connected by NotificationService.connected.collectAsState()
    var enabled by remember { mutableStateOf(NotificationService.isEnabled(context)) }
    LaunchedEffect(connected) { enabled = NotificationService.isEnabled(context) }

    fun openAndClose(intent: Intent) {
        ui.closeAll()
        actions.open(intent)
    }

    val tiles = listOf(
        QuickTileSpec("Wi‑Fi", if (net == NetKind.WIFI) "Conectado" else "Redes", Icons.Rounded.Wifi, net == NetKind.WIFI) {
            openAndClose(Intent(AndroidSettings.Panel.ACTION_INTERNET_CONNECTIVITY))
        },
        QuickTileSpec("Bluetooth", "Dispositivos", Icons.Rounded.Bluetooth, false) {
            openAndClose(Intent(AndroidSettings.ACTION_BLUETOOTH_SETTINGS))
        },
        QuickTileSpec("Sonido", "Volumen", Icons.Rounded.VolumeUp, false) {
            openAndClose(Intent(AndroidSettings.Panel.ACTION_VOLUME))
        },
        QuickTileSpec("Pantalla", "Brillo y más", Icons.Rounded.Brightness6, false) {
            openAndClose(Intent(AndroidSettings.ACTION_DISPLAY_SETTINGS))
        },
        QuickTileSpec(
            "Batería",
            when {
                battery.level < 0 -> "Estado"
                battery.charging -> "${battery.level}% · cargando"
                else -> "${battery.level}%"
            },
            if (battery.charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
            battery.charging,
        ) {
            openAndClose(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
        },
        QuickTileSpec("Ajustes", "Del teléfono", Icons.Rounded.Settings, false) {
            openAndClose(Intent(AndroidSettings.ACTION_SETTINGS))
        },
    )

    Column(
        Modifier
            .width(392.dp)
            .heightIn(max = maxH)
            .glass(RoundedCornerShape(22.dp), 32.dp, 0.92f)
            .blockPointer()
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                formatTime(now, NexoPrefs.clock24),
                color = Nexo.text,
                fontSize = 34.sp,
                fontWeight = FontWeight.Light,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                formatDateLong(now),
                color = Nexo.textDim,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 7.dp),
            )
        }
        Spacer(Modifier.height(14.dp))

        tiles.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { QuickTile(it, Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Notificaciones",
                color = Nexo.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (enabled && notifs.any { it.clearable }) {
                Text(
                    "Borrar todo",
                    color = LocalAccent.current,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { NotificationService.dismissAll() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        when {
            !enabled -> PermissionCard {
                openAndClose(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            notifs.isEmpty() -> EmptyNotifications()
            else -> LazyColumn(
                Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(notifs, key = { it.key }) { n ->
                    NotificationCard(
                        n = n,
                        app = byPkg[n.packageName],
                        onOpen = {
                            ui.closeAll()
                            actions.openNotification(n)
                        },
                        onDismiss = { NotificationService.dismiss(n.key) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickTile(spec: QuickTileSpec, modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val accent = LocalAccent.current
    val bg = when {
        spec.active -> accent
        hovered -> Color.White.copy(alpha = 0.12f)
        else -> Color.White.copy(alpha = 0.07f)
    }
    val fg = if (spec.active) Nexo.onAccent else Nexo.text
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .hoverable(source)
            .clickable(interactionSource = source, indication = null, onClick = spec.onClick)
            .padding(12.dp)
    ) {
        Icon(spec.icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(10.dp))
        Text(spec.title, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(
            spec.subtitle,
            color = if (spec.active) Nexo.onAccent.copy(alpha = 0.75f) else Nexo.textDim,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PermissionCard(onAllow: () -> Unit) {
    val accent = LocalAccent.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(16.dp)
    ) {
        Icon(Icons.Rounded.Notifications, null, tint = accent, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(8.dp))
        Text("Ve tus notificaciones aquí", color = Nexo.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Permite a Nexo leer las notificaciones para mostrarlas y abrirlas desde el escritorio. Solo se usan en tu teléfono.",
            color = Nexo.textDim,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Permitir acceso",
            color = Nexo.onAccent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(accent)
                .clickable(onClick = onAllow)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun EmptyNotifications() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.CheckCircle, null, tint = Nexo.success, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(8.dp))
        Text("Todo al día", color = Nexo.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text("No tienes notificaciones nuevas", color = Nexo.textDim, fontSize = 12.sp)
    }
}

@Composable
private fun NotificationCard(n: NotifItem, app: AppEntry?, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val ago = DateUtils.getRelativeTimeSpanString(
        n.postTime, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = if (hovered) 0.10f else 0.05f))
            .hoverable(source)
            .clickable(interactionSource = source, indication = null, onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (app != null) {
            AppIconImage(app, 30.dp)
        } else {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    app?.label ?: n.packageName,
                    color = Nexo.textDim,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(ago, color = Nexo.textFaint, fontSize = 11.sp)
            }
            if (n.title.isNotBlank()) {
                Text(
                    n.title,
                    color = Nexo.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (n.text.isNotBlank()) {
                Text(n.text, color = Nexo.textDim, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        if (n.clearable) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Descartar",
                    tint = Nexo.textDim.copy(alpha = if (hovered) 1f else 0.5f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
