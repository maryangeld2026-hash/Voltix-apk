package com.nexo.escritorio

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
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
import android.provider.Settings as AndroidSettings

/** Pantalla del teléfono: estado del monitor y guía de configuración. */
class MainActivity : ComponentActivity() {

    private val refreshTick = mutableIntStateOf(0)
    private val external = mutableStateOf<Display?>(null)
    private lateinit var displayManager: DisplayManager

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refreshDisplay(autoOpen = true)
        override fun onDisplayRemoved(displayId: Int) = refreshDisplay(autoOpen = false)
        override fun onDisplayChanged(displayId: Int) = refreshDisplay(autoOpen = false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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

    override fun onStart() {
        super.onStart()
        displayManager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        refreshDisplay(autoOpen = false)
    }

    override fun onStop() {
        displayManager.unregisterDisplayListener(displayListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshTick.intValue++
    }

    private fun refreshDisplay(autoOpen: Boolean) {
        val before = external.value
        val now = Displays.external(this)
        external.value = now
        if (autoOpen && now != null && before == null && NexoPrefs.autoOpen) {
            Displays.openDesktop(this, now)
        }
    }

    // ---- Acciones que usa la pantalla de configuración ----

    fun readStatus(): SetupStatus = SetupStatus(
        developerOptions = globalFlag(AndroidSettings.Global.DEVELOPMENT_SETTINGS_ENABLED),
        freeform = globalFlag("enable_freeform_support"),
        forceDesktop = globalFlag("force_desktop_mode_on_external_displays"),
        resizable = globalFlag("force_resizable_activities"),
        notifications = NotificationService.isEnabled(this),
        canWriteSettings = checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED,
    )

    /** true/false si se pudo leer; null si el teléfono no deja comprobarlo. */
    private fun globalFlag(key: String): Boolean? = try {
        AndroidSettings.Global.getInt(contentResolver, key, 0) != 0
    } catch (e: Exception) {
        null
    }

    fun openDesktopOnMonitor() {
        val d = external.value ?: Displays.external(this)
        if (d == null) {
            toast("No hay ningún monitor conectado")
            return
        }
        if (!Displays.openDesktop(this, d)) toast("No se pudo abrir el escritorio en el monitor")
    }

    fun previewHere() = Displays.openDesktopHere(this)

    fun openDeveloperOptions() {
        if (!tryStart(Intent(AndroidSettings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))) {
            toast("Primero activa las opciones de desarrollador (paso 1)")
        }
    }

    fun openAboutPhone() {
        if (!tryStart(Intent(AndroidSettings.ACTION_DEVICE_INFO_SETTINGS))) tryStart(Intent(AndroidSettings.ACTION_SETTINGS))
    }

    fun openNotificationAccess() {
        tryStart(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
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
