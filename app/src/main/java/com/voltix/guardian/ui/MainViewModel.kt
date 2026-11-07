package com.voltix.guardian.ui

import android.app.Application
import android.app.usage.UsageStatsManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voltix.guardian.data.BatteryReader
import com.voltix.guardian.data.BatterySnapshot
import com.voltix.guardian.data.History
import com.voltix.guardian.data.Prefs
import com.voltix.guardian.data.Sample
import com.voltix.guardian.engine.AutoEngine
import com.voltix.guardian.engine.Calibration
import com.voltix.guardian.engine.DeviceProfiles
import com.voltix.guardian.engine.ModeApplier
import com.voltix.guardian.engine.Modes
import com.voltix.guardian.engine.PermState
import com.voltix.guardian.engine.Perms
import com.voltix.guardian.engine.Step
import com.voltix.guardian.service.GuardianService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppUsage(val pkg: String, val label: String, val foregroundMs: Long, val isSystem: Boolean)

data class UiState(
    val modeId: String = "normal",
    val auto: Boolean = true,
    val guardian: Boolean = false,
    val chargeLimit: Int = 80,
    val chargeAlerts: Boolean = true,
    val heatAlerts: Boolean = true,
    val heatThreshold: Int = 41,
    val nightMode: Boolean = true,
    val calibStage: String = "idle",
    val calibSince: Long = 0L,
    val perms: PermState = PermState(),
    val report: List<Step> = emptyList(),
    val history: List<Sample> = emptyList(),
    val message: String? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Context = app.applicationContext
    private val prefs = Prefs(ctx)
    private val historyStore = History(ctx)

    val device = DeviceProfiles.detect()

    private val _snap = MutableStateFlow(BatteryReader.read(ctx))
    val snap: StateFlow<BatterySnapshot> = _snap.asStateFlow()

    private val _apps = MutableStateFlow<List<AppUsage>>(emptyList())
    val apps: StateFlow<List<AppUsage>> = _apps.asStateFlow()

    private val _ui = MutableStateFlow(readUi())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private fun readUi(): UiState = UiState(
        modeId = prefs.modeId,
        auto = prefs.autoEnabled,
        guardian = prefs.guardianEnabled,
        chargeLimit = prefs.chargeLimit,
        chargeAlerts = prefs.chargeAlerts,
        heatAlerts = prefs.heatAlerts,
        heatThreshold = prefs.heatThresholdC,
        nightMode = prefs.nightMode,
        calibStage = prefs.calibStage,
        calibSince = prefs.calibSince,
        perms = Perms.read(ctx),
        history = historyStore.last24h()
    )

    /** Se llama cada pocos segundos mientras la app está visible. */
    fun refresh() {
        val s = BatteryReader.read(ctx)
        _snap.value = s
        Calibration.onSnapshot(prefs, s)
        // Si el guardián no está corriendo, el modo automático se evalúa al abrir la app.
        if (prefs.autoEnabled && !prefs.guardianEnabled) {
            AutoEngine.tick(ctx, prefs, s)
        }
        if (s.timeMs - prefs.lastHistory >= 5 * 60_000L) {
            historyStore.append(s)
            prefs.lastHistory = s.timeMs
        }
        _ui.update {
            it.copy(
                modeId = prefs.modeId,
                calibStage = prefs.calibStage,
                calibSince = prefs.calibSince,
                history = historyStore.last24h()
            )
        }
    }

    fun refreshPerms() {
        _ui.update { it.copy(perms = Perms.read(ctx)) }
        loadTopApps()
    }

    fun clearMessage() = _ui.update { it.copy(message = null) }

    fun selectMode(id: String) {
        val mode = Modes.byId(id)
        // Elegir un modo a mano desactiva el automático para que Voltix no lo pise.
        prefs.autoEnabled = false
        prefs.modeId = id
        viewModelScope.launch(Dispatchers.IO) {
            val steps = ModeApplier.apply(ctx, mode, prefs)
            val failed = steps.count { !it.ok }
            _ui.update {
                it.copy(
                    modeId = id,
                    auto = false,
                    report = steps,
                    message = if (failed == 0) "Modo ${mode.name} activado"
                    else "Modo ${mode.name}: $failed función(es) sin aplicar. Revisa los permisos."
                )
            }
        }
    }

    fun setAuto(on: Boolean) {
        prefs.autoEnabled = on
        _ui.update {
            it.copy(auto = on, message = if (on) "Modo automático activado" else "Modo automático desactivado")
        }
        if (on) {
            viewModelScope.launch(Dispatchers.IO) {
                AutoEngine.tick(ctx, prefs, BatteryReader.read(ctx))
                _ui.update { it.copy(modeId = prefs.modeId) }
            }
        }
    }

    fun setGuardian(on: Boolean) {
        prefs.guardianEnabled = on
        try {
            if (on) GuardianService.start(ctx) else GuardianService.stop(ctx)
            _ui.update { it.copy(guardian = on, message = if (on) "Guardián activado" else "Guardián detenido") }
        } catch (e: Exception) {
            prefs.guardianEnabled = false
            _ui.update { it.copy(guardian = false, message = "No se pudo iniciar el guardián. Abre la app y reintenta.") }
        }
    }

    fun setChargeLimit(v: Int) {
        prefs.chargeLimit = v
        _ui.update { it.copy(chargeLimit = v) }
    }

    fun setChargeAlerts(on: Boolean) {
        prefs.chargeAlerts = on
        _ui.update { it.copy(chargeAlerts = on) }
    }

    fun setHeatAlerts(on: Boolean) {
        prefs.heatAlerts = on
        _ui.update { it.copy(heatAlerts = on) }
    }

    fun setHeatThreshold(v: Int) {
        prefs.heatThresholdC = v
        _ui.update { it.copy(heatThreshold = v) }
    }

    fun setNightMode(on: Boolean) {
        prefs.nightMode = on
        _ui.update { it.copy(nightMode = on) }
    }

    fun startCalibration() {
        Calibration.start(prefs)
        _ui.update { it.copy(calibStage = prefs.calibStage, calibSince = prefs.calibSince) }
    }

    fun cancelCalibration() {
        Calibration.cancel(prefs)
        _ui.update { it.copy(calibStage = "idle") }
    }

    fun copyAdb() {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("adb", Perms.adbCommand(ctx)))
        _ui.update { it.copy(message = "Comando copiado") }
    }

    fun adbCommand(): String = Perms.adbCommand(ctx)

    fun loadTopApps() {
        if (!Perms.usage(ctx)) {
            _apps.value = emptyList()
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val end = System.currentTimeMillis()
                val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, end - 24L * 3600_000L, end)
                    ?: emptyList()
                val pm = ctx.packageManager
                val list = stats
                    .filter { it.totalTimeInForeground > 60_000L && it.packageName != ctx.packageName }
                    .groupBy { it.packageName }
                    .map { (p, l) -> p to l.sumOf { it.totalTimeInForeground } }
                    .sortedByDescending { it.second }
                    .take(10)
                    .mapNotNull { (p, t) ->
                        try {
                            val ai = pm.getApplicationInfo(p, 0)
                            AppUsage(
                                p,
                                pm.getApplicationLabel(ai).toString(),
                                t,
                                (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }
                _apps.value = list
            } catch (_: Exception) {
                _apps.value = emptyList()
            }
        }
    }
}
