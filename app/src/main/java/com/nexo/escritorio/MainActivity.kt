package com.nexo.escritorio

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.nexo.escritorio.ui.NexoTheme
import com.nexo.escritorio.ui.SetupScreen
import com.nexo.escritorio.ui.SetupStatus
import com.nexo.escritorio.ui.accentColor
import java.net.Inet4Address
import android.provider.Settings as AndroidSettings

/** Pantalla del teléfono: formas de conexión, estado y guía de configuración. */
class MainActivity : ComponentActivity() {

    private val refreshTick = mutableIntStateOf(0)
    private val external = mutableStateOf<Display?>(null)
    private lateinit var displayManager: DisplayManager

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refreshDisplay()
        override fun onDisplayRemoved(displayId: Int) = refreshDisplay()
        override fun onDisplayChanged(displayId: Int) = refreshDisplay()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Si se abrió en otra pantalla (por ejemplo desde scrcpy en el PC), allí va el escritorio.
        if (redirectIfOnOtherDisplay()) return
        displayManager = getSystemService(DisplayManager::class.java)
        setContent {
            NexoTheme(accentColor(NexoPrefs.accent)) {
                SetupScreen(
                    tick = refreshTick.intValue,
                    display = external.value,
                    host = this,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        redirectIfOnOtherDisplay()
    }

    private fun redirectIfOnOtherDisplay(): Boolean {
        val id = currentDisplayId()
        if (id == Display.DEFAULT_DISPLAY) return false
        Displays.byId(this, id)?.let { Displays.openDesktop(this, it) }
        finish()
        return true
    }

    override fun onStart() {
        super.onStart()
        if (!::displayManager.isInitialized) return
        displayManager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        refreshDisplay()
    }

    override fun onStop() {
        if (::displayManager.isInitialized) displayManager.unregisterDisplayListener(displayListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshTick.intValue++
    }

    private fun refreshDisplay() {
        external.value = Displays.external(this)
    }

    // ---- Datos para la pantalla de configuración ----

    fun readStatus(): SetupStatus = SetupStatus(
        developerOptions = globalFlag(AndroidSettings.Global.DEVELOPMENT_SETTINGS_ENABLED),
        freeform = globalFlag("enable_freeform_support"),
        forceDesktop = globalFlag("force_desktop_mode_on_external_displays"),
        resizable = globalFlag("force_resizable_activities"),
        notifications = NotificationService.isEnabled(this),
        canWriteSettings = checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED,
        overlay = BarOverlay.canUse(this),
        assistant = NexoAccessibility.isEnabled(this),
        isXiaomi = Build.MANUFACTURER.equals("xiaomi", ignoreCase = true) ||
            Build.BRAND.equals("redmi", ignoreCase = true) || Build.BRAND.equals("poco", ignoreCase = true),
        phoneIp = phoneIp(),
    )

    /** true/false si se pudo leer; null si el teléfono no deja comprobarlo. */
    private fun globalFlag(key: String): Boolean? = try {
        AndroidSettings.Global.getInt(contentResolver, key, 0) != 0
    } catch (e: Exception) {
        null
    }

    /** IP del teléfono en la red Wi‑Fi (para conectar scrcpy sin cable). */
    private fun phoneIp(): String? = try {
        val cm = getSystemService(ConnectivityManager::class.java)
        cm.getLinkProperties(cm.activeNetwork)?.linkAddresses
            ?.map { it.address }
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    } catch (e: Exception) {
        null
    }

    // ---- Acciones ----

    fun openDesktopOnMonitor() {
        val d = external.value ?: Displays.external(this)
        if (d == null) {
            toast("No hay ninguna pantalla conectada")
            return
        }
        if (!Displays.openDesktop(this, d)) toast("No se pudo abrir el escritorio en esa pantalla")
    }

    fun previewHere() = Displays.openDesktopHere(this)

    fun openTouchpad() {
        startActivity(Intent(this, TouchpadActivity::class.java))
    }

    /** Busca TVs y adaptadores inalámbricos (Miracast / «Transmitir»). */
    fun openCast() {
        val candidates = listOf(
            Intent(AndroidSettings.ACTION_CAST_SETTINGS),
            Intent("android.settings.WIFI_DISPLAY_SETTINGS"),
        )
        for (i in candidates) if (tryStart(i)) return
        toast("Abre los ajustes rápidos y toca «Transmitir» o «Duplicar pantalla»")
    }

    fun openDeveloperOptions() {
        if (!tryStart(Intent(AndroidSettings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))) {
            toast("Primero activa las opciones de desarrollador")
        }
    }

    fun openAboutPhone() {
        if (!tryStart(Intent(AndroidSettings.ACTION_DEVICE_INFO_SETTINGS))) tryStart(Intent(AndroidSettings.ACTION_SETTINGS))
    }

    fun openNotificationAccess() {
        tryStart(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    fun openOverlayPermission() {
        val i = Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        if (!tryStart(i)) tryStart(Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION))
    }

    fun openAccessibility() {
        tryStart(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    /** Información de la app (allí está «Permitir ajustes restringidos» en Android 13+). */
    fun openAppDetails() {
        tryStart(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    /** Permisos extra de Xiaomi: «Mostrar ventanas emergentes en segundo plano». */
    fun openXiaomiPermissions() {
        val editor = Intent("miui.intent.action.APP_PERM_EDITOR").putExtra("extra_pkgname", packageName)
        val withComponent = Intent(editor).setClassName(
            "com.miui.securitycenter",
            "com.miui.permcenter.permissions.PermissionsEditorActivity",
        )
        if (!tryStart(withComponent) && !tryStart(editor)) openAppDetails()
    }

    /** Con el permiso concedido por ADB, activa todo sin entrar en ajustes. */
    fun applyAllSettings() {
        val ok = try {
            AndroidSettings.Global.putInt(contentResolver, "enable_freeform_support", 1)
            AndroidSettings.Global.putInt(contentResolver, "force_resizable_activities", 1)
            AndroidSettings.Global.putInt(contentResolver, "force_desktop_mode_on_external_displays", 1)
            true
        } catch (e: Exception) {
            false
        }
        refreshTick.intValue++
        toast(if (ok) "¡Listo! Reinicia el teléfono para aplicar los cambios." else "Tu teléfono no permitió el cambio. Hazlo a mano en Opciones de desarrollador.")
    }

    fun copy(text: String) {
        val cm = getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("Nexo", text))
        if (Build.VERSION.SDK_INT < 33) toast("Copiado")
    }

    private fun tryStart(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
