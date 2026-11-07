package com.voltix.guardian.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.voltix.guardian.data.BatteryReader
import com.voltix.guardian.data.History
import com.voltix.guardian.data.Prefs
import com.voltix.guardian.engine.AutoEngine
import com.voltix.guardian.engine.Calibration
import com.voltix.guardian.engine.Modes

/**
 * Servicio en primer plano, dirigido por eventos: solo despierta cuando Android avisa de un cambio
 * de batería (nivel, temperatura, conexión). No hace sondeo continuo, así que su consumo es mínimo.
 */
class GuardianService : Service() {

    private lateinit var prefs: Prefs
    private lateinit var history: History
    private var lastText = ""

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handle()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        history = History(this)
        Notifier.ensureChannels(this)

        val n = Notifier.foreground(this, "Voltix activo", "Vigilando tu batería")
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(Notifier.ID_FG, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(Notifier.ID_FG, n)
            }
        } catch (_: Exception) {
            stopSelf()
            return
        }

        ContextCompat.registerReceiver(
            this, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        handle()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        try {
            unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    private fun handle() {
        val s = BatteryReader.read(this)
        val now = s.timeMs

        Calibration.onSnapshot(prefs, s)

        if (now - prefs.lastHistory >= 5 * 60_000L) {
            history.append(s)
            prefs.lastHistory = now
        }

        // Límite de carga
        if (s.charging) {
            if (prefs.chargeAlerts && !prefs.limitAlerted && s.level >= prefs.chargeLimit) {
                prefs.limitAlerted = true
                Notifier.alert(
                    this, Notifier.ID_CHARGE,
                    "🔋 Llegó al ${prefs.chargeLimit}%",
                    "Desconecta el cargador para cuidar la batería. Con 120 W este nivel se alcanza en pocos minutos."
                )
            }
        } else {
            prefs.limitAlerted = false
        }

        // Calor
        if (prefs.heatAlerts && s.tempC >= prefs.heatThresholdC && now - prefs.lastHeatAlert > 10 * 60_000L) {
            prefs.lastHeatAlert = now
            val extra = if (s.charging) {
                "Deja de usar el teléfono mientras carga o desconéctalo un rato."
            } else {
                "Cierra apps pesadas, evita el sol directo y baja el brillo."
            }
            Notifier.alert(
                this, Notifier.ID_HEAT,
                "🌡️ Batería a ${"%.1f".format(s.tempC)} °C",
                "El calor es el mayor enemigo de la batería. $extra"
            )
        }

        // Modo automático
        if (prefs.autoEnabled) {
            val changed = AutoEngine.tick(this, prefs, s)
            if (changed != null) {
                Notifier.alert(this, Notifier.ID_INFO, "Voltix cambió a modo $changed", "Batería al ${s.level}%.")
            }
        }

        val mode = Modes.byId(prefs.modeId)
        val text = "${s.level}%  ·  ${mode.emoji} ${mode.name}" + if (s.charging) "  ·  ⚡ cargando" else ""
        if (text != lastText) {
            lastText = text
            Notifier.update(this, "Voltix activo", text)
        }
    }

    companion object {
        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, GuardianService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, GuardianService::class.java))
        }
    }
}
