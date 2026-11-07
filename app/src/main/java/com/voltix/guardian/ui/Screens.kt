package com.voltix.guardian.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voltix.guardian.data.BatterySnapshot
import com.voltix.guardian.engine.Calibration
import com.voltix.guardian.engine.DeviceProfile
import com.voltix.guardian.engine.Launch
import com.voltix.guardian.engine.ModeDef
import com.voltix.guardian.engine.Modes
import com.voltix.guardian.engine.Need
import kotlin.math.roundToInt

private val screenPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp)

// ───────────────────────────── Raíz ─────────────────────────────

@Composable
fun VoltixApp(vm: MainViewModel, requestNotifications: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val ui by vm.ui.collectAsState()
    val host = remember { SnackbarHostState() }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            host.showSnackbar(it)
            vm.clearMessage()
        }
    }

    val tabs = listOf("⚡" to "Inicio", "🎛️" to "Modos", "🔋" to "Carga", "📊" to "Apps", "🔐" to "Permisos")

    ScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(host) },
            bottomBar = {
                NavigationBar(containerColor = Color(0xF20B1224)) {
                    tabs.forEachIndexed { i, (icon, label) ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Text(icon, fontSize = 20.sp) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedTextColor = Neon.cyan,
                                unselectedTextColor = Neon.dim,
                                indicatorColor = Neon.cyan.copy(alpha = 0.18f)
                            )
                        )
                    }
                }
            }
        ) { inner ->
            Box(Modifier.padding(inner).fillMaxSize()) {
                when (tab) {
                    0 -> HomeScreen(vm)
                    1 -> ModesScreen(vm)
                    2 -> ChargeScreen(vm)
                    3 -> AppsScreen(vm)
                    else -> PermissionsScreen(vm, requestNotifications)
                }
            }
        }
    }
}

// ───────────────────────────── Utilidades ─────────────────────────────

private fun fmtHours(h: Float): String {
    val m = (h * 60).toInt()
    return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
}

private fun etaText(s: BatterySnapshot, dev: DeviceProfile): String {
    if (s.currentMa < 30 || s.chargeMah <= 0) return "Calculando autonomía…"
    return if (s.charging) {
        val full = if (s.estFullMah > 0) s.estFullMah else dev.designMah
        val rem = (full - s.chargeMah).coerceAtLeast(0)
        "≈ ${fmtHours(rem.toFloat() / s.currentMa)} para llenarse (estimado)"
    } else {
        "≈ ${fmtHours(s.chargeMah.toFloat() / s.currentMa)} al consumo actual"
    }
}

@Composable
private fun ToggleRow(title: String, desc: String, checked: Boolean, accent: Color = Neon.cyan, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Neon.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(desc, color = Neon.dim, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = accent,
                uncheckedThumbColor = Neon.dim,
                uncheckedTrackColor = Color.White.copy(alpha = 0.08f)
            )
        )
    }
}

@Composable
private fun ActionRow(title: String, desc: String, button: String, accent: Color = Neon.cyan, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Neon.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(desc, color = Neon.dim, fontSize = 12.sp)
        }
        OutlinedButton(onClick = onClick) { Text(button, color = accent, fontSize = 12.sp) }
    }
}

// ───────────────────────────── Inicio ─────────────────────────────

@Composable
fun HomeScreen(vm: MainViewModel) {
    val s by vm.snap.collectAsState()
    val ui by vm.ui.collectAsState()
    val dev = vm.device
    val mode = Modes.byId(ui.modeId)

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = screenPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("VOLTIX", color = Neon.cyan, fontSize = 13.sp, letterSpacing = 6.sp, fontWeight = FontWeight.Bold)
                    Text(dev.title, color = Neon.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Pill("${mode.emoji} ${mode.name}", Color(mode.accent))
            }
        }

        item {
            GlassCard(accent = if (s.charging) Neon.lime else Neon.cyan) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BatteryGauge(level = s.level, charging = s.charging)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${s.source} · ${etaText(s, dev)}",
                    color = Neon.dim, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val tempColor = when {
                    s.tempC >= 42f -> Neon.red
                    s.tempC >= 38f -> Neon.amber
                    else -> Neon.cyan
                }
                StatTile("Temperatura", "%.1f °C".format(s.tempC), if (s.tempC >= ui.heatThreshold) "Alta" else "Normal", tempColor, Modifier.weight(1f))
                StatTile("Corriente", "${s.currentMa} mA", if (s.charging) "Entrando" else "Consumo", Neon.violet, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Voltaje", "%.2f V".format(s.voltageMv / 1000f), "Celda", Neon.lime, Modifier.weight(1f))
                StatTile(
                    "Salud", s.health,
                    if (s.cycles > 0) "${s.cycles} ciclos" else "Ciclos no disponibles",
                    Neon.cyan, Modifier.weight(1f)
                )
            }
        }

        item {
            GlassCard(accent = Neon.violet) {
                Text("Capacidad estimada", color = Neon.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                if (s.estFullMah > 0) {
                    val pct = (s.estFullMah * 100f / dev.designMah).coerceIn(0f, 120f)
                    Text("≈ ${s.estFullMah} mAh de ${dev.designMah} mAh de diseño (${pct.roundToInt()}%)", color = Neon.violet, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (pct / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = Neon.violet,
                        trackColor = Color.White.copy(alpha = 0.08f)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Cálculo a partir del medidor del sistema. Es una referencia: varía con la temperatura y el nivel.",
                        color = Neon.dim, fontSize = 12.sp
                    )
                } else {
                    Text("Se calcula con la batería entre 15% y 99%.", color = Neon.dim, fontSize = 13.sp)
                }
            }
        }

        item {
            GlassCard(accent = Neon.cyan) {
                Text("Últimas 24 h", color = Neon.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (ui.history.size < 3) {
                    Text("Aún no hay datos. Activa el guardián y vuelve en un rato.", color = Neon.dim, fontSize = 13.sp)
                } else {
                    Text("Nivel de batería (%)", color = Neon.dim, fontSize = 11.sp)
                    LineChart(ui.history.map { it.level.toFloat() }, Neon.cyan, minV = 0f, maxV = 100f)
                    Spacer(Modifier.height(10.dp))
                    Text("Temperatura (°C)", color = Neon.dim, fontSize = 11.sp)
                    LineChart(ui.history.map { it.tempC }, Neon.amber)
                }
            }
        }

        item {
            GlassCard(accent = Neon.lime) {
                Text("Tu equipo", color = Neon.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                dev.highlights.forEach { (title, body) ->
                    Text(title, color = Neon.lime, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(body, color = Neon.dim, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        item {
            GlassCard(accent = Neon.cyan) {
                ToggleRow(
                    "Guardián en segundo plano",
                    "Vigila carga, calor y modo automático. Funciona por eventos, sin sondeo constante.",
                    ui.guardian
                ) { vm.setGuardian(it) }
            }
        }
    }
}

// ───────────────────────────── Modos ─────────────────────────────

@Composable
fun ModesScreen(vm: MainViewModel) {
    val ui by vm.ui.collectAsState()
    val ctx = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = screenPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { SectionTitle("Modos de ahorro", "Cada modo muestra exactamente qué restringe y qué permiso necesita.") }

        item {
            GlassCard(accent = Neon.violet) {
                ToggleRow(
                    "🤖 Modo automático inteligente",
                    "Voltix elige el modo por ti según nivel, temperatura, carga y hora.",
                    ui.auto, Neon.violet
                ) { vm.setAuto(it) }
                Spacer(Modifier.height(4.dp))
                Text(
                    "≥70% Normal · 50–69% Moderado · 35–49% Medio · 20–34% Alto · <20% Extremo. " +
                        "Al conectar el cargador vuelve a Normal. Si el equipo pasa de 42 °C o es de noche, sube a Medio como mínimo.",
                    color = Neon.dim, fontSize = 12.sp
                )
                ToggleRow(
                    "🌙 Modo noche (23:00–06:00)",
                    "De noche el automático usa al menos el modo Medio.",
                    ui.nightMode, Neon.violet
                ) { vm.setNightMode(it) }
            }
        }

        items(Modes.all, key = { it.id }) { m ->
            ModeCard(
                mode = m,
                active = ui.modeId == m.id,
                expanded = expanded == m.id,
                writeOk = ui.perms.writeSettings,
                secureOk = ui.perms.secure,
                onToggle = { expanded = if (expanded == m.id) "" else m.id },
                onActivate = { vm.selectMode(m.id) }
            )
        }

        if (ui.report.isNotEmpty()) {
            item {
                GlassCard(accent = Neon.lime) {
                    Text("Resultado de la última activación", color = Neon.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    ui.report.forEach { st ->
                        Row(Modifier.padding(vertical = 2.dp)) {
                            Text(if (st.ok) "✅" else "⚠️", fontSize = 13.sp)
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(st.label, color = Neon.text, fontSize = 13.sp)
                                Text(st.note, color = Neon.dim, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        item {
            GlassCard(accent = Neon.amber) {
                Text("Ajustes que hace mejor el sistema", color = Neon.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Android no deja que una app cambie estas opciones sola. Voltix te lleva directo a cada una.",
                    color = Neon.dim, fontSize = 12.sp
                )
                ActionRow("Pantalla", "Refresco, modo oscuro, pantalla siempre activa (AOD).", "Abrir", Neon.amber) { Launch.display(ctx) }
                ActionRow("Red móvil", "Con señal 5G débil el módem gasta más. Prueba 4G/LTE si estás en esa zona.", "Abrir", Neon.amber) { Launch.network(ctx) }
                ActionRow("Batería del sistema", "Ahorro de batería y protección de carga de HyperOS (si tu versión lo incluye).", "Abrir", Neon.amber) { Launch.batterySettings(ctx) }
            }
        }
    }
}

@Composable
private fun ModeCard(
    mode: ModeDef,
    active: Boolean,
    expanded: Boolean,
    writeOk: Boolean,
    secureOk: Boolean,
    onToggle: () -> Unit,
    onActivate: () -> Unit
) {
    val accent = Color(mode.accent)
    val restrictions = mode.restrictions
    fun granted(n: Need) = when (n) {
        Need.NONE -> true
        Need.WRITE_SETTINGS -> writeOk
        Need.SECURE -> secureOk
    }
    val working = restrictions.count { granted(it.need) }

    GlassCard(accent = accent, onClick = onToggle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(mode.emoji, fontSize = 26.sp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(mode.name, color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(mode.tagline, color = Neon.dim, fontSize = 12.sp)
            }
            if (active) Pill("ACTIVO", accent) else Pill(mode.impact, Neon.dim)
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            restrictions.forEach { r ->
                val ok = granted(r.need)
                Row(Modifier.padding(vertical = 4.dp)) {
                    Text(if (ok) "●" else "○", color = if (ok) accent else Neon.amber, fontSize = 14.sp)
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(r.title, color = Neon.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(r.detail, color = Neon.dim, fontSize = 12.sp)
                        if (!ok) {
                            Text(
                                if (r.need == Need.WRITE_SETTINGS) "Requiere: Modificar ajustes del sistema" else "Requiere: permiso de ajustes seguros (ADB)",
                                color = Neon.amber, fontSize = 11.sp
                            )
                        }
                    }
                }
            }
            if (mode.id != "normal") {
                Spacer(Modifier.height(4.dp))
                Text("$working de ${restrictions.size} funciones activas con tus permisos actuales.", color = Neon.dim, fontSize = 11.sp)
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onActivate,
                enabled = !active,
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (active) "Modo activo" else "Activar ${mode.name}", fontWeight = FontWeight.Bold) }
        }
    }
}

// ───────────────────────────── Carga ─────────────────────────────

@Composable
fun ChargeScreen(vm: MainViewModel) {
    val ui by vm.ui.collectAsState()
    val s by vm.snap.collectAsState()
    val ctx = LocalContext.current
    val dev = vm.device

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = screenPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { SectionTitle("Cuidado de la batería", "Lo que más alarga la vida útil: poco calor y evitar los extremos de carga.") }

        item {
            GlassCard(accent = Neon.lime) {
                Text("Límite de carga", color = Neon.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("${ui.chargeLimit}%", color = Neon.lime, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = ui.chargeLimit.toFloat(),
                    onValueChange = { vm.setChargeLimit(((it / 5f).roundToInt() * 5).coerceIn(60, 100)) },
                    valueRange = 60f..100f,
                    steps = 7,
                    colors = SliderDefaults.colors(
                        thumbColor = Neon.lime,
                        activeTrackColor = Neon.lime,
                        inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                    )
                )
                ToggleRow(
                    "Avisar al llegar al límite",
                    "Una notificación con sonido para que desconectes. Android no permite cortar la carga sin root.",
                    ui.chargeAlerts, Neon.lime
                ) { vm.setChargeAlerts(it) }
                if (!ui.guardian) {
                    Text("⚠️ Requiere el guardián activo (pestaña Inicio).", color = Neon.amber, fontSize = 12.sp)
                }
            }
        }

        item {
            GlassCard(accent = Neon.amber) {
                Text("Alerta de calor", color = Neon.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("${ui.heatThreshold} °C", color = Neon.amber, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = ui.heatThreshold.toFloat(),
                    onValueChange = { vm.setHeatThreshold(it.roundToInt().coerceIn(36, 46)) },
                    valueRange = 36f..46f,
                    steps = 9,
                    colors = SliderDefaults.colors(
                        thumbColor = Neon.amber,
                        activeTrackColor = Neon.amber,
                        inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                    )
                )
                ToggleRow("Avisar si se calienta", "El calor es el mayor enemigo de la vida útil de la batería.", ui.heatAlerts, Neon.amber) { vm.setHeatAlerts(it) }
                Text("Ahora: ${"%.1f".format(s.tempC)} °C", color = Neon.dim, fontSize = 12.sp)
            }
        }

        item {
            GlassCard(accent = Neon.cyan) {
                Text("Reglas que sí funcionan", color = Neon.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                listOf(
                    "🎯 Mantén la carga entre 20% y 80% cuando puedas; evita vivir en 0% o 100%.",
                    "🌡️ Evita cargar y jugar a la vez, y no la dejes al sol o en el coche.",
                    "🔌 Con 120 W el calor sube: para cargar de noche usa un cargador más lento o desconecta al llegar al límite.",
                    "🚫 No necesitas descargar al 0% de forma periódica: en baterías de litio no ayuda."
                ).forEach { Text(it, color = Neon.dim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 3.dp)) }
                if (dev.isTarget) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = { Launch.batterySettings(ctx) }) {
                        Text("Abrir ajustes de batería de HyperOS", color = Neon.cyan, fontSize = 12.sp)
                    }
                }
            }
        }

        item { CalibrationCard(vm) }
    }
}

@Composable
private fun CalibrationCard(vm: MainViewModel) {
    val ui by vm.ui.collectAsState()
    val s by vm.snap.collectAsState()

    GlassCard(accent = Neon.violet) {
        Text("Precisión del medidor", color = Neon.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(
            "La recalibración solo corrige el porcentaje que ves (saltos raros, apagados al 10–20%). No aumenta la capacidad ni la duración. Úsala solo si notas esos síntomas, como mucho cada pocos meses.",
            color = Neon.dim, fontSize = 12.sp
        )
        Spacer(Modifier.height(8.dp))

        when (ui.calibStage) {
            "idle" -> {
                Button(
                    onClick = { vm.startCalibration() },
                    colors = ButtonDefaults.buttonColors(containerColor = Neon.violet, contentColor = Color.White)
                ) { Text("Iniciar asistente") }
            }
            "charge" -> {
                Text("Paso 1 de 3 · Carga completa", color = Neon.violet, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Conecta el cargador y deja cargar hasta 100% con la pantalla apagada y sin usar el móvil. Ahora: ${s.level}%.", color = Neon.text, fontSize = 13.sp)
                LinearProgressIndicator(
                    progress = { s.level / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp),
                    color = Neon.violet, trackColor = Color.White.copy(alpha = 0.08f)
                )
            }
            "hold" -> {
                val elapsed = (s.timeMs - ui.calibSince).coerceAtLeast(0L)
                val left = ((Calibration.HOLD_MS - elapsed) / 60_000L).coerceAtLeast(0L)
                Text("Paso 2 de 3 · Mantener en 100%", color = Neon.violet, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("No lo desconectes. Faltan unos $left min para que el medidor se asiente.", color = Neon.text, fontSize = 13.sp)
                LinearProgressIndicator(
                    progress = { (elapsed.toFloat() / Calibration.HOLD_MS).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp),
                    color = Neon.violet, trackColor = Color.White.copy(alpha = 0.08f)
                )
            }
            "unplug" -> {
                Text("Paso 3 de 3 · Desconectar", color = Neon.violet, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Desconecta el cargador. Después reinicia el teléfono una vez para terminar.", color = Neon.text, fontSize = 13.sp)
            }
            else -> {
                Text("✅ Asistente completado", color = Neon.lime, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Reinicia el teléfono si aún no lo hiciste. Observa tu porcentaje en los próximos días.", color = Neon.dim, fontSize = 13.sp)
            }
        }
        if (ui.calibStage != "idle") {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { vm.cancelCalibration() }) {
                Text(if (ui.calibStage == "done") "Cerrar" else "Cancelar", color = Neon.dim, fontSize = 12.sp)
            }
        }
    }
}

// ───────────────────────────── Apps ─────────────────────────────

@Composable
fun AppsScreen(vm: MainViewModel) {
    val ui by vm.ui.collectAsState()
    val apps by vm.apps.collectAsState()
    val ctx = LocalContext.current

    LaunchedEffect(ui.perms.usage) { vm.loadTopApps() }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = screenPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { SectionTitle("Apps y consumo", "Las apps más usadas en las últimas 24 h y cómo controlarlas en HyperOS.") }

        if (!ui.perms.usage) {
            item {
                GlassCard(accent = Neon.amber) {
                    Text("Falta el permiso de acceso de uso", color = Neon.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Con él Voltix puede decirte qué apps pasan más tiempo en pantalla. No lee su contenido.", color = Neon.dim, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { Launch.usageAccess(ctx) },
                        colors = ButtonDefaults.buttonColors(containerColor = Neon.amber, contentColor = Color.Black)
                    ) { Text("Conceder acceso") }
                }
            }
        } else if (apps.isEmpty()) {
            item { Text("Aún no hay datos suficientes.", color = Neon.dim, fontSize = 13.sp) }
        } else {
            val maxMs = apps.maxOf { it.foregroundMs }.coerceAtLeast(1L)
            items(apps, key = { it.pkg }) { a ->
                GlassCard(accent = Neon.cyan) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(a.label, color = Neon.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("${fmtHours(a.foregroundMs / 3_600_000f)} en pantalla", color = Neon.dim, fontSize = 12.sp)
                        }
                        OutlinedButton(onClick = { Launch.appBattery(ctx, a.pkg, a.label) }) {
                            Text("Controlar", color = Neon.cyan, fontSize = 12.sp)
                        }
                    }
                    LinearProgressIndicator(
                        progress = { a.foregroundMs.toFloat() / maxMs },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(4.dp),
                        color = Neon.cyan, trackColor = Color.White.copy(alpha = 0.08f)
                    )
                }
            }
        }

        item {
            GlassCard(accent = Neon.lime) {
                Text("Regla práctica para cada app", color = Neon.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                listOf(
                    "💬 Mensajería y correo importante → Sin restricciones.",
                    "📱 La mayoría de apps → Ahorro de batería (equilibrado).",
                    "🎮 Juegos y redes que no necesitas en segundo plano → Restringir.",
                    "🚀 Autoinicio: déjalo solo en las apps que deben avisarte al instante."
                ).forEach { Text(it, color = Neon.dim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 3.dp)) }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { Launch.xiaomiAutostart(ctx) }) {
                    Text("Abrir Autoinicio de HyperOS", color = Neon.lime, fontSize = 12.sp)
                }
            }
        }
    }
}

// ───────────────────────────── Permisos ─────────────────────────────

@Composable
fun PermissionsScreen(vm: MainViewModel, requestNotifications: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val ctx = LocalContext.current
    val p = ui.perms

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = screenPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { SectionTitle("Permisos", "Voltix solo pide lo que necesita. Aquí ves qué desbloquea cada uno.") }

        item {
            PermCard("🔔 Notificaciones", "Alertas de límite de carga, calor y cambios de modo.", p.notifications, "Conceder") { requestNotifications() }
        }
        item {
            PermCard(
                "⚙️ Modificar ajustes del sistema",
                "Desbloquea: tope de brillo, tiempo de apagado de pantalla y vibración táctil.",
                p.writeSettings, "Conceder"
            ) { Launch.writeSettings(ctx) }
        }
        item {
            PermCard("📊 Acceso de uso", "Desbloquea: ranking de apps por tiempo en pantalla.", p.usage, "Conceder") { Launch.usageAccess(ctx) }
        }
        item {
            PermCard(
                "🛡️ Sin optimización de batería",
                "Evita que HyperOS cierre el guardián de Voltix. Recomendado.",
                p.batteryOpt, "Conceder"
            ) { Launch.ignoreBatteryOpt(ctx) }
        }
        item {
            GlassCard(accent = Neon.amber) {
                Text("🚀 Autoinicio de Voltix", color = Neon.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Xiaomi cierra apps agresivamente. Activa Autoinicio y pon 'Sin restricciones' en el ahorro de batería de Voltix para que el guardián sobreviva.",
                    color = Neon.dim, fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { Launch.xiaomiAutostart(ctx) }) { Text("Autoinicio", color = Neon.amber, fontSize = 12.sp) }
                    OutlinedButton(onClick = { Launch.appBattery(ctx, ctx.packageName, "Voltix") }) { Text("Ahorro de batería", color = Neon.amber, fontSize = 12.sp) }
                }
            }
        }
        item {
            GlassCard(accent = if (p.secure) Neon.lime else Neon.violet) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔑 Ajustes seguros (una sola vez)", color = Neon.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Pill(if (p.secure) "CONCEDIDO" else "PENDIENTE", if (p.secure) Neon.lime else Neon.amber)
                }
                Text(
                    "Desbloquea: refresco de pantalla a 90/60 Hz, animaciones reducidas y ahorro de batería del sistema. Android solo permite concederlo por ADB desde un PC.",
                    color = Neon.dim, fontSize = 12.sp
                )
                if (!p.secure) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "1) Ajustes > Acerca del teléfono: toca 'Versión de HyperOS' 7 veces.\n" +
                            "2) Opciones de desarrollador: activa 'Depuración USB' y 'Depuración USB (ajustes de seguridad)'.\n" +
                            "3) Conecta al PC y ejecuta:",
                        color = Neon.text, fontSize = 12.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        vm.adbCommand(),
                        color = Neon.cyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth().padding(8.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { vm.copyAdb() },
                            colors = ButtonDefaults.buttonColors(containerColor = Neon.violet, contentColor = Color.White)
                        ) { Text("Copiar comando", fontSize = 12.sp) }
                        OutlinedButton(onClick = { Launch.developerOptions(ctx) }) { Text("Opciones de desarrollador", color = Neon.violet, fontSize = 12.sp) }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Tras ejecutarlo, vuelve aquí: el estado se actualiza solo.", color = Neon.dim, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun PermCard(title: String, desc: String, granted: Boolean, button: String, onClick: () -> Unit) {
    GlassCard(accent = if (granted) Neon.lime else Neon.amber) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                Text(title, color = Neon.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(desc, color = Neon.dim, fontSize = 12.sp)
            }
            if (granted) {
                Pill("OK", Neon.lime)
            } else {
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Neon.amber, contentColor = Color.Black)
                ) { Text(button, fontSize = 12.sp) }
            }
        }
    }
}
