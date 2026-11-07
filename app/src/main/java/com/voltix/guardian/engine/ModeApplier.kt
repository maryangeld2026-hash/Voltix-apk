package com.voltix.guardian.engine

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.provider.Settings
import com.voltix.guardian.data.Prefs
import org.json.JSONObject

data class Step(val label: String, val ok: Boolean, val note: String)

/**
 * Aplica y revierte los ajustes de cada modo.
 * Siempre guarda el valor original antes de tocar nada y lo restaura al volver a Normal.
 */
object ModeApplier {

    private enum class Ns { SYSTEM, SECURE, GLOBAL }

    private const val NULL_TOKEN = "<null>"
    private const val SYNC_KEY = "SYNC:master"

    private fun get(cr: ContentResolver, ns: Ns, key: String): String? = try {
        when (ns) {
            Ns.SYSTEM -> Settings.System.getString(cr, key)
            Ns.SECURE -> Settings.Secure.getString(cr, key)
            Ns.GLOBAL -> Settings.Global.getString(cr, key)
        }
    } catch (_: Exception) {
        null
    }

    private fun put(cr: ContentResolver, ns: Ns, key: String, value: String?): Boolean = try {
        when (ns) {
            Ns.SYSTEM -> Settings.System.putString(cr, key, value)
            Ns.SECURE -> Settings.Secure.putString(cr, key, value)
            Ns.GLOBAL -> Settings.Global.putString(cr, key, value)
        }
        true
    } catch (_: Exception) {
        false
    }

    private fun brightnessMax(): Int = try {
        val r = Resources.getSystem()
        val id = r.getIdentifier("config_screenBrightnessSettingMaximum", "integer", "android")
        if (id != 0) r.getInteger(id).coerceAtLeast(100) else 255
    } catch (_: Exception) {
        255
    }

    fun apply(ctx: Context, mode: ModeDef, prefs: Prefs): List<Step> {
        val steps = mutableListOf<Step>()
        // Siempre se parte del estado original: así cambiar de un modo a otro nunca acumula cambios.
        restore(ctx, prefs)
        if (mode.id == "normal") {
            return listOf(Step("Modo Normal", true, "Ajustes originales restaurados"))
        }

        val cr = ctx.contentResolver
        val canSys = Settings.System.canWrite(ctx)
        val canSecure = ctx.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
        val saved = JSONObject(prefs.originalsJson)

        fun remember(ns: Ns, key: String) {
            val k = "${ns.name}:$key"
            if (!saved.has(k)) saved.put(k, get(cr, ns, key) ?: NULL_TOKEN)
        }

        fun needSys(label: String) = Step(label, false, "Falta permiso: Modificar ajustes del sistema")
        fun needSecure(label: String) = Step(label, false, "Falta permiso: ajustes seguros (ADB)")

        // Brillo
        mode.brightnessCapPct?.let { pct ->
            val label = "Tope de brillo $pct%"
            if (!canSys) {
                steps += needSys(label)
            } else {
                val cap = brightnessMax() * pct / 100
                val cur = get(cr, Ns.SYSTEM, "screen_brightness")?.toIntOrNull()
                if (cur != null && cur > cap) {
                    remember(Ns.SYSTEM, "screen_brightness")
                    val ok = put(cr, Ns.SYSTEM, "screen_brightness", cap.toString())
                    steps += Step(label, ok, if (ok) "Brillo bajado" else "El sistema lo rechazó")
                } else {
                    steps += Step(label, true, "Ya estaba por debajo del tope")
                }
            }
        }

        // Tiempo de apagado
        mode.timeoutSec?.let { sec ->
            val label = "Apagado de pantalla a $sec s"
            if (!canSys) {
                steps += needSys(label)
            } else {
                val cur = get(cr, Ns.SYSTEM, "screen_off_timeout")?.toLongOrNull()
                if (cur != null && cur > sec * 1000L) {
                    remember(Ns.SYSTEM, "screen_off_timeout")
                    val ok = put(cr, Ns.SYSTEM, "screen_off_timeout", (sec * 1000L).toString())
                    steps += Step(label, ok, if (ok) "Aplicado" else "El sistema lo rechazó")
                } else {
                    steps += Step(label, true, "Ya lo tenías igual o más corto")
                }
            }
        }

        // Vibración táctil
        if (mode.hapticsOff) {
            val label = "Vibración táctil"
            if (!canSys) {
                steps += needSys(label)
            } else {
                remember(Ns.SYSTEM, "haptic_feedback_enabled")
                val ok = put(cr, Ns.SYSTEM, "haptic_feedback_enabled", "0")
                steps += Step(label, ok, if (ok) "Desactivada" else "El sistema lo rechazó")
            }
        }

        // Frecuencia de refresco (mejor esfuerzo: cada fabricante usa claves distintas)
        mode.refreshHz?.let { hz ->
            val label = "Refresco a $hz Hz"
            var any = false
            if (canSys) {
                remember(Ns.SYSTEM, "peak_refresh_rate")
                remember(Ns.SYSTEM, "min_refresh_rate")
                any = put(cr, Ns.SYSTEM, "peak_refresh_rate", "$hz.0") or any
                any = put(cr, Ns.SYSTEM, "min_refresh_rate", "60.0") or any
            }
            if (canSecure) {
                remember(Ns.SECURE, "miui_refresh_rate")
                remember(Ns.SECURE, "user_refresh_rate")
                any = put(cr, Ns.SECURE, "miui_refresh_rate", hz.toString()) or any
                any = put(cr, Ns.SECURE, "user_refresh_rate", hz.toString()) or any
            }
            steps += when {
                !canSys && !canSecure -> needSecure(label)
                any && canSecure -> Step(label, true, "Aplicado. Verifica en Ajustes > Pantalla")
                any -> Step(label, true, "Aplicado parcialmente. Con ADB se aplica la clave de Xiaomi")
                else -> Step(label, false, "El sistema no aceptó las claves")
            }
        }

        // Animaciones
        mode.animScale?.let { scale ->
            val label = "Animaciones al ${(scale * 100).toInt()}%"
            if (!canSecure) {
                steps += needSecure(label)
            } else {
                var ok = true
                for (k in listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")) {
                    remember(Ns.GLOBAL, k)
                    ok = put(cr, Ns.GLOBAL, k, scale.toString()) && ok
                }
                steps += Step(label, ok, if (ok) "Aplicado" else "El sistema lo rechazó")
            }
        }

        // Ahorro de batería del sistema
        if (mode.lowPower) {
            val label = "Ahorro de batería del sistema"
            if (!canSecure) {
                steps += needSecure(label)
            } else {
                remember(Ns.GLOBAL, "low_power")
                val ok = put(cr, Ns.GLOBAL, "low_power", "1")
                steps += Step(label, ok, if (ok) "Activado" else "El sistema lo rechazó")
            }
        }

        // Sincronización automática (permiso normal)
        if (mode.syncOff) {
            val label = "Sincronización automática"
            try {
                if (!saved.has(SYNC_KEY)) {
                    saved.put(SYNC_KEY, if (ContentResolver.getMasterSyncAutomatically()) "1" else "0")
                }
                ContentResolver.setMasterSyncAutomatically(false)
                steps += Step(label, true, "Pausada")
            } catch (_: Exception) {
                steps += Step(label, false, "El sistema lo rechazó")
            }
        }

        prefs.originalsJson = saved.toString()
        return steps
    }

    /** Devuelve todo a como estaba antes de activar un modo. */
    fun restore(ctx: Context, prefs: Prefs): List<Step> {
        val saved = try {
            JSONObject(prefs.originalsJson)
        } catch (_: Exception) {
            JSONObject()
        }
        if (saved.length() == 0) return emptyList()

        val cr = ctx.contentResolver
        var fails = 0
        val keys = saved.keys().asSequence().toList()
        for (k in keys) {
            val raw = saved.optString(k, NULL_TOKEN)
            if (k == SYNC_KEY) {
                try {
                    ContentResolver.setMasterSyncAutomatically(raw == "1")
                } catch (_: Exception) {
                    fails++
                }
                continue
            }
            val idx = k.indexOf(':')
            if (idx < 0) continue
            val ns = try {
                Ns.valueOf(k.substring(0, idx))
            } catch (_: Exception) {
                continue
            }
            val key = k.substring(idx + 1)
            val value = if (raw == NULL_TOKEN) null else raw
            if (!put(cr, ns, key, value)) fails++
        }
        prefs.originalsJson = "{}"
        return listOf(
            Step(
                "Restauración", fails == 0,
                if (fails == 0) "Ajustes originales restaurados" else "$fails ajustes no se pudieron restaurar"
            )
        )
    }
}
