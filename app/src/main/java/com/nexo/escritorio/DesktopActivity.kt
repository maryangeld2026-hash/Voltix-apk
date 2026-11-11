package com.nexo.escritorio

import android.content.Intent
import android.content.pm.ActivityInfo
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.nexo.escritorio.ui.DesktopRoot
import com.nexo.escritorio.ui.DesktopUi
import java.lang.ref.WeakReference

/** El escritorio completo: en el monitor, en la TV o en la propia pantalla del teléfono. */
class DesktopActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ACTION = "nexo_action"
        const val ACTION_START = "start"
        const val ACTION_PANEL = "panel"
        const val ACTION_SETTINGS = "settings"
        var current: WeakReference<DesktopActivity>? = null
        /** Último intento de agrandar el escritorio (evita repetirlo en bucle). */
        private var lastFixAttempt = 0L
    }

    val ui = DesktopUi()
    private lateinit var actions: DesktopActions
    private var overlay: BarOverlay? = null
    private var homeDisplay = Display.DEFAULT_DISPLAY
    private var controls: WindowControls? = null
    private var lastW = 0
    private var lastH = 0
    private var lastUiSize = NexoPrefs.uiSize

    // Si se desconecta la pantalla, el escritorio se cierra en lugar de pasarse al teléfono.
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayRemoved(displayId: Int) {
            if (displayId == homeDisplay && homeDisplay != Display.DEFAULT_DISPLAY) finish()
        }
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == homeDisplay) onScreenMaybeResized()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = WeakReference(this)
        homeDisplay = currentDisplayId()
        getSystemService(DisplayManager::class.java)
            ?.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        actions = DesktopActions(this) { currentDisplayId() }
        actions.onLaunched = { mode ->
            // Con un juego a pantalla completa, la barra se esconde para no tapar nada.
            ui.barHidden = ui.overlayActive && mode == WindowMode.FULLSCREEN
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Usar también la zona de la cámara: sin franjas negras a los lados en horizontal.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        // En el teléfono (para duplicar en la TV): formato de la TV para llenarla sin franjas.
        if (homeDisplay == Display.DEFAULT_DISPLAY) ScreenFit.apply(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (currentDisplayId() == Display.DEFAULT_DISPLAY && NexoPrefs.phoneLandscape) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }

        // «Atrás» nunca cierra el escritorio: solo cierra menús abiertos.
        onBackPressedDispatcher.addCallback(this) { ui.closeAll() }

        setContent { DesktopRoot(actions, ui) }

        Displays.byId(this, homeDisplay)?.let { d ->
            val size = Displays.sizeOf(d)
            lastW = size.x
            lastH = size.y
            controls = WindowControls(this, desktopTaskId = { taskId }, menuOpen = { ui.anyOpen })
                .also { it.start(d, com.nexo.escritorio.ui.UiScale.forDisplay(this, d)) }
        }
        handleAction(intent?.getStringExtra(EXTRA_ACTION))
        window.decorView.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            v.removeCallbacks(fullSizeCheck)
            v.postDelayed(fullSizeCheck, 600)
        }
    }

    private val fullSizeCheck = Runnable { ensureFullSize() }

    /**
     * Comprueba que el escritorio ocupa todo el monitor o la TV. Si el sistema lo abrió como
     * una ventana más pequeña, lo agranda: con Shizuku, con el botón de maximizar de Android
     * (asistente) o, como último recurso, reabriéndolo a pantalla completa.
     */
    private fun ensureFullSize() {
        if (homeDisplay == Display.DEFAULT_DISPLAY || isFinishing) return
        val d = Displays.byId(this, homeDisplay) ?: return
        val size = Displays.sizeOf(d)
        val v = window.decorView
        if (v.width >= size.x * 0.97f && v.height >= size.y * 0.97f) return
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastFixAttempt < 15_000) return
        lastFixAttempt = now
        val full = android.graphics.Rect(0, 0, size.x, size.y)
        if (TaskControl.ready.value && TaskControl.resize(taskId, full)) return
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        val mine = android.graphics.Rect(loc[0], loc[1], loc[0] + v.width, loc[1] + v.height)
        if (NexoAccessibility.get()?.clickCaptionButton(homeDisplay, mine, NexoAccessibility.CaptionButton.MAXIMIZE) == true) return
        val app = applicationContext
        finishAndRemoveTask()
        Handler(Looper.getMainLooper()).postDelayed({ Displays.openDesktop(app, d) }, 400)
    }

    override fun onStart() {
        super.onStart()
        refreshOverlay()
    }

    override fun onResume() {
        super.onResume()
        TaskControl.refresh()
        if (lastUiSize != NexoPrefs.uiSize) rebuildForNewSize()
        refreshOverlay()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        onScreenMaybeResized()
    }

    /**
     * La pantalla cambió de tamaño o de resolución (otra TV, ventana de scrcpy, giro del teléfono):
     * el escritorio se reescala solo y las ventanas abiertas se reacomodan.
     */
    private fun onScreenMaybeResized() {
        val d = Displays.byId(this, homeDisplay) ?: return
        val size = Displays.sizeOf(d)
        if (size.x == lastW && size.y == lastH) return
        val oldW = lastW
        val oldH = lastH
        lastW = size.x
        lastH = size.y
        // Se espera a que la interfaz tome el nuevo tamaño antes de mover ventanas.
        window.decorView.postDelayed({
            controls?.onDisplayResized(oldW, oldH, size.x, size.y)
        }, 350)
        rebuildForNewSize()
    }

    /** Vuelve a crear la barra superpuesta y los botones con la nueva escala. */
    fun rebuildForNewSize() {
        lastUiSize = NexoPrefs.uiSize
        overlay?.detach()
        overlay = null
        ui.overlayActive = false
        refreshOverlay()
        val d = Displays.byId(this, homeDisplay) ?: return
        controls?.start(d, com.nexo.escritorio.ui.UiScale.forDisplay(this, d))
    }

    /** Pone o quita la barra superpuesta según el permiso y la preferencia. */
    fun refreshOverlay() {
        val want = NexoPrefs.floatingBar && BarOverlay.canUse(this)
        if (want && overlay == null) {
            val d = Displays.byId(this, currentDisplayId())
            val o = BarOverlay(this, ui, actions)
            if (d != null && o.attach(d)) {
                overlay = o
                ui.overlayActive = true
            }
        } else if (!want && overlay != null) {
            overlay?.detach()
            overlay = null
            ui.overlayActive = false
            ui.barHidden = false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val action = intent.getStringExtra(EXTRA_ACTION)
        if (action == null) ui.closeAll()
        handleAction(action)
    }

    fun handleAction(action: String?) {
        when (action) {
            ACTION_START -> ui.toggleStart()
            ACTION_PANEL -> ui.togglePanel()
            ACTION_SETTINGS -> ui.openSettings()
        }
    }

    /** Tecla Windows pulsada en cualquier app (la detecta el asistente). */
    fun onGlobalWinKey() {
        ui.barHidden = false
        if (ui.overlayActive) {
            ui.toggleStart()
        } else if (ui.startOpen) {
            ui.closeAll()
        } else {
            actions.showDesktop(ACTION_START)
        }
    }

    override fun onDestroy() {
        runCatching { getSystemService(DisplayManager::class.java)?.unregisterDisplayListener(displayListener) }
        controls?.stop()
        controls = null
        overlay?.detach()
        overlay = null
        if (current?.get() === this) current = null
        // La pantalla del teléfono vuelve a su formato original.
        if (homeDisplay == Display.DEFAULT_DISPLAY) ScreenFit.reset(this)
        super.onDestroy()
    }
}
