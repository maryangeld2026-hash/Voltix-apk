package com.nexo.escritorio

import android.content.Context
import android.os.Build
import android.os.IBinder
import android.view.Display
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Hace que las apps se adapten de verdad a cada dispositivo (TV, monitor, PC con scrcpy):
 *
 * 1. Escala de las apps: Android dibuja las apps según la «densidad» de la pantalla. En una TV 4K
 *    la densidad suele ser baja y todo se ve diminuto; en otras, enorme. Nexo calcula la densidad
 *    ideal para cada pantalla y la aplica mientras el escritorio está abierto (como «wm density»).
 *
 * 2. Apps que solo funcionan en vertical o no se dejan redimensionar: en Android 14+ y con Shizuku,
 *    Nexo activa para esa app la opción del sistema «Relación de aspecto › Pantalla completa»
 *    mientras usas el escritorio, para que llene la ventana en lugar de verse con bordes.
 *
 * Todo se restaura al cerrar el escritorio.
 */
object AppFit {
    /** Escala de las apps: 0 = original, 1 = pequeña, 2 = automática, 3 = grande. */
    val labels = listOf("Original", "Pequeña", "Automática", "Grande")
    const val ORIGINAL = 0

    private const val USER_CURRENT = -2
    private const val ASPECT_UNSET = 0
    private const val ASPECT_FULLSCREEN = 6

    private const val TARGET_SHORT_SIDE_DP = 820f

    // ---------- Escala (densidad) de la pantalla externa ----------

    /** Densidad ideal (dpi) para que las apps se vean bien en esta pantalla. */
    fun recommendedDpi(d: Display): Int {
        val size = Displays.sizeOf(d)
        val shortPx = min(size.x, size.y)
        val factor = when (NexoPrefs.appScale) {
            1 -> 0.85f
            3 -> 1.2f
            else -> 1f
        }
        val density = shortPx / TARGET_SHORT_SIDE_DP * factor
        return (density * 160).roundToInt().coerceIn(120, 640)
    }

    fun canScale(ctx: Context): Boolean = ScreenFit.canApply(ctx)

    /** Aplica la escala elegida a una pantalla externa. */
    fun applyScale(ctx: Context, d: Display): Boolean {
        if (d.displayId == Display.DEFAULT_DISPLAY) return false
        if (NexoPrefs.appScale == ORIGINAL) return resetScale(ctx, d.displayId)
        val wms = ScreenFit.windowService(ctx) ?: return false
        val dpi = recommendedDpi(d)
        // Si ya tiene esa densidad, no se toca nada (evita parpadeos).
        val current = ctx.applicationContext.createDisplayContext(d).resources.displayMetrics.densityDpi
        if (kotlin.math.abs(current - dpi) <= 2) return true
        return runCatching {
            wms.javaClass.getMethod(
                "setForcedDisplayDensityForUser",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            ).invoke(wms, d.displayId, dpi, USER_CURRENT)
            NexoPrefs.scaledDisplays = NexoPrefs.scaledDisplays + d.displayId
            true
        }.getOrDefault(false)
    }

    fun resetScale(ctx: Context, displayId: Int): Boolean {
        if (displayId !in NexoPrefs.scaledDisplays) return true
        val wms = ScreenFit.windowService(ctx) ?: return false
        return runCatching {
            wms.javaClass.getMethod(
                "clearForcedDisplayDensityForUser",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            ).invoke(wms, displayId, USER_CURRENT)
            NexoPrefs.scaledDisplays = NexoPrefs.scaledDisplays - displayId
            true
        }.getOrDefault(false)
    }

    // ---------- Apps en «pantalla completa» (Android 14+, Shizuku) ----------

    val canForceApps: Boolean
        get() = Build.VERSION.SDK_INT >= 34 && TaskControl.ready.value

    private fun packageService(): Any? = runCatching {
        if (!TaskControl.ready.value) return null
        val binder: IBinder = SystemServiceHelper.getSystemService("package") ?: return null
        Class.forName("android.content.pm.IPackageManager\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, ShizukuBinderWrapper(binder))
    }.getOrNull()

    private val userId: Int get() = android.os.Process.myUid() / 100_000

    /**
     * Antes de abrir una app: si no tiene un formato elegido por el usuario, se le pone
     * «pantalla completa» para que se adapte a la ventana. Devuelve true si se cambió.
     */
    fun prepareApp(pkg: String): Boolean {
        if (!canForceApps || !NexoPrefs.forceAppsFit) return false
        if (pkg in NexoPrefs.forcedApps) return true
        val pm = packageService() ?: return false
        return runCatching {
            val cls = pm.javaClass
            val current = cls.getMethod("getUserMinAspectRatio", String::class.java, Int::class.javaPrimitiveType)
                .invoke(pm, pkg, userId) as Int
            // Si el usuario ya eligió un formato para esa app, se respeta.
            if (current != ASPECT_UNSET) return false
            cls.getMethod(
                "setUserMinAspectRatio",
                String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            ).invoke(pm, pkg, userId, ASPECT_FULLSCREEN)
            NexoPrefs.forcedApps = NexoPrefs.forcedApps + pkg
            true
        }.getOrDefault(false)
    }

    /** Devuelve todas las apps a su formato original. */
    fun restoreApps() {
        val list = NexoPrefs.forcedApps
        if (list.isEmpty()) return
        val pm = packageService() ?: return
        val left = list.toMutableSet()
        for (pkg in list) {
            val ok = runCatching {
                pm.javaClass.getMethod(
                    "setUserMinAspectRatio",
                    String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                ).invoke(pm, pkg, userId, ASPECT_UNSET)
                true
            }.getOrDefault(false)
            if (ok) left -= pkg
        }
        NexoPrefs.forcedApps = left
    }

    /** Al cerrar el escritorio (o si quedó algo cambiado tras un cierre inesperado). */
    fun restoreAll(ctx: Context) {
        for (id in NexoPrefs.scaledDisplays) resetScale(ctx, id)
        restoreApps()
    }

    fun restoreIfLeftover(ctx: Context) {
        if (DesktopActivity.current?.get() != null) return
        if (NexoPrefs.scaledDisplays.isNotEmpty() || NexoPrefs.forcedApps.isNotEmpty()) restoreAll(ctx)
    }
}
