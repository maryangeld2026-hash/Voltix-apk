package com.nexo.escritorio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.view.Display
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Llenar toda la TV al duplicar la pantalla del teléfono.
 *
 * Los teléfonos son muy alargados (20:9) y las TV y monitores son 16:9, así que al duplicar
 * la TV pone franjas negras. Mientras el escritorio está en el teléfono, Nexo cambia
 * temporalmente el formato de la pantalla del teléfono al de la TV (lo mismo que hace
 * «adb shell wm size») y lo restaura al salir. Así la imagen llena la TV de borde a borde.
 *
 * Necesita uno de estos permisos: el de ajustes que se da una vez por ADB, o Shizuku.
 */
object ScreenFit {
    val labels = listOf("Original", "16:9", "16:10", "21:9")
    private val ratios = listOf(0f, 16f / 9f, 16f / 10f, 21f / 9f)

    fun hasOwnPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun canApply(ctx: Context): Boolean = hasOwnPermission(ctx) || TaskControl.ready.value

    /** Servicio de ventanas de Android: con nuestro permiso o, si no, a través de Shizuku. */
    private fun windowService(ctx: Context): Any? = runCatching {
        if (hasOwnPermission(ctx)) {
            Class.forName("android.view.WindowManagerGlobal").getMethod("getWindowManagerService").invoke(null)
        } else if (TaskControl.ready.value) {
            val binder: IBinder = SystemServiceHelper.getSystemService("window") ?: return null
            Class.forName("android.view.IWindowManager\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, ShizukuBinderWrapper(binder))
        } else {
            null
        }
    }.getOrNull()

    /**
     * Aplica el formato elegido en Ajustes a la pantalla del teléfono.
     * Devuelve true si la pantalla quedó con el nuevo formato.
     */
    fun apply(ctx: Context): Boolean {
        val ratio = ratios.getOrElse(NexoPrefs.mirrorFit) { 0f }
        if (ratio <= 0f) return reset(ctx)
        val d = Displays.byId(ctx, Display.DEFAULT_DISPLAY) ?: return false
        val mode = d.mode
        val w = mode.physicalWidth
        val h = mode.physicalHeight
        if (w <= 0 || h <= 0) return false
        val shortSide = min(w, h)
        val longSide = max(w, h)
        // Se conserva el lado corto y se acorta el largo hasta el formato de la TV.
        var newShort = shortSide
        var newLong = (shortSide * ratio).roundToInt()
        if (newLong > longSide) {
            newLong = longSide
            newShort = (longSide / ratio).roundToInt()
        }
        newShort -= newShort % 2
        newLong -= newLong % 2
        // Si el teléfono ya tiene ese formato, no hace falta cambiar nada.
        if (newLong >= longSide - 4 && newShort >= shortSide - 4) return true
        val (fw, fh) = if (w < h) newShort to newLong else newLong to newShort
        val wms = windowService(ctx) ?: return false
        return runCatching {
            wms.javaClass.getMethod(
                "setForcedDisplaySize",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            ).invoke(wms, Display.DEFAULT_DISPLAY, fw, fh)
            NexoPrefs.fitActive = true
            true
        }.getOrDefault(false)
    }

    /** Devuelve la pantalla del teléfono a su formato original. */
    fun reset(ctx: Context): Boolean {
        if (!NexoPrefs.fitActive) return true
        val wms = windowService(ctx) ?: return false
        return runCatching {
            wms.javaClass.getMethod("clearForcedDisplaySize", Int::class.javaPrimitiveType)
                .invoke(wms, Display.DEFAULT_DISPLAY)
            NexoPrefs.fitActive = false
            true
        }.getOrDefault(false)
    }

    /** Si quedó cambiada (por ejemplo, porque la app se cerró de golpe), se restaura. */
    fun restoreIfLeftover(ctx: Context) {
        if (NexoPrefs.fitActive && DesktopActivity.current?.get() == null) reset(ctx)
    }
}
