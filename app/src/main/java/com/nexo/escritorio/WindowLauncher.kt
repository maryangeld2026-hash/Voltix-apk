package com.nexo.escritorio

import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Point
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.Display
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import android.provider.Settings as AndroidSettings

/** Cómo se abre una app. */
enum class WindowMode { DEFAULT, WINDOW, MAXIMIZED, LEFT, RIGHT, FULLSCREEN }

/**
 * Calcula el tamaño y la posición de las ventanas. Android dibuja las ventanas
 * (barra de título, mover, cambiar tamaño); nosotros le decimos dónde abrirlas.
 */
object WindowLauncher {
    var screenW = 0
    var screenH = 0
    var reservedBottom = 0
    /** Densidad de la interfaz del escritorio (ya escalada). */
    var density = 1f
    private var cascade = 0

    /** Ancho de la pantalla en dp de la interfaz (para decidir tamaños). */
    private val widthDp: Float get() = if (density > 0f) screenW / density else screenW.toFloat()

    /** Apps abiertas desde el escritorio en esta sesión (si el asistente no está activo). */
    val opened = mutableStateListOf<String>()

    fun resolve(mode: WindowMode, isGame: Boolean): WindowMode = when {
        mode != WindowMode.DEFAULT -> mode
        isGame && NexoPrefs.gamesFullscreen -> WindowMode.FULLSCREEN
        // Cómo se abren las apps (Ajustes de Nexo): por defecto ocupan toda la pantalla.
        NexoPrefs.openMode == NexoPrefs.OPEN_FULLSCREEN -> WindowMode.FULLSCREEN
        NexoPrefs.openMode == NexoPrefs.OPEN_MAXIMIZED -> WindowMode.MAXIMIZED
        // En pantallas pequeñas (por ejemplo, el teléfono) las ventanas se abren grandes.
        screenW > 0 && widthDp < 900f -> WindowMode.MAXIMIZED
        else -> WindowMode.WINDOW
    }

    fun bounds(mode: WindowMode): Rect? {
        if (screenW <= 0 || screenH <= 0) return null
        val usableH = (screenH - reservedBottom).coerceAtLeast(screenH / 2)
        return when (mode) {
            WindowMode.FULLSCREEN -> null
            WindowMode.MAXIMIZED -> Rect(0, 0, screenW, usableH)
            WindowMode.LEFT -> Rect(0, 0, screenW / 2, usableH)
            WindowMode.RIGHT -> Rect(screenW / 2, 0, screenW, usableH)
            else -> {
                // Tamaño inteligente: en pantallas grandes (TV, monitor 4K) las ventanas ocupan
                // menos proporción para que quepan varias a la vez.
                val fracW = when {
                    widthDp >= 1800f -> 0.48f
                    widthDp >= 1300f -> 0.56f
                    else -> 0.66f
                }
                val w = (screenW * fracW).toInt()
                val h = (usableH * 0.80f).toInt()
                val step = (screenW * 0.022f).toInt()
                val i = cascade++ % 5
                val left = ((screenW - w) / 2 - 2 * step + i * step).coerceIn(0, (screenW - w).coerceAtLeast(0))
                val top = ((usableH - h) / 2 - 2 * step + i * step).coerceIn(0, (usableH - h).coerceAtLeast(0))
                Rect(left, top, left + w, top + h)
            }
        }
    }

    /**
     * Opciones para abrir una app en [displayId]. Si [rich] es true se pide además el
     * modo de ventana (libre o pantalla completa), que hace que las ventanas funcionen
     * también en la pantalla del propio teléfono.
     */
    fun options(displayId: Int, mode: WindowMode, rich: Boolean = true): ActivityOptions {
        val o = ActivityOptions.makeBasic()
        o.setLaunchDisplayId(displayId)
        if (mode == WindowMode.FULLSCREEN) {
            if (rich) Hidden.setWindowingMode(o, Hidden.WINDOWING_MODE_FULLSCREEN)
        } else {
            if (rich) Hidden.setWindowingMode(o, Hidden.WINDOWING_MODE_FREEFORM)
            bounds(mode)?.let { o.setLaunchBounds(it) }
        }
        return o
    }

    fun markOpened(key: String) {
        opened.remove(key)
        opened.add(key)
        while (opened.size > 12) opened.removeAt(0)
    }
}

/** Detecta pantallas y abre el escritorio en ellas. */
object Displays {
    /** Una pantalla donde tiene sentido abrir el escritorio (monitor, TV inalámbrica, scrcpy…). */
    fun isDesktopCandidate(d: Display): Boolean {
        if (d.displayId == Display.DEFAULT_DISPLAY) return false
        if ((d.flags and Display.FLAG_PRIVATE) != 0) return false
        if (d.state == Display.STATE_OFF) return false
        val presentation = (d.flags and Display.FLAG_PRESENTATION) != 0
        val name = d.name.orEmpty()
        return presentation || name.contains("scrcpy", ignoreCase = true) || name.contains("nexo", ignoreCase = true)
    }

    fun external(ctx: Context): Display? {
        val dm = ctx.getSystemService(DisplayManager::class.java) ?: return null
        dm.displays.firstOrNull { isDesktopCandidate(it) }?.let { return it }
        return dm.displays.firstOrNull {
            it.displayId != Display.DEFAULT_DISPLAY &&
                (it.flags and Display.FLAG_PRIVATE) == 0 &&
                it.state != Display.STATE_OFF
        }
    }

    fun byId(ctx: Context, id: Int): Display? =
        ctx.getSystemService(DisplayManager::class.java)?.getDisplay(id)

    @Suppress("DEPRECATION")
    fun sizeOf(d: Display): Point {
        val p = Point()
        d.getRealSize(p)
        return p
    }

    fun desktopIntent(ctx: Context, action: String? = null): Intent =
        Intent(ctx, DesktopActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .apply { if (action != null) putExtra(DesktopActivity.EXTRA_ACTION, action) }

    /** Abre (o trae al frente) el escritorio en una pantalla. */
    fun openDesktop(ctx: Context, display: Display, action: String? = null): Boolean {
        DesktopActivity.current?.get()?.let { running ->
            if (running.currentDisplayId() != display.displayId) running.finish()
        }
        val o = ActivityOptions.makeBasic()
        o.setLaunchDisplayId(display.displayId)
        if (display.displayId != Display.DEFAULT_DISPLAY) {
            // El escritorio ocupa SIEMPRE toda la pantalla: se pide pantalla completa y, además,
            // el tamaño exacto de la pantalla por si el sistema lo abre como ventana.
            Hidden.setWindowingMode(o, Hidden.WINDOWING_MODE_FULLSCREEN)
            val size = sizeOf(display)
            o.setLaunchBounds(Rect(0, 0, size.x, size.y))
        } else {
            Hidden.setWindowingMode(o, Hidden.WINDOWING_MODE_FULLSCREEN)
        }
        return try {
            ctx.startActivity(desktopIntent(ctx, action), o.toBundle())
            true
        } catch (e: Exception) {
            try {
                val basic = ActivityOptions.makeBasic().setLaunchDisplayId(display.displayId)
                ctx.startActivity(desktopIntent(ctx, action), basic.toBundle())
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    /** Escritorio en la pantalla del propio teléfono (para duplicar en una TV). */
    fun openDesktopHere(ctx: Context) {
        val d = byId(ctx, Display.DEFAULT_DISPLAY)
        if (d != null) openDesktop(ctx, d) else ctx.startActivity(desktopIntent(ctx))
    }

    /** Trae el escritorio que ya está abierto al frente, en su pantalla. */
    fun bringDesktop(ctx: Context, action: String? = null): Boolean {
        val running = DesktopActivity.current?.get() ?: return false
        val d = byId(ctx, running.currentDisplayId()) ?: return false
        return try {
            val o = ActivityOptions.makeBasic().setLaunchDisplayId(d.displayId)
            ctx.startActivity(desktopIntent(ctx, action), o.toBundle())
            true
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Vigila cuando se conecta una pantalla (cable, inalámbrica o scrcpy) y abre el escritorio,
 * también con la app cerrada si el asistente o las notificaciones de Nexo están activos.
 */
object DisplayWatcher {
    private var started = false
    private val handler = Handler(Looper.getMainLooper())

    fun start(app: Context) {
        if (started) return
        started = true
        val ctx = app.applicationContext
        val dm = ctx.getSystemService(DisplayManager::class.java) ?: return
        dm.registerDisplayListener(object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                handler.postDelayed({ maybeOpen(ctx, displayId) }, 900)
            }

            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {}
        }, handler)
    }

    private fun maybeOpen(ctx: Context, displayId: Int) {
        if (!NexoPrefs.autoOpen) return
        val d = Displays.byId(ctx, displayId) ?: return
        if (!Displays.isDesktopCandidate(d)) return
        if (DesktopActivity.current?.get()?.currentDisplayId() == displayId) return
        runCatching { Displays.openDesktop(ctx, d) }
    }
}

@Suppress("DEPRECATION")
fun Activity.currentDisplayId(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        display?.displayId ?: Display.DEFAULT_DISPLAY
    } else {
        windowManager.defaultDisplay.displayId
    }

/** Todo lo que el escritorio puede hacer: abrir apps, ajustes, notificaciones… */
class DesktopActions(private val ctx: Context, private val displayIdProvider: () -> Int) {
    val displayId: Int get() = displayIdProvider()
    private val launcherApps: LauncherApps get() = ctx.getSystemService(LauncherApps::class.java)

    /** Se llama después de abrir algo (por ejemplo, para ocultar la barra con los juegos). */
    var onLaunched: (WindowMode) -> Unit = {}

    fun launch(app: AppEntry, mode: WindowMode = WindowMode.DEFAULT, newInstance: Boolean = false) {
        val resolved = WindowLauncher.resolve(mode, app.isGame)
        WindowControls.noteLaunch(app.packageName, resolved)
        val ok = tryLaunch(app, resolved, newInstance, rich = true) || tryLaunch(app, resolved, newInstance, rich = false)
        if (ok) {
            WindowLauncher.markOpened(app.key)
            onLaunched(resolved)
        } else {
            toast("No se pudo abrir ${app.label}")
        }
    }

    private fun tryLaunch(app: AppEntry, mode: WindowMode, newInstance: Boolean, rich: Boolean): Boolean {
        val opts = WindowLauncher.options(displayId, mode, rich).toBundle()
        return try {
            if (newInstance && app.user == Process.myUserHandle()) {
                val i = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(app.component)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                            Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                    )
                ctx.startActivity(i, opts)
            } else {
                launcherApps.startMainActivity(app.component, app.user, null, opts)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Abre cualquier pantalla (ajustes, archivos, web…) en una ventana. */
    fun open(intent: Intent, mode: WindowMode = WindowMode.WINDOW, quiet: Boolean = false): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        for (rich in listOf(true, false)) {
            try {
                ctx.startActivity(intent, WindowLauncher.options(displayId, mode, rich).toBundle())
                onLaunched(mode)
                return true
            } catch (e: Exception) {
                // se intenta de nuevo sin opciones especiales
            }
        }
        if (!quiet) toast("Esta opción no está disponible en tu teléfono")
        return false
    }

    fun appInfo(app: AppEntry) {
        try {
            launcherApps.startAppDetailsActivity(
                app.component, app.user, null,
                WindowLauncher.options(displayId, WindowMode.WINDOW).toBundle()
            )
        } catch (e: Exception) {
            open(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
        }
    }

    fun uninstall(app: AppEntry) {
        open(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
    }

    fun webSearch(query: String) {
        val search = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
        if (!open(search, quiet = true)) {
            open(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))))
        }
    }

    fun files() {
        val app = AppRepository.findByPackages(
            listOf(
                "com.google.android.apps.nbu.files", "com.sec.android.app.myfiles",
                "com.mi.android.globalFileexplorer", "com.android.fileexplorer",
                "com.coloros.filemanager", "com.huawei.hidisk",
                "com.google.android.documentsui", "com.android.documentsui"
            )
        )
        if (app != null) {
            launch(app)
            return
        }
        val browse = Intent("android.provider.action.BROWSE")
            .setData(Uri.parse("content://com.android.externalstorage.documents/root/primary"))
        open(browse)
    }

    fun openNotification(n: NotifItem) {
        val pi = n.intent ?: return
        val o = WindowLauncher.options(displayId, WindowLauncher.resolve(WindowMode.DEFAULT, false))
        if (Build.VERSION.SDK_INT >= 34) {
            o.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }
        try {
            pi.send(ctx, 0, null, null, null, null, o.toBundle())
        } catch (e: PendingIntent.CanceledException) {
            toast("Esta notificación ya no se puede abrir")
        } catch (e: Exception) {
            toast("No se pudo abrir la notificación")
        }
        if (n.autoCancel) NotificationService.dismiss(n.key)
    }

    /** Trae el escritorio al frente (como «Mostrar escritorio» en Windows). */
    fun showDesktop(action: String? = null) {
        if (!Displays.bringDesktop(ctx, action)) {
            DesktopActivity.current?.get()?.handleAction(action)
        }
    }

    fun touchpad() {
        try {
            val o = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY)
            ctx.startActivity(
                Intent(ctx, TouchpadActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                o.toBundle()
            )
        } catch (e: Exception) {
            toast("No se pudo abrir el touchpad")
        }
    }

    fun exit() {
        DesktopActivity.current?.get()?.finish()
    }

    fun toast(msg: String) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }
}
