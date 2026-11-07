package com.voltix.guardian.engine

import android.content.Context
import com.voltix.guardian.data.BatterySnapshot
import com.voltix.guardian.data.Prefs
import java.util.Calendar

/** Elige el modo según nivel, temperatura, carga y hora. Función pura: fácil de razonar y probar. */
object AutoPolicy {

    fun decide(s: BatterySnapshot, hour: Int, nightMode: Boolean, nightStart: Int, nightEnd: Int): String {
        if (s.charging) return "normal"

        var idx = when {
            s.level >= 70 -> 0 // normal
            s.level >= 50 -> 1 // moderado
            s.level >= 35 -> 2 // medio
            s.level >= 20 -> 3 // alto
            else -> 4          // extremo
        }

        // Si el equipo está caliente, reducir carga de trabajo ayuda a enfriarlo.
        if (s.tempC >= 42f) idx = maxOf(idx, 2)

        // De noche no hace falta máximo rendimiento.
        if (nightMode && isNight(hour, nightStart, nightEnd)) idx = maxOf(idx, 2)

        return Modes.all[idx.coerceIn(0, Modes.all.size - 1)].id
    }

    fun isNight(hour: Int, start: Int, end: Int): Boolean =
        if (start <= end) hour in start until end else (hour >= start || hour < end)
}

object AutoEngine {
    private const val MIN_DWELL_MS = 10 * 60_000L

    /**
     * Evalúa y, si corresponde, cambia de modo. Devuelve el nombre del nuevo modo o null si no cambió.
     * Histéresis: subir de severidad es inmediato; relajar espera 10 min (salvo al conectar el cargador).
     */
    fun tick(ctx: Context, prefs: Prefs, s: BatterySnapshot): String? {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val target = AutoPolicy.decide(s, hour, prefs.nightMode, prefs.nightStart, prefs.nightEnd)
        val cur = prefs.modeId
        if (target == cur) return null

        val severer = Modes.indexOf(target) > Modes.indexOf(cur)
        val now = System.currentTimeMillis()
        val relaxNow = target == "normal" && s.charging
        if (!severer && !relaxNow && now - prefs.lastAutoChange < MIN_DWELL_MS) return null

        prefs.modeId = target
        prefs.lastAutoChange = now
        ModeApplier.apply(ctx, Modes.byId(target), prefs)
        return Modes.byId(target).name
    }
}

/** Asistente de recalibración del medidor: una carga completa e ininterrumpida, sin descargas forzadas. */
object Calibration {
    const val HOLD_MS = 30 * 60_000L

    fun start(p: Prefs) {
        p.calibStage = "charge"
        p.calibSince = System.currentTimeMillis()
    }

    fun cancel(p: Prefs) {
        p.calibStage = "idle"
    }

    fun onSnapshot(p: Prefs, s: BatterySnapshot) {
        when (p.calibStage) {
            "charge" -> if (s.charging && s.level >= 100) {
                p.calibStage = "hold"
                p.calibSince = s.timeMs
            }
            "hold" -> if (!s.charging) {
                p.calibStage = "charge"
            } else if (s.timeMs - p.calibSince >= HOLD_MS) {
                p.calibStage = "unplug"
            }
            "unplug" -> if (!s.charging) {
                p.calibStage = "done"
            }
        }
    }
}
