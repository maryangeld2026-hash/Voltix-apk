package com.nexo.escritorio.ui

import android.content.Context
import android.view.Display
import com.nexo.escritorio.Displays
import com.nexo.escritorio.NexoPrefs
import kotlin.math.min

/**
 * Tamaño automático de la interfaz según la pantalla.
 * Nexo se diseña para una pantalla de unos 820 dp de alto: en una TV grande todo se
 * agranda para verse bien desde el sofá; en un portátil o en el teléfono se ajusta para caber.
 */
object UiScale {
    private const val TARGET_SHORT_SIDE_DP = 820f

    fun auto(widthPx: Int, heightPx: Int, density: Float, isPhone: Boolean): Float {
        if (widthPx <= 0 || heightPx <= 0 || density <= 0f) return 1f
        val shortDp = min(widthPx, heightPx) / density
        val base = (shortDp / TARGET_SHORT_SIDE_DP).coerceIn(if (isPhone) 0.8f else 0.7f, 2.2f)
        val user = when (NexoPrefs.uiSize) {
            0 -> 0.85f
            2 -> 1.2f
            else -> 1f
        }
        return base * user
    }

    fun forDisplay(ctx: Context, d: Display): Float {
        val size = Displays.sizeOf(d)
        val density = ctx.applicationContext.createDisplayContext(d).resources.displayMetrics.density
        return auto(size.x, size.y, density, d.displayId == Display.DEFAULT_DISPLAY)
    }
}
