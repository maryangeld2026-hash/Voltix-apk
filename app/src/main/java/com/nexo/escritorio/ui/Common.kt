package com.nexo.escritorio.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexo.escritorio.AppEntry
import kotlin.math.roundToInt

// ---------- Menú contextual (clic derecho) ----------

sealed interface MenuEntry
data class MenuAction(
    val label: String,
    val icon: ImageVector? = null,
    val danger: Boolean = false,
    val onClick: () -> Unit,
) : MenuEntry
data object MenuDivider : MenuEntry

data class MenuSpec(val position: Offset, val title: String? = null, val entries: List<MenuEntry>)

/** Llama a [onMenu] con la posición del cursor cuando se hace clic derecho. */
fun Modifier.contextMenuTrigger(onMenu: (Offset) -> Unit): Modifier = composed {
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val callback by rememberUpdatedState(onMenu)
    this
        .onGloballyPositioned { holder[0] = it }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                        val change = event.changes.firstOrNull() ?: continue
                        if (change.isConsumed) continue
                        val c = holder[0]
                        val root = if (c != null && c.isAttached) c.localToRoot(change.position) else change.position
                        event.changes.forEach { it.consume() }
                        callback(root)
                    }
                }
            }
        }
}

/** Centro de un elemento en coordenadas de pantalla (para pulsación larga en pantallas táctiles). */
fun Array<LayoutCoordinates?>.centerInRoot(): Offset {
    val c = this[0] ?: return Offset.Zero
    if (!c.isAttached) return Offset.Zero
    return c.localToRoot(Offset(c.size.width / 2f, c.size.height / 2f))
}

/**
 * Evita que un clic dentro de un panel llegue a lo que hay detrás (y lo cierre).
 * Los botones del panel reciben el clic antes, así que siguen funcionando.
 */
fun Modifier.blockPointer(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
        }
    }
}

@Composable
fun ContextMenuHost(spec: MenuSpec?, onDismiss: () -> Unit) {
    if (spec == null) return
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(spec) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent()
                            if (e.type == PointerEventType.Press) {
                                e.changes.forEach { it.consume() }
                                onDismiss()
                            }
                        }
                    }
                }
        )
        Layout(content = { MenuCard(spec, onDismiss) }) { measurables, constraints ->
            val p = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
            val w = constraints.maxWidth
            val h = constraints.maxHeight
            val margin = 8
            var x = spec.position.x.roundToInt()
            var y = spec.position.y.roundToInt()
            if (x + p.width > w - margin) x = (x - p.width).coerceAtLeast(margin)
            if (y + p.height > h - margin) y = (y - p.height).coerceAtLeast(margin)
            layout(w, h) { p.place(x, y) }
        }
    }
}

@Composable
private fun MenuCard(spec: MenuSpec, onDismiss: () -> Unit) {
    Column(
        Modifier
            .width(250.dp)
            .glass(RoundedCornerShape(14.dp), 18.dp, 0.94f)
            .blockPointer()
            .padding(6.dp)
    ) {
        spec.title?.let {
            Text(
                it,
                color = Nexo.textDim,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        spec.entries.forEach { entry ->
            when (entry) {
                is MenuAction -> MenuRow(entry) {
                    onDismiss()
                    entry.onClick()
                }
                is MenuDivider -> Box(
                    Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                )
            }
        }
    }
}

@Composable
private fun MenuRow(action: MenuAction, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val accent = LocalAccent.current
    val tint = if (action.danger) Nexo.danger else Nexo.text
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(if (hovered) accent.copy(alpha = 0.18f) else Color.Transparent)
            .hoverable(source)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = action.icon
        if (icon != null) {
            Icon(icon, null, tint = if (action.danger) Nexo.danger else Nexo.textDim, modifier = Modifier.size(18.dp))
        } else {
            Spacer(Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(action.label, color = tint, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------- Piezas reutilizables ----------

@Composable
fun AppIconImage(app: AppEntry, size: Dp, modifier: Modifier = Modifier) {
    Image(
        bitmap = app.icon,
        contentDescription = app.label,
        modifier = modifier.size(size),
        filterQuality = FilterQuality.High,
    )
}

/** Botón de opciones excluyentes (por ejemplo: Ventana / Maximizada). */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val accent = LocalAccent.current
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Nexo.fieldBg)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val isSel = i == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSel) accent else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    label,
                    color = if (isSel) Nexo.onAccent else Nexo.textDim,
                    fontSize = 13.sp,
                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Nexo.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = Nexo.textDim, fontSize = 12.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = LocalAccent.current,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = modifier,
    )
}
