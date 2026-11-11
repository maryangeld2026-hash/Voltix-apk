package com.nexo.escritorio.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexo.escritorio.NexoPrefs
import com.nexo.escritorio.currentDisplayId
import kotlinx.coroutines.launch

/** Ventana de ajustes del escritorio: fondo, color, ventanas, iconos y atajos. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsWindow(ui: DesktopUi, maxW: Dp, maxH: Dp) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = LocalAccent.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { NexoPrefs.setCustomWallpaper(context, uri) }
    }
    val pickPhoto = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .pointerInput(Unit) { detectTapGestures { ui.settingsOpen = false } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(minOf(720.dp, maxW - 32.dp))
                .heightIn(max = minOf(660.dp, maxH - 110.dp))
                .glass(RoundedCornerShape(22.dp), 40.dp, 0.95f)
                .blockPointer()
        ) {
            // Barra de título
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 10.dp, top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NexoLogo(20.dp, accent)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Ajustes de Nexo",
                    color = Nexo.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                CloseButton { ui.settingsOpen = false }
            }

            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 22.dp)
            ) {
                Section("Fondo de pantalla") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        WALLPAPERS.forEachIndexed { i, w ->
                            WallThumb(w.name, NexoPrefs.wallpaper == i, onClick = { NexoPrefs.changeWallpaper(i) }) {
                                Wallpaper(i, null, animated = false, modifier = Modifier.fillMaxSize())
                            }
                        }
                        val custom = NexoPrefs.customWallpaper
                        WallThumb(
                            "Tu foto",
                            NexoPrefs.wallpaper == -1,
                            onClick = {
                                if (custom != null && NexoPrefs.wallpaper != -1) NexoPrefs.changeWallpaper(-1) else pickPhoto()
                            },
                        ) {
                            if (custom != null) {
                                Image(custom, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color.White.copy(alpha = 0.06f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Rounded.AddPhotoAlternate, null, tint = Nexo.textDim, modifier = Modifier.size(26.dp))
                                }
                            }
                        }
                    }
                    if (NexoPrefs.customWallpaper != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Elegir otra foto",
                            color = accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { pickPhoto() }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    SwitchRow(
                        "Fondo animado",
                        "Las luces del fondo se mueven muy despacio",
                        NexoPrefs.animatedWallpaper,
                    ) { NexoPrefs.changeAnimated(it) }
                }

                Section("Color de acento") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Nexo.accents.forEachIndexed { i, color ->
                            val selected = NexoPrefs.accent == i
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(3.dp, if (selected) Color.White else Color.Transparent, CircleShape)
                                    .clickable { NexoPrefs.changeAccent(i) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) {
                                    Icon(Icons.Rounded.Check, Nexo.accentNames[i], tint = Nexo.onAccent, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }

                Section("Ventanas") {
                    Text("Cómo se abren las aplicaciones", color = Nexo.textDim, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Segmented(
                        listOf("En ventana", "Toda la pantalla", "Pantalla completa"),
                        NexoPrefs.openMode,
                    ) { NexoPrefs.changeOpenMode(it) }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when (NexoPrefs.openMode) {
                            0 -> "Las apps se abren en ventanas que puedes mover; su tamaño se adapta a la pantalla."
                            2 -> "Las apps llenan toda la pantalla y la barra se esconde (tócala abajo o pulsa la tecla Windows para verla)."
                            else -> "Las apps ocupan todo el ancho y el alto de la pantalla, dejando visible la barra de tareas."
                        },
                        color = Nexo.textFaint,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Consejo: haz clic derecho sobre cualquier app para abrirla en la mitad izquierda o derecha de la pantalla, o en una ventana nueva.",
                        color = Nexo.textFaint,
                        fontSize = 12.sp,
                    )
                }

                Section("Pantalla") {
                    Text("Tamaño de la interfaz", color = Nexo.textDim, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Segmented(listOf("Más pequeño", "Automático", "Más grande"), NexoPrefs.uiSize) {
                        NexoPrefs.changeUiSize(it)
                        com.nexo.escritorio.DesktopActivity.current?.get()?.rebuildForNewSize()
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "En «Automático» Nexo se adapta solo a cada pantalla: más grande en una TV, más compacto en un portátil o en el teléfono.",
                        color = Nexo.textFaint,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    SwitchRow(
                        "Reacomodar ventanas al cambiar de pantalla",
                        "Si cambia la resolución o el tamaño, las ventanas se ajustan solas",
                        NexoPrefs.autoFit,
                    ) { NexoPrefs.changeAutoFit(it) }
                    SwitchRow(
                        "Botones de minimizar, maximizar y cerrar en las ventanas",
                        "Con el asistente de Nexo o con Shizuku (ver la app en el teléfono)",
                        NexoPrefs.windowButtons,
                    ) { NexoPrefs.changeWindowButtons(it) }
                }

                Section("Duplicar el teléfono en una TV") {
                    Text("Formato de la imagen", color = Nexo.textDim, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    Segmented(com.nexo.escritorio.ScreenFit.labels, NexoPrefs.mirrorFit) {
                        NexoPrefs.changeMirrorFit(it)
                        val desk = com.nexo.escritorio.DesktopActivity.current?.get()
                        if (desk != null && desk.currentDisplayId() == android.view.Display.DEFAULT_DISPLAY) {
                            com.nexo.escritorio.ScreenFit.apply(ctx)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Cuando el escritorio está en el teléfono y lo duplicas en una TV, Nexo cambia el formato del teléfono al de la TV para que la imagen la llene de borde a borde, sin franjas. Al salir del escritorio todo vuelve a la normalidad." +
                            if (com.nexo.escritorio.ScreenFit.canApply(ctx)) "" else " Necesita el permiso de ADB o Shizuku (ver la app en el teléfono).",
                        color = Nexo.textFaint,
                        fontSize = 12.sp,
                    )
                }

                Section("Juegos") {
                    SwitchRow(
                        "Abrir los juegos a pantalla completa",
                        "La barra se esconde mientras juegas; tócala abajo o pulsa la tecla Windows para verla",
                        NexoPrefs.gamesFullscreen,
                    ) { NexoPrefs.changeGamesFullscreen(it) }
                    Text(
                        "Los mandos Bluetooth o USB funcionan directamente en los juegos que los admiten.",
                        color = Nexo.textFaint,
                        fontSize = 12.sp,
                    )
                }

                Section("Barra de tareas y teclado") {
                    SwitchRow(
                        "Barra siempre visible",
                        "Queda encima de las apps abiertas. Necesita el permiso «Mostrar sobre otras apps»",
                        NexoPrefs.floatingBar,
                    ) { NexoPrefs.changeFloatingBar(it) }
                    SwitchRow(
                        "Tecla Windows en todas las apps",
                        "Abre el inicio aunque estés dentro de otra app (requiere el asistente de Nexo)",
                        NexoPrefs.globalWinKey,
                    ) { NexoPrefs.changeGlobalWinKey(it) }
                    SwitchRow(
                        "Girar a horizontal en el teléfono",
                        "Cuando el escritorio se muestra en la pantalla del teléfono (modo espejo)",
                        NexoPrefs.phoneLandscape,
                    ) { NexoPrefs.changePhoneLandscape(it) }
                }

                Section("Escritorio") {
                    Text("Tamaño de los iconos", color = Nexo.textDim, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Segmented(listOf("Pequeños", "Normales", "Grandes"), NexoPrefs.iconSize) { NexoPrefs.changeIconSize(it) }
                    Spacer(Modifier.height(8.dp))
                    SwitchRow("Mostrar iconos en el escritorio", null, NexoPrefs.showDesktopIcons) {
                        NexoPrefs.changeShowDesktopIcons(it)
                    }
                    SwitchRow("Reloj de 24 horas", null, NexoPrefs.clock24) { NexoPrefs.changeClock24(it) }
                }

                Section("Atajos de teclado") {
                    Shortcut("Tecla Windows", "Abrir o cerrar el inicio")
                    Shortcut("Escribir en el escritorio", "Buscar apps al instante")
                    Shortcut("Win + A", "Centro de control y notificaciones")
                    Shortcut("Win + E", "Archivos")
                    Shortcut("Win + I", "Ajustes de Nexo")
                    Shortcut("Win + D", "Mostrar el escritorio")
                    Shortcut("Win + T", "Touchpad en el teléfono")
                    Shortcut("Win + ↑ / ↓", "Maximizar / minimizar la ventana")
                    Shortcut("Win + ← / →", "Ajustar la ventana a un lado")
                    Shortcut("Clic derecho / Esc dentro de una app", "Volver atrás")
                    Shortcut("Ctrl + Esc", "Inicio (si tu teclado no tiene tecla Windows)")
                    Shortcut("Esc", "Cerrar menús")
                    Shortcut("Doble clic / Intro", "Abrir un icono del escritorio")
                    Shortcut("Supr", "Quitar el icono seleccionado del escritorio")
                    Shortcut("Clic derecho", "Más opciones en cualquier sitio")
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(top = 20.dp)) {
        SectionLabel(title)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun WallThumb(label: String, selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(14.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(132.dp, 80.dp)
                .clip(shape)
                .border(if (selected) 2.5.dp else 1.dp, if (selected) accent else Color.White.copy(alpha = 0.10f), shape)
                .clickable(onClick = onClick)
        ) {
            content()
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = if (selected) Nexo.text else Nexo.textDim,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun Shortcut(keys: String, description: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            keys,
            color = Nexo.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(horizontal = 9.dp, vertical = 4.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(description, color = Nexo.textDim, fontSize = 13.sp)
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (hovered) Nexo.danger.copy(alpha = 0.85f) else Color.Transparent)
            .hoverable(source)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = Nexo.text, modifier = Modifier.size(20.dp))
    }
}
