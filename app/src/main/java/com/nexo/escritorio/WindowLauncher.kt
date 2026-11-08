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
import android.os.Process
import android.view.Display
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import android.provider.Settings as AndroidSettings

/** Cómo se abre una app en el monitor. */
enum class WindowMode { DEFAULT, WINDOW, MAXIMIZED, LEFT, RIGHT }

/**
 * Calcula el tamaño y la posición de las ventanas. Android dibuja las ventanas
 * (barra de título, mover, cambiar tamaño); nosotros le decimos dónde abrirlas.
 */
object WindowLauncher {
    var screenW = 0
    var screenH = 0
    var reservedBottom = 0
    private var cascade = 0

    /** Apps abiertas desde el escritorio en esta sesión (se muestran en la barra). */
    val opened = mutableStateListOf<String>()

    fun bounds(mode: WindowMode): Rect? {
        if (screenW <= 0 || screenH <= 0) return null
        val m = if (mode == WindowMode.DEFAULT) {
            if (NexoPrefs.openMaximized) WindowMode.MAXIMIZED else WindowMode.WINDOW
        } else mode
        val usableH = (screenH - reservedBottom).coerceAtLeast(screenH / 2)
        return when (m) {
            WindowMode.MAXIMIZED -> Rect(0, 0, screenW, usableH)
            WindowMode.LEFT -> Rect(0, 0, screenW / 2, usableH)
            WindowMode.RIGHT -> Rect(screenW / 2, 0, screenW, usableH)
            else -> {
                val w = (screenW * 0.62f).toInt()
                val h = (usableH * 0.80f).toInt()
                val step = (screenW * 0.022f).toInt()
                val i = cascade++ % 5
                val left = ((screenW - w) / 2 - 2 * step + i * step).coerceIn(0, (screenW - w).coerceAtLeast(0))
                val top = ((usableH - h) / 2 - 2 * step + i * step).coerceIn(0, (usableH - h).coerceAtLeast(0))
                Rect(left, top, left + w, top + h)
            }
        }
    }

    fun options(displayId: Int, mode: WindowMode): ActivityOptions {
        val o = ActivityOptions.makeBasic()
        o.setLaunchDisplayId(displayId)
        bounds(mode)?.let { o.setLaunchBounds(it) }
        return o
    }

    fun markOpened(key: String) {
        opened.remove(key)
        opened.add(key)
        while (opened.size > 12) opened.removeAt(0)
    }
}

/** Detecta el monitor y abre el escritorio en él. */
object Displays {
    fun external(ctx: Context): Display? {
        val dm = ctx.getSystemService(DisplayManager::class.java) ?: return null
        dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
            ?.let { return it }
        return dm.displays.firstOrNull {
            it.displayId != Display.DEFAULT_DISPLAY &&
                (it.flags and Display.FLAG_PRIVATE) == 0 &&
                it.state != Display.STATE_OFF
        }
    }

    @Suppress("DEPRECATION")
    fun sizeOf(d: Display): Point {
        val p = Point()
        d.getRealSize(p)
        return p
    }

    /** Abre el escritorio en el monitor. */
    fun openDesktop(ctx: Context, display: Display): Boolean {
        DesktopActivity.current?.get()?.let { running ->
            if (running.currentDisplayId() != display.displayId) running.finish()
        }
        val intent = Intent(ctx, DesktopActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val o = ActivityOptions.makeBasic()
        o.setLaunchDisplayId(display.displayId)
        // En un monitor en modo ventanas, el escritorio debe ocupar toda la pantalla y
        // no ser una ventana más. Se pide «pantalla completa»; si el teléfono no lo
        // permite, se abre como una ventana del tamaño del monitor.
        if (!requestFullscreen(o)) {
            val size = sizeOf(display)
            o.setLaunchBounds(Rect(0, 0, size.x, size.y))
        }
        return try {
            ctx.startActivity(intent, o.toBundle())
            true
        } catch (e: Exception) {
            false
        }
    }

    private const val WINDOWING_MODE_FULLSCREEN = 1

    private fun requestFullscreen(o: ActivityOptions): Boolean = try {
        ActivityOptions::class.java
            .getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
            .invoke(o, WINDOWING_MODE_FULLSCREEN)
        true
    } catch (e: Throwable) {
        false
    }

    /** Vista previa del escritorio en la pantalla del propio teléfono. */
    fun openDesktopHere(ctx: Context) {
        DesktopActivity.current?.get()?.let { running ->
            if (running.currentDisplayId() != Display.DEFAULT_DISPLAY) running.finish()
        }
        ctx.startActivity(Intent(ctx, DesktopActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
class DesktopActions(private val activity: Activity) {
    private val displayId: Int get() = activity.currentDisplayId()
    private val launcherApps: LauncherApps get() = activity.getSystemService(LauncherApps::class.java)

    fun launch(app: AppEntry, mode: WindowMode = WindowMode.DEFAULT, newInstance: Boolean = false) {
        val opts = WindowLauncher.options(displayId, mode).toBundle()
        try {
            if (newInstance && app.user == Process.myUserHandle()) {
                val i = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(app.component)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                            Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                    )
                activity.startActivity(i, opts)
            } else {
                launcherApps.startMainActivity(app.component, app.user, null, opts)
            }
            WindowLauncher.markOpened(app.key)
        } catch (e: Exception) {
            toast("No se pudo abrir ${app.label}")
        }
    }

    /** Abre cualquier pantalla (ajustes, archivos, web…) en una ventana del monitor. */
    fun open(intent: Intent, mode: WindowMode = WindowMode.WINDOW, quiet: Boolean = false): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            activity.startActivity(intent, WindowLauncher.options(displayId, mode).toBundle())
            true
        } catch (e: Exception) {
            if (!quiet) toast("Esta opción no está disponible en tu teléfono")
            false
        }
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
        val o = WindowLauncher.options(displayId, WindowMode.DEFAULT)
        if (Build.VERSION.SDK_INT >= 34) {
            o.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }
        try {
            pi.send(activity, 0, null, null, null, null, o.toBundle())
        } catch (e: PendingIntent.CanceledException) {
            toast("Esta notificación ya no se puede abrir")
        } catch (e: Exception) {
            toast("No se pudo abrir la notificación")
        }
        if (n.autoCancel) NotificationService.dismiss(n.key)
    }

    fun exit() = activity.finish()

    fun toast(msg: String) {
        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
    }
}
