package com.nexo.escritorio.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.FilterNone
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerticalSplit
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexo.escritorio.AppEntry
import com.nexo.escritorio.AppRepository
import com.nexo.escritorio.DesktopActions
import com.nexo.escritorio.NexoPrefs
import com.nexo.escritorio.NexoAccessibility
import com.nexo.escritorio.NotificationService
import com.nexo.escritorio.WindowLauncher
import com.nexo.escritorio.WindowMode
import java.time.LocalDateTime
import android.provider.Settings as AndroidSettings

/** Estado de lo que está abierto en el escritorio (menús, paneles, selección). */
class DesktopUi {
    var startOpen by mutableStateOf(false)
    var panelOpen by mutableStateOf(false)
    var settingsOpen by mutableStateOf(false)
    var menu by mutableStateOf<MenuSpec?>(null)
    /** Menú contextual dibujado en la barra superpuesta. */
    var overlayMenu by mutableStateOf<MenuSpec?>(null)
    var query by mutableStateOf("")
    var selected by mutableStateOf<String?>(null)
    /** true cuando la barra vive en la capa superpuesta (siempre encima de las apps). */
    var overlayActive by mutableStateOf(false)
    /** Barra escondida (por ejemplo, con un juego a pantalla completa). */
    var barHidden by mutableStateOf(false)

    val anyOpen: Boolean get() = startOpen || panelOpen || settingsOpen || menu != null || overlayMenu != null

    fun closeAll() {
        startOpen = false
        panelOpen = false
        settingsOpen = false
        menu = null
        overlayMenu = null
    }

    fun toggleStart(initialQuery: String = "") {
        val open = !startOpen
        barHidden = false
        closeAll()
        if (open) {
            query = initialQuery
            startOpen = true
        }
    }

    fun togglePanel() {
        val open = !panelOpen
        closeAll()
        panelOpen = open
    }

    fun openSettings() {
        closeAll()
        settingsOpen = true
    }
}

const val TASKBAR_RESERVE_DP = 76

@Composable
fun DesktopRoot(actions: DesktopActions, ui: DesktopUi) {
    val apps by AppRepository.apps.collectAsState()
    val byKey = remember(apps) { apps.associateBy { it.key } }
    val byPkg = remember(apps) { apps.associateBy { it.packageName } }
    val notifs by NotificationService.items.collectAsState()
    val accent = accentColor(NexoPrefs.accent)
    val density = LocalDensity.current
    val focus = remember { FocusRequester() }
    val now by rememberNow()
    val battery = rememberBattery()
    val net = rememberNetwork()
    val windowInfo = LocalWindowInfo.current
    val metaCombo = remember { booleanArrayOf(false) }

    LaunchedEffect(Unit) { NexoPrefs.loadCustomWallpaper() }
    LaunchedEffect(windowInfo.isWindowFocused, ui.startOpen) {
        if (windowInfo.isWindowFocused && !ui.startOpen) runCatching { focus.requestFocus() }
    }

    fun appMenu(app: AppEntry, pos: Offset): MenuSpec = buildAppMenu(app, pos, actions, ui)

    fun onKey(e: KeyEvent): Boolean {
        val isMeta = e.key == Key.MetaLeft || e.key == Key.MetaRight
        if (isMeta && winKeyIsGlobal()) return false
        if (isMeta) {
            if (e.type == KeyEventType.KeyDown) metaCombo[0] = false
            else if (e.type == KeyEventType.KeyUp && !metaCombo[0]) ui.toggleStart()
            return true
        }
        if (e.type != KeyEventType.KeyDown) return false
        if (e.isMetaPressed) {
            metaCombo[0] = true
            when (e.key) {
                Key.A, Key.N -> { ui.togglePanel(); return true }
                Key.E -> { ui.closeAll(); actions.files(); return true }
                Key.I -> { ui.openSettings(); return true }
                Key.D -> { ui.closeAll(); actions.showDesktop(); return true }
                Key.T -> { ui.closeAll(); actions.touchpad(); return true }
                Key.S -> { ui.toggleStart(); return true }
            }
            return false
        }
        if (e.isCtrlPressed && e.key == Key.Escape) { ui.toggleStart(); return true }
        if (e.key == Key.Escape) {
            if (ui.anyOpen) { ui.closeAll(); return true }
            ui.selected = null
            return false
        }
        if (!ui.anyOpen) {
            val sel = ui.selected
            if (sel != null && (e.key == Key.Enter || e.key == Key.NumPadEnter)) {
                byKey[sel]?.let { actions.launch(it) }
                return true
            }
            if (sel != null && e.key == Key.Delete) {
                if (sel in NexoPrefs.desktopItems) NexoPrefs.toggleDesktop(sel)
                ui.selected = null
                return true
            }
            if (!e.isCtrlPressed && !e.isAltPressed) {
                val ch = e.nativeKeyEvent.unicodeChar
                if (ch > 0 && Character.isLetterOrDigit(ch)) {
                    ui.toggleStart(String(Character.toChars(ch)))
                    return true
                }
            }
        }
        return false
    }

    NexoTheme(accent) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .onSizeChanged {
                    WindowLauncher.screenW = it.width
                    WindowLauncher.screenH = it.height
                    WindowLauncher.reservedBottom = with(density) { TASKBAR_RESERVE_DP.dp.roundToPx() }
                }
                .onPreviewKeyEvent { onKey(it) }
                .focusRequester(focus)
                .focusable()
        ) {
            val maxW = maxWidth
            val maxH = maxHeight

            Wallpaper(NexoPrefs.wallpaper, NexoPrefs.customWallpaper, NexoPrefs.animatedWallpaper, Modifier.fillMaxSize())

            // Zona del escritorio: iconos, reloj y clic derecho
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = TASKBAR_RESERVE_DP.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            ui.selected = null
                            ui.closeAll()
                        })
                    }
                    .contextMenuTrigger { pos ->
                        ui.closeAll()
                        ui.menu = desktopMenu(pos, ui, byKey, actions)
                    }
            ) {
                if (NexoPrefs.showDesktopIcons) {
                    DesktopIcons(
                        items = NexoPrefs.desktopItems.mapNotNull { byKey[it] },
                        ui = ui,
                        onOpen = { actions.launch(it) },
                        onMenu = { app, pos -> ui.closeAll(); ui.menu = appMenu(app, pos) },
                    )
                }
                if (maxW > 760.dp) {
                    ClockWidget(now, battery, Modifier.align(Alignment.TopEnd))
                }
            }

            if (!ui.overlayActive) {
                ChromeLayer(actions, ui, maxW, maxH, expandedArea = true) { ui.menu = it }
            }

            if (ui.settingsOpen) {
                SettingsWindow(ui = ui, maxW = maxW, maxH = maxH)
            }

            ContextMenuHost(ui.menu) { ui.menu = null }
        }
    }
}

/** La tecla Windows la maneja el asistente cuando está activo (funciona sobre cualquier app). */
fun winKeyIsGlobal(): Boolean = NexoPrefs.globalWinKey && NexoAccessibility.connected.value

/** Claves de las apps abiertas en una pantalla: reales con el asistente, o las abiertas desde Nexo. */
@Composable
fun rememberRunning(displayId: Int, byPkg: Map<String, AppEntry>): List<String> {
    val connected by NexoAccessibility.connected.collectAsState()
    val running by NexoAccessibility.running.collectAsState()
    return if (connected) {
        running[displayId].orEmpty().mapNotNull { byPkg[it]?.key }.distinct()
    } else {
        WindowLauncher.opened.toList()
    }
}

/** Barra de tareas, menú de inicio y centro de control. */
@Composable
fun BoxScope.ChromeLayer(
    actions: DesktopActions,
    ui: DesktopUi,
    maxW: Dp,
    maxH: Dp,
    expandedArea: Boolean,
    showMenu: (MenuSpec) -> Unit,
) {
    val apps by AppRepository.apps.collectAsState()
    val byKey = remember(apps) { apps.associateBy { it.key } }
    val byPkg = remember(apps) { apps.associateBy { it.packageName } }
    val notifs by NotificationService.items.collectAsState()
    val now by rememberNow()
    val battery = rememberBattery()
    val net = rememberNetwork()
    val running = rememberRunning(actions.displayId, byPkg)
    val appMenu: (AppEntry, Offset) -> Unit = { app, pos -> showMenu(buildAppMenu(app, pos, actions, ui)) }

    if (expandedArea) {
        // Un clic fuera del menú de inicio o del panel los cierra
        if (ui.startOpen || ui.panelOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            ui.startOpen = false
                            ui.panelOpen = false
                        }
                    }
            )
        }

        AnimatedVisibility(
            visible = ui.startOpen,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (TASKBAR_RESERVE_DP + 4).dp),
            enter = fadeIn(tween(150)) +
                slideInVertically(tween(240, easing = FastOutSlowInEasing)) { it / 7 } +
                scaleIn(tween(240), initialScale = 0.96f, transformOrigin = TransformOrigin(0.5f, 1f)),
            exit = fadeOut(tween(120)) +
                slideOutVertically(tween(160)) { it / 9 } +
                scaleOut(tween(160), targetScale = 0.97f, transformOrigin = TransformOrigin(0.5f, 1f)),
        ) {
            StartMenu(
                apps = apps,
                byKey = byKey,
                ui = ui,
                actions = actions,
                maxW = maxW - 24.dp,
                maxH = maxH - (TASKBAR_RESERVE_DP + 28).dp,
                onAppMenu = appMenu,
            )
        }

        AnimatedVisibility(
            visible = ui.panelOpen,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = (TASKBAR_RESERVE_DP + 4).dp),
            enter = fadeIn(tween(150)) + slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { it / 5 },
            exit = fadeOut(tween(120)) + slideOutHorizontally(tween(160)) { it / 6 },
        ) {
            ControlPanel(
                ui = ui,
                actions = actions,
                now = now,
                battery = battery,
                net = net,
                byPkg = byPkg,
                maxH = maxH - (TASKBAR_RESERVE_DP + 28).dp,
            )
        }
    }

    Taskbar(
        modifier = Modifier.align(Alignment.BottomCenter),
        byKey = byKey,
        running = running,
        ui = ui,
        actions = actions,
        now = now,
        battery = battery,
        net = net,
        notifCount = notifs.size,
        onAppMenu = appMenu,
    )
}

/** Contenido de la barra superpuesta (encima de todas las apps). */
@Composable
fun BarChrome(actions: DesktopActions, ui: DesktopUi, expanded: Boolean, menuOffsetY: () -> Float) {
    CompositionLocalProvider(LocalTooltips provides false) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.Escape && ui.anyOpen) {
                        ui.closeAll()
                        true
                    } else if (e.type == KeyEventType.KeyUp && (e.key == Key.MetaLeft || e.key == Key.MetaRight) && !winKeyIsGlobal()) {
                        ui.toggleStart()
                        true
                    } else {
                        false
                    }
                }
        ) {
            val maxW = maxWidth
            val maxH = maxHeight
            if (ui.barHidden && !expanded) {
                // Barra escondida: una franja fina que la muestra al pasar el ratón o tocar.
                val source = remember { MutableInteractionSource() }
                val hovered by source.collectIsHoveredAsState()
                LaunchedEffect(hovered) { if (hovered) ui.barHidden = false }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(LocalAccent.current.copy(alpha = 0.35f))
                        .hoverable(source)
                        .pointerInput(Unit) { detectTapGestures { ui.barHidden = false } }
                )
            } else {
                ChromeLayer(actions, ui, maxW, maxH, expandedArea = expanded) { spec ->
                    ui.overlayMenu = spec.copy(position = spec.position + Offset(0f, menuOffsetY()))
                }
            }
            if (expanded) {
                ContextMenuHost(ui.overlayMenu) { ui.overlayMenu = null }
            }
        }
    }
}

fun buildAppMenu(app: AppEntry, pos: Offset, actions: DesktopActions, ui: DesktopUi): MenuSpec {
    val pinned = app.key in NexoPrefs.taskbarPins
    val onDesktop = app.key in NexoPrefs.desktopItems
    val close = { ui.startOpen = false; ui.panelOpen = false; ui.overlayMenu = null }
    return MenuSpec(
        position = pos,
        title = app.label,
        entries = listOf(
            MenuAction("Abrir", Icons.Rounded.OpenInNew) { close(); actions.launch(app) },
            MenuAction("Abrir en ventana", Icons.Rounded.FilterNone) { close(); actions.launch(app, WindowMode.WINDOW) },
            MenuAction("Abrir maximizada", Icons.Rounded.CropFree) { close(); actions.launch(app, WindowMode.MAXIMIZED) },
            MenuAction(if (app.isGame) "Jugar a pantalla completa" else "Pantalla completa", Icons.Rounded.Fullscreen) {
                close(); actions.launch(app, WindowMode.FULLSCREEN)
            },
            MenuAction("Mitad izquierda", Icons.Rounded.VerticalSplit) { close(); actions.launch(app, WindowMode.LEFT) },
            MenuAction("Mitad derecha", Icons.Rounded.VerticalSplit) { close(); actions.launch(app, WindowMode.RIGHT) },
            MenuAction("Nueva ventana", Icons.Rounded.Add) { close(); actions.launch(app, WindowMode.WINDOW, newInstance = true) },
            MenuDivider,
            MenuAction(if (pinned) "Desanclar de la barra" else "Anclar a la barra de tareas", Icons.Rounded.PushPin) {
                NexoPrefs.togglePin(app.key)
            },
            MenuAction(if (onDesktop) "Quitar del escritorio" else "Añadir al escritorio", Icons.Rounded.DesktopWindows) {
                NexoPrefs.toggleDesktop(app.key)
            },
            MenuDivider,
            MenuAction("Información de la app", Icons.Rounded.Info) { close(); actions.appInfo(app) },
            MenuAction("Desinstalar", Icons.Rounded.Delete, danger = true) { close(); actions.uninstall(app) },
        ),
    )
}

private fun desktopMenu(
    pos: Offset,
    ui: DesktopUi,
    byKey: Map<String, AppEntry>,
    actions: DesktopActions,
): MenuSpec = MenuSpec(
    position = pos,
    title = null,
    entries = listOf(
        MenuAction("Cambiar fondo de pantalla", Icons.Rounded.Wallpaper) { ui.openSettings() },
        MenuAction("Ordenar iconos por nombre", Icons.Rounded.SortByAlpha) {
            NexoPrefs.sortDesktop { key -> byKey[key]?.label ?: key }
        },
        MenuAction(
            if (NexoPrefs.showDesktopIcons) "Ocultar iconos" else "Mostrar iconos",
            Icons.Rounded.GridView,
        ) { NexoPrefs.changeShowDesktopIcons(!NexoPrefs.showDesktopIcons) },
        MenuDivider,
        MenuAction("Ajustes de pantalla", Icons.Rounded.Brightness6) {
            actions.open(Intent(AndroidSettings.ACTION_DISPLAY_SETTINGS))
        },
        MenuAction("Ajustes de Nexo", Icons.Rounded.Tune) { ui.openSettings() },
        MenuAction("Usar el teléfono como touchpad", Icons.Rounded.TouchApp) { actions.touchpad() },
    ),
)

// ---------- Iconos del escritorio ----------

@Composable
private fun DesktopIcons(
    items: List<AppEntry>,
    ui: DesktopUi,
    onOpen: (AppEntry) -> Unit,
    onMenu: (AppEntry, Offset) -> Unit,
) {
    val iconDp = when (NexoPrefs.iconSize) {
        0 -> 40.dp
        2 -> 64.dp
        else -> 52.dp
    }
    LazyHorizontalGrid(
        rows = GridCells.FixedSize(iconDp + 54.dp),
        modifier = Modifier
            .fillMaxHeight()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(items, key = { it.key }) { app ->
            DesktopIcon(
                app = app,
                iconDp = iconDp,
                selected = ui.selected == app.key,
                onSelect = { ui.closeAll(); ui.selected = app.key },
                onOpen = { ui.selected = app.key; onOpen(app) },
                onMenu = { pos -> ui.selected = app.key; onMenu(app, pos) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DesktopIcon(
    app: AppEntry,
    iconDp: Dp,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
    onMenu: (Offset) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val accent = LocalAccent.current
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val shape = RoundedCornerShape(12.dp)
    val bg = when {
        selected -> accent.copy(alpha = 0.24f)
        hovered -> Color.White.copy(alpha = 0.08f)
        else -> Color.Transparent
    }
    Column(
        Modifier
            .width(iconDp + 44.dp)
            .onGloballyPositioned { holder[0] = it }
            .clip(shape)
            .background(bg)
            .border(1.dp, if (selected) accent.copy(alpha = 0.55f) else Color.Transparent, shape)
            .hoverable(source)
            .combinedClickable(
                interactionSource = source,
                indication = null,
                onClick = onSelect,
                onDoubleClick = onOpen,
                onLongClick = { onMenu(holder.centerInRoot()) },
            )
            .contextMenuTrigger(onMenu)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIconImage(app, iconDp)
        Spacer(Modifier.height(6.dp))
        Text(
            app.label,
            color = Nexo.text,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.75f), Offset(0f, 1f), 6f)),
        )
    }
}

// ---------- Widget de reloj ----------

@Composable
private fun ClockWidget(now: LocalDateTime, battery: BatteryInfo, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Column(
        modifier
            .padding(28.dp)
            .width(300.dp)
            .glass(RoundedCornerShape(24.dp), 20.dp, 0.55f)
            .padding(22.dp)
    ) {
        Text(
            greeting(now).uppercase(),
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
        )
        Text(
            formatTime(now, NexoPrefs.clock24),
            color = Nexo.text,
            fontSize = 60.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-1).sp,
        )
        Text(formatDateLong(now), color = Nexo.textDim, fontSize = 14.sp)
        if (battery.level >= 0) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(battery.level / 100f)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(if (battery.level <= 15 && !battery.charging) Nexo.danger else accent)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "${battery.level}%" + if (battery.charging) " · cargando" else "",
                    color = Nexo.textDim,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
