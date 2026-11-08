package com.nexo.escritorio

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
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
    }

    val ui = DesktopUi()
    private lateinit var actions: DesktopActions
    private var overlay: BarOverlay? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = WeakReference(this)
        actions = DesktopActions(this) { currentDisplayId() }
        actions.onLaunched = { mode ->
            // Con un juego a pantalla completa, la barra se esconde para no tapar nada.
            ui.barHidden = ui.overlayActive && mode == WindowMode.FULLSCREEN
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
        handleAction(intent?.getStringExtra(EXTRA_ACTION))
    }

    override fun onStart() {
        super.onStart()
        refreshOverlay()
    }

    override fun onResume() {
        super.onResume()
        refreshOverlay()
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
        overlay?.detach()
        overlay = null
        if (current?.get() === this) current = null
        super.onDestroy()
    }
}
