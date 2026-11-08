package com.nexo.escritorio

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.nexo.escritorio.ui.DesktopRoot
import com.nexo.escritorio.ui.DesktopUi
import java.lang.ref.WeakReference

/** El escritorio completo que se ve en el monitor. */
class DesktopActivity : ComponentActivity() {

    companion object {
        var current: WeakReference<DesktopActivity>? = null
    }

    private val ui = DesktopUi()
    private lateinit var actions: DesktopActions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = WeakReference(this)
        actions = DesktopActions(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // «Atrás» nunca cierra el escritorio: solo cierra menús abiertos.
        onBackPressedDispatcher.addCallback(this) { ui.closeAll() }

        setContent { DesktopRoot(actions, ui) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        ui.closeAll()
    }

    override fun onDestroy() {
        if (current?.get() === this) current = null
        super.onDestroy()
    }
}
