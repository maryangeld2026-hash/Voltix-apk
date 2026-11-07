package com.voltix.guardian.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlin.math.abs

data class BatterySnapshot(
    val level: Int,
    val charging: Boolean,
    val source: String,
    val tempC: Float,
    val voltageMv: Int,
    /** Magnitud de la corriente en mA (siempre positiva). */
    val currentMa: Int,
    val health: String,
    val cycles: Int,
    /** Carga restante segun el medidor, en mAh (-1 si no disponible). */
    val chargeMah: Int,
    /** Capacidad total estimada = carga restante / nivel (-1 si no hay datos fiables). */
    val estFullMah: Int,
    val timeMs: Long = System.currentTimeMillis()
)

object BatteryReader {

    fun read(ctx: Context): BatterySnapshot {
        val app = ctx.applicationContext
        val i: Intent? = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val bm = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        val rawLevel = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val level = if (rawLevel >= 0 && scale > 0) {
            rawLevel * 100 / scale
        } else {
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        }

        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val source = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "Cargador"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Inalámbrica"
            else -> "Batería"
        }

        val tempC = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val voltage = i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0

        val healthCode = i?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0) ?: 0
        val health = when (healthCode) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Buena"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Sobrecalentada"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Degradada"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Sobrevoltaje"
            BatteryManager.BATTERY_HEALTH_COLD -> "Fría"
            else -> "Sin datos"
        }

        // EXTRA_CYCLE_COUNT existe desde Android 14; se lee por nombre para no depender del SDK.
        val cycles = i?.getIntExtra("android.os.extra.CYCLE_COUNT", -1) ?: -1

        val rawCurrent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentMa = when {
            rawCurrent == Int.MIN_VALUE -> 0
            abs(rawCurrent) < 10_000 -> abs(rawCurrent) // algunos equipos reportan en mA
            else -> abs(rawCurrent) / 1000               // lo normal: microamperios
        }

        val rawCounter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val chargeMah = when {
            rawCounter <= 0 -> -1
            rawCounter > 20_000 -> rawCounter / 1000      // microamperios-hora
            else -> rawCounter
        }
        val estFull = if (chargeMah > 0 && level in 15..99) chargeMah * 100 / level else -1

        return BatterySnapshot(
            level = level,
            charging = charging,
            source = source,
            tempC = tempC,
            voltageMv = voltage,
            currentMa = currentMa,
            health = health,
            cycles = cycles,
            chargeMah = chargeMah,
            estFullMah = estFull
        )
    }
}
