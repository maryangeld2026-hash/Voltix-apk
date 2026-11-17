package com.nexo.escritorio.pc

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexo.escritorio.Displays
import com.nexo.escritorio.NexoAccessibility
import com.nexo.escritorio.NexoPrefs
import com.nexo.escritorio.TaskControl
import com.nexo.escritorio.ui.Card
import com.nexo.escritorio.ui.CodeBlock
import com.nexo.escritorio.ui.FlowRow2
import com.nexo.escritorio.ui.GhostButton
import com.nexo.escritorio.ui.MiniStep
import com.nexo.escritorio.ui.Nexo
import com.nexo.escritorio.ui.NexoTheme
import com.nexo.escritorio.ui.Note
import com.nexo.escritorio.ui.PrimaryButton
import com.nexo.escritorio.ui.Wallpaper
import com.nexo.escritorio.ui.accentColor

/** «Escritorio en el PC por cable USB»: con el navegador del PC o con el programa Nexo PC (modo accesorio). */
class PcActivity : ComponentActivity() {

    companion object {
        const val DESCARGA_PC = "https://github.com/maryangeld2026-hash/Voltix-apk/releases/download/nexo-escritorio/NexoPC.zip"
    }

    private val permiso = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK && r.data != null) {
            val i = Intent(this, PcService::class.java).putExtra(PcService.EXTRA_CODIGO, r.resultCode).putExtra(PcService.EXTRA_DATOS, r.data)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        } else {
            Toast.makeText(this, "Sin el permiso de la pantalla no se puede mostrar el escritorio en el PC.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
        }
        accesorio(intent)
        if (intent?.getBooleanExtra("empezar", false) == true && !PcService.estado.value.activo) empezar()
        setContent {
            NexoTheme(accentColor(NexoPrefs.accent)) { Pantalla() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        accesorio(intent)
        if (intent.getBooleanExtra("empezar", false) && !PcService.estado.value.activo) empezar()
    }

    /** El programa Nexo PC se conectó por el cable (modo accesorio). */
    private fun accesorio(i: Intent?) {
        if (i?.action != UsbManager.ACTION_USB_ACCESSORY_ATTACHED) return
        @Suppress("DEPRECATION")
        val acc: UsbAccessory = (if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java) else i.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)) ?: return
        if (!PcService.conectarAccesorio(acc)) empezar()
    }

    private fun empezar() {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val i = if (Build.VERSION.SDK_INT >= 34) mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()) else mpm.createScreenCaptureIntent()
        runCatching { permiso.launch(i) }
    }

    private fun intentar(vararg l: Intent): Boolean {
        for (i in l) if (runCatching { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return true
        return false
    }

    private fun modemUsb() {
        if (!intentar(
                Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.TetherSettings")),
                Intent("android.settings.TETHER_SETTINGS"),
                Intent(Settings.ACTION_WIRELESS_SETTINGS),
            )
        ) Toast.makeText(this, "Busca «Módem USB» en Ajustes › Conexiones › Zona Wi‑Fi y módem.", Toast.LENGTH_LONG).show()
    }

    private fun copiar(t: String) {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Nexo", t))
        Toast.makeText(this, "Copiado", Toast.LENGTH_SHORT).show()
    }

    @Composable
    private fun Pantalla() {
        val e by PcService.estado.collectAsState()
        val shizuku by TaskControl.ready.collectAsState()
        val asistente by NexoAccessibility.connected.collectAsState()
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            Wallpaper(0, null, animated = false, modifier = Modifier.fillMaxSize())
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).safeDrawingPadding().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                    Text("Escritorio en el PC", color = Nexo.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("Por el cable USB‑C a USB, sin salida de video ni depuración USB", color = Nexo.textDim, fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))

                    Card {
                        if (e.activo) {
                            Text("● Compartiendo", color = Nexo.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            if (e.direcciones.isEmpty()) {
                                Text("Todavía no hay red con el PC. Conecta el cable y enciende «Módem USB» (o conéctate a la misma Wi‑Fi).", color = Nexo.textDim, fontSize = 14.sp)
                            } else {
                                Text("En el navegador del PC (Chrome o Edge), escribe:", color = Nexo.textDim, fontSize = 13.sp)
                                e.direcciones.forEach { (tipo, ip) ->
                                    val u = "http://$ip:${e.puerto}/?k=${e.clave}"
                                    Spacer(Modifier.height(6.dp))
                                    Text(tipo, color = Nexo.textFaint, fontSize = 12.sp)
                                    CodeBlock(u) { copiar(u) }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Clave: ${e.clave}", color = Nexo.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text(
                                (if (e.navegadores > 0) "${e.navegadores} navegador${if (e.navegadores > 1) "es" else ""} conectado${if (e.navegadores > 1) "s" else ""}" else "Ningún navegador conectado") +
                                    (if (e.programa) " · programa Nexo PC conectado" else ""),
                                color = Nexo.textDim, fontSize = 13.sp,
                            )
                            Spacer(Modifier.height(12.dp))
                            FlowRow2 {
                                PrimaryButton("Escritorio en este teléfono") { Displays.openDesktopHere(this@PcActivity) }
                                GhostButton("Detener") { PcService.parar(this@PcActivity) }
                            }
                        } else {
                            Text("Listo para conectar", color = Nexo.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            if (e.aviso.isNotEmpty()) Note(e.aviso)
                            Spacer(Modifier.height(10.dp))
                            PrimaryButton("Empezar", Modifier.fillMaxWidth()) { empezar() }
                            Note("Android te pregunta si Nexo puede compartir la pantalla: escoge «Pantalla completa».")
                        }
                    }
                    Spacer(Modifier.height(14.dp))

                    Card {
                        Text("Con el navegador del PC (sin instalar nada)", color = Nexo.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        MiniStep(1, "Conecta el teléfono al PC con el cable USB‑C a USB.")
                        MiniStep(2, "Enciende «Módem USB» (comparte la conexión por el cable; no hace falta tener datos).")
                        MiniStep(3, "Toca «Empezar» y luego «Escritorio en este teléfono».")
                        MiniStep(4, "En el PC abre Chrome o Edge, escribe la dirección y la clave. Usa el ratón y el teclado del PC; F11 para pantalla completa.")
                        Spacer(Modifier.height(8.dp))
                        FlowRow2 { GhostButton("Abrir «Módem USB»") { modemUsb() } }
                        Note("También sirve sin cable si el PC y el teléfono están en la misma Wi‑Fi. Solo entra quien tiene la clave, que cambia cada vez.")
                    }
                    Spacer(Modifier.height(14.dp))

                    Card {
                        Text("Con el programa Nexo PC (modo accesorio)", color = Nexo.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        MiniStep(1, "Descarga Nexo PC en el computador (Windows, Mac o Linux) y ábrelo.")
                        MiniStep(2, "Conecta el cable: el programa pone el teléfono en modo accesorio y Android abre Nexo solo.")
                        MiniStep(3, "Acepta compartir la pantalla. El escritorio aparece en la ventana del programa.")
                        Note("Va directo por el cable, sin red ni «Módem USB». En Windows, la primera vez hay que instalar el controlador WinUSB con Zadig (el programa te explica cómo).")
                        Spacer(Modifier.height(8.dp))
                        FlowRow2 {
                            GhostButton("Descargar Nexo PC") { intentar(Intent(Intent.ACTION_VIEW, Uri.parse(DESCARGA_PC))) }
                            GhostButton("Copiar enlace") { copiar(DESCARGA_PC) }
                        }
                    }
                    Spacer(Modifier.height(14.dp))

                    Card {
                        Text("Ratón y teclado del PC", color = Nexo.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            when {
                                shizuku -> "✓ Con Shizuku: ratón, rueda, atajos y todas las teclas, como un computador."
                                asistente -> "✓ Con el asistente de Nexo: clic, arrastrar, clic derecho (mantener), rueda y escribir texto. Con Shizuku funcionan además los atajos de teclado."
                                else -> "Activa el asistente de Nexo (accesibilidad) para usar el ratón y el teclado del PC. Sin él, el PC solo ve la pantalla."
                            },
                            color = Nexo.textDim, fontSize = 13.sp,
                        )
                        if (!asistente && !shizuku) {
                            Spacer(Modifier.height(8.dp))
                            PrimaryButton("Activar el asistente") { intentar(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
