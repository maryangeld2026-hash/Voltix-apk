package com.nexo.escritorio

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.nexo.escritorio.ui.BarChrome
import com.nexo.escritorio.ui.DesktopUi
import com.nexo.escritorio.ui.NexoTheme
import com.nexo.escritorio.ui.TASKBAR_RESERVE_DP
import com.nexo.escritorio.ui.accentColor
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Ciclo de vida propio para la capa: sigue activa aunque el escritorio quede detrás de las apps. */
private class OverlayLifecycle : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

    fun start() {
        controller.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        if (registry.currentState != Lifecycle.State.INITIALIZED) {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}

/**
 * La barra de tareas, el inicio y el centro de control en una capa por encima de todas
 * las apps (como en Windows). Necesita el permiso «Mostrar sobre otras apps».
 * Sin permiso, el escritorio dibuja la barra dentro de su propia pantalla.
 */
class BarOverlay(
    private val ctx: Context,
    private val ui: DesktopUi,
    private val actions: DesktopActions,
) {
    private val owner = OverlayLifecycle()
    private val scope = MainScope()
    private var view: ComposeView? = null
    private var wm: WindowManager? = null
    private var params: WindowManager.LayoutParams? = null
    private var screenH = 0

    /** true cuando la capa ocupa toda la pantalla (inicio o panel abiertos). */
    var expanded by mutableStateOf(false)
        private set

    val attached: Boolean get() = view != null

    companion object {
        fun canUse(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)
    }

    fun attach(display: Display): Boolean {
        if (view != null) return true
        if (!canUse(ctx)) return false
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        return try {
            val displayCtx = ctx.applicationContext.createDisplayContext(display)
            val windowCtx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                displayCtx.createWindowContext(type, null)
            } else {
                displayCtx
            }
            val manager = windowCtx.getSystemService(WindowManager::class.java)
            val density = windowCtx.resources.displayMetrics.density
            screenH = Displays.sizeOf(display).y
            val stripPx = (TASKBAR_RESERVE_DP * density).toInt()

            val compose = ComposeView(windowCtx).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent {
                    NexoTheme(accentColor(NexoPrefs.accent)) {
                        BarChrome(
                            actions = actions,
                            ui = ui,
                            expanded = expanded,
                            menuOffsetY = if (expanded) 0f else (screenH - stripPx).toFloat(),
                        )
                    }
                }
            }
            owner.start()
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                stripPx,
                type,
                collapsedFlags,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.BOTTOM
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                title = "Nexo barra"
            }
            manager.addView(compose, lp)
            view = compose
            wm = manager
            params = lp

            scope.launch {
                snapshotFlow { Pair(ui.startOpen || ui.panelOpen || ui.overlayMenu != null, ui.barHidden) }
                    .collect { (open, hidden) -> resize(open, hidden, density, stripPx) }
            }
            true
        } catch (e: Throwable) {
            owner.stop()
            view = null
            false
        }
    }

    private val collapsedFlags =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

    private val expandedFlags =
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    private fun resize(open: Boolean, hidden: Boolean, density: Float, stripPx: Int) {
        val v = view ?: return
        val lp = params ?: return
        val manager = wm ?: return
        when {
            open -> {
                lp.height = WindowManager.LayoutParams.MATCH_PARENT
                lp.flags = expandedFlags
            }
            hidden -> {
                lp.height = (6 * density).toInt()
                lp.flags = collapsedFlags
            }
            else -> {
                lp.height = stripPx
                lp.flags = collapsedFlags
            }
        }
        // Primero se agranda la ventana y después se dibuja el menú, para que no se vea cortado.
        runCatching { manager.updateViewLayout(v, lp) }
        expanded = open
    }

    fun detach() {
        scope.cancel()
        view?.let { v -> runCatching { wm?.removeViewImmediate(v) } }
        view = null
        owner.stop()
    }
}
