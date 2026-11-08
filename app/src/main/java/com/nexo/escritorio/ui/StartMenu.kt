package com.nexo.escritorio.ui

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexo.escritorio.AppEntry
import com.nexo.escritorio.DesktopActions
import com.nexo.escritorio.NexoPrefs
import java.text.Normalizer
import java.util.Locale
import android.provider.Settings as AndroidSettings

/** Menú de inicio: busca al escribir, apps fijadas y todas las apps. */
@Composable
fun StartMenu(
    apps: List<AppEntry>,
    byKey: Map<String, AppEntry>,
    ui: DesktopUi,
    actions: DesktopActions,
    maxW: Dp,
    maxH: Dp,
    onAppMenu: (AppEntry, Offset) -> Unit,
) {
    var field by remember { mutableStateOf(TextFieldValue(ui.query, TextRange(ui.query.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val query = field.text.trim()
    val results = remember(query, apps) { if (query.isEmpty()) emptyList() else searchApps(apps, query) }
    val pinned = NexoPrefs.taskbarPins.mapNotNull { byKey[it] }

    val open: (AppEntry) -> Unit = { app ->
        ui.closeAll()
        actions.launch(app)
    }
    val launchFirst: () -> Unit = {
        val first = results.firstOrNull()
        if (first != null) {
            open(first)
        } else if (query.isNotEmpty()) {
            ui.closeAll()
            actions.webSearch(query)
        }
    }

    Column(
        Modifier
            .width(minOf(720.dp, maxW))
            .height(minOf(640.dp, maxH))
            .glass(RoundedCornerShape(24.dp), 32.dp, 0.90f)
            .blockPointer()
            .padding(22.dp)
    ) {
        SearchField(
            value = field,
            onChange = {
                field = it
                ui.query = it.text
            },
            focus = focus,
            onEnter = launchFirst,
        )
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (query.isEmpty()) {
                AllApps(pinned, apps, open, onAppMenu)
            } else {
                SearchResults(results, query, open, onAppMenu) {
                    ui.closeAll()
                    actions.webSearch(query)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        StartFooter(ui, actions)
    }
}

@Composable
private fun SearchField(
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    focus: FocusRequester,
    onEnter: () -> Unit,
) {
    val accent = LocalAccent.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = Nexo.text, fontSize = 16.sp),
        cursorBrush = SolidColor(accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onEnter() }),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) {
                    onEnter()
                    true
                } else {
                    false
                }
            },
        decorationBox = { inner ->
            Row(
                Modifier
                    .clip(shape)
                    .background(Nexo.fieldBg)
                    .border(1.5.dp, if (focused) accent.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.06f), shape)
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = if (focused) accent else Nexo.textDim,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (value.text.isEmpty()) {
                        Text("Escribe para buscar apps o en la web…", color = Nexo.textFaint, fontSize = 16.sp)
                    }
                    inner()
                }
            }
        },
    )
}

@Composable
private fun AllApps(
    pinned: List<AppEntry>,
    apps: List<AppEntry>,
    onOpen: (AppEntry) -> Unit,
    onMenu: (AppEntry, Offset) -> Unit,
) {
    if (apps.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Cargando aplicaciones…", color = Nexo.textDim, fontSize = 14.sp)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(100.dp),
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (pinned.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionLabel("Fijadas", Modifier.padding(start = 6.dp, bottom = 6.dp))
            }
            items(pinned, key = { "pin_" + it.key }) { app -> StartTile(app, onOpen, onMenu) }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(12.dp)) }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionLabel("Todas las aplicaciones · ${apps.size}", Modifier.padding(start = 6.dp, bottom = 6.dp))
        }
        items(apps, key = { "all_" + it.key }) { app -> StartTile(app, onOpen, onMenu) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StartTile(app: AppEntry, onOpen: (AppEntry) -> Unit, onMenu: (AppEntry, Offset) -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Column(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { holder[0] = it }
            .clip(RoundedCornerShape(14.dp))
            .background(if (hovered) Nexo.hover else Color.Transparent)
            .hoverable(source)
            .combinedClickable(
                interactionSource = source,
                indication = null,
                onClick = { onOpen(app) },
                onLongClick = { onMenu(app, holder.centerInRoot()) },
            )
            .contextMenuTrigger { pos -> onMenu(app, pos) }
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIconImage(app, 42.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            app.label,
            color = Nexo.text,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResults(
    results: List<AppEntry>,
    query: String,
    onOpen: (AppEntry) -> Unit,
    onMenu: (AppEntry, Offset) -> Unit,
    onWeb: () -> Unit,
) {
    val accent = LocalAccent.current
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item {
            SectionLabel(
                if (results.isEmpty()) "Sin apps que coincidan" else "Mejores resultados",
                Modifier.padding(start = 6.dp, bottom = 8.dp),
            )
        }
        itemsIndexed(results.take(30), key = { _, app -> app.key }) { index, app ->
            val source = remember { MutableInteractionSource() }
            val hovered by source.collectIsHoveredAsState()
            val first = index == 0
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when {
                            first -> accent.copy(alpha = 0.16f)
                            hovered -> Nexo.hover
                            else -> Color.Transparent
                        }
                    )
                    .hoverable(source)
                    .combinedClickable(interactionSource = source, indication = null, onClick = { onOpen(app) })
                    .contextMenuTrigger { pos -> onMenu(app, pos) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIconImage(app, 34.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, color = Nexo.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    Text("Aplicación", color = Nexo.textDim, fontSize = 12.sp)
                }
                if (first) KeyHint("Intro")
            }
        }
        item {
            val source = remember { MutableInteractionSource() }
            val hovered by source.collectIsHoveredAsState()
            Row(
                Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when {
                            results.isEmpty() -> accent.copy(alpha = 0.16f)
                            hovered -> Nexo.hover
                            else -> Color.Transparent
                        }
                    )
                    .hoverable(source)
                    .clickable(interactionSource = source, indication = null, onClick = onWeb)
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Language, null, tint = accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    "Buscar «$query» en la web",
                    color = Nexo.text,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (results.isEmpty()) KeyHint("Intro")
            }
        }
    }
}

@Composable
private fun KeyHint(text: String) {
    Text(
        text,
        color = Nexo.textDim,
        fontSize = 11.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun StartFooter(ui: DesktopUi, actions: DesktopActions) {
    val accent = LocalAccent.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(accent, lerp(accent, Color.White, 0.4f)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Person, null, tint = Nexo.onAccent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Mi escritorio", color = Nexo.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(deviceName(), color = Nexo.textDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FooterButton(Icons.Rounded.Folder, "Archivos") { ui.closeAll(); actions.files() }
        FooterButton(Icons.Rounded.Settings, "Ajustes del teléfono") {
            ui.closeAll()
            actions.open(Intent(AndroidSettings.ACTION_SETTINGS))
        }
        FooterButton(Icons.Rounded.Tune, "Ajustes de Nexo") { ui.openSettings() }
        FooterButton(Icons.Rounded.PowerSettingsNew, "Salir del escritorio") { actions.exit() }
    }
}

@Composable
private fun FooterButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (hovered) Color.White.copy(alpha = 0.10f) else Color.Transparent)
            .hoverable(source)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Nexo.text, modifier = Modifier.size(20.dp))
        if (hovered) HoverTooltip(label)
    }
}

private fun deviceName(): String {
    val maker = Build.MANUFACTURER.replaceFirstChar { it.titlecase(Locale.getDefault()) }
    return if (Build.MODEL.startsWith(Build.MANUFACTURER, ignoreCase = true)) Build.MODEL else "$maker ${Build.MODEL}"
}

private val MARKS = Regex("\\p{Mn}+")

private fun normalize(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(MARKS, "").lowercase(Locale.getDefault())

/** Busca sin importar tildes ni mayúsculas; primero lo que empieza igual. */
fun searchApps(apps: List<AppEntry>, query: String): List<AppEntry> {
    val q = normalize(query)
    return apps.mapNotNull { app ->
        val label = normalize(app.label)
        val score = when {
            label.startsWith(q) -> 0
            label.split(' ', '-', '_').any { it.startsWith(q) } -> 1
            label.contains(q) -> 2
            app.packageName.lowercase(Locale.ROOT).contains(q) -> 3
            else -> -1
        }
        if (score < 0) null else score to app
    }
        .sortedWith(compareBy<Pair<Int, AppEntry>>({ it.first }, { it.second.label.length }))
        .map { it.second }
}
