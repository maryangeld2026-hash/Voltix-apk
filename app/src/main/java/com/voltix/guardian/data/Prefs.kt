package com.voltix.guardian.data

import android.content.Context
import android.content.SharedPreferences

class Prefs(ctx: Context) {
    private val sp: SharedPreferences =
        ctx.applicationContext.getSharedPreferences("voltix", Context.MODE_PRIVATE)

    var modeId: String
        get() = sp.getString("mode", "normal") ?: "normal"
        set(v) = sp.edit().putString("mode", v).apply()

    var autoEnabled: Boolean
        get() = sp.getBoolean("auto", true)
        set(v) = sp.edit().putBoolean("auto", v).apply()

    var guardianEnabled: Boolean
        get() = sp.getBoolean("guardian", false)
        set(v) = sp.edit().putBoolean("guardian", v).apply()

    var chargeLimit: Int
        get() = sp.getInt("charge_limit", 80)
        set(v) = sp.edit().putInt("charge_limit", v).apply()

    var chargeAlerts: Boolean
        get() = sp.getBoolean("charge_alerts", true)
        set(v) = sp.edit().putBoolean("charge_alerts", v).apply()

    var heatAlerts: Boolean
        get() = sp.getBoolean("heat_alerts", true)
        set(v) = sp.edit().putBoolean("heat_alerts", v).apply()

    var heatThresholdC: Int
        get() = sp.getInt("heat_threshold", 41)
        set(v) = sp.edit().putInt("heat_threshold", v).apply()

    var nightMode: Boolean
        get() = sp.getBoolean("night_mode", true)
        set(v) = sp.edit().putBoolean("night_mode", v).apply()

    var nightStart: Int
        get() = sp.getInt("night_start", 23)
        set(v) = sp.edit().putInt("night_start", v).apply()

    var nightEnd: Int
        get() = sp.getInt("night_end", 6)
        set(v) = sp.edit().putInt("night_end", v).apply()

    var lastAutoChange: Long
        get() = sp.getLong("last_auto_change", 0L)
        set(v) = sp.edit().putLong("last_auto_change", v).apply()

    var originalsJson: String
        get() = sp.getString("originals", "{}") ?: "{}"
        set(v) = sp.edit().putString("originals", v).apply()

    var limitAlerted: Boolean
        get() = sp.getBoolean("limit_alerted", false)
        set(v) = sp.edit().putBoolean("limit_alerted", v).apply()

    var lastHeatAlert: Long
        get() = sp.getLong("last_heat_alert", 0L)
        set(v) = sp.edit().putLong("last_heat_alert", v).apply()

    var lastHistory: Long
        get() = sp.getLong("last_history", 0L)
        set(v) = sp.edit().putLong("last_history", v).apply()

    /** idle | charge | hold | unplug | done */
    var calibStage: String
        get() = sp.getString("calib_stage", "idle") ?: "idle"
        set(v) = sp.edit().putString("calib_stage", v).apply()

    var calibSince: Long
        get() = sp.getLong("calib_since", 0L)
        set(v) = sp.edit().putLong("calib_since", v).apply()
}
