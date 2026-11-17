package com.nexo.escritorio

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.Normalizer
import java.util.Locale

/**
 * Nexo ⇄ Jarvis (el asistente co.jarvis.docente).
 *  · Nexo → Jarvis: «Preguntar a Jarvis» en el menú de inicio y el botón de Jarvis en la barra
 *    abren Jarvis en una ventana del monitor con la pregunta; Jarvis devuelve la respuesta y
 *    Nexo la muestra en una tarjeta del escritorio.
 *  · Jarvis → Nexo: [NexoApiActivity] recibe órdenes («abre el escritorio», «abre Chrome a la
 *    izquierda»…) y le contesta a Jarvis cómo le fue.
 * Nada de esto toca contraseñas, pagos ni desinstalaciones.
 */
object Jarvis {
    const val PKG = "co.jarvis.docente"
    /** Nexo → Jarvis: una pregunta (o «escúchame») desde el escritorio. */
    const val ACCION_PREGUNTAR = "co.jarvis.docente.PREGUNTAR"
    /** Nexo → Jarvis: cómo salió una orden y el estado del escritorio. */
    const val ACCION_RESULTADO = "co.jarvis.docente.NEXO"
    /** Jarvis → Nexo: la respuesta a una pregunta hecha desde el escritorio. */
    const val ACCION_RESPUESTA = "com.nexo.escritorio.JARVIS_RESPUESTA"

    data class Respuesta(val pregunta: String, val texto: String, val t: Long)

    private val _respuesta = MutableStateFlow<Respuesta?>(null)
    val respuesta: StateFlow<Respuesta?> = _respuesta
    fun cerrarRespuesta() { _respuesta.value = null }

    fun instalado(ctx: Context): Boolean =
        runCatching { ctx.packageManager.getPackageInfo(PKG, 0); true }.getOrDefault(false)

    private fun intent(texto: String?, escuchar: Boolean): Intent =
        Intent(ACCION_PREGUNTAR)
            .setPackage(PKG)
            .addCategory(Intent.CATEGORY_DEFAULT)
            .putExtra("origen", "nexo")
            .putExtra("escuchar", escuchar)
            .apply { if (!texto.isNullOrBlank()) putExtra("texto", texto.take(2000)) }

    /** Abre Jarvis en una ventana del monitor con la pregunta. */
    fun preguntar(actions: DesktopActions, texto: String) {
        if (!actions.open(intent(texto, false), WindowMode.WINDOW, quiet = true)) {
            actions.toast("Instala Jarvis para preguntarle desde el escritorio")
        }
    }

    /** Abre Jarvis en una ventana del monitor, escuchando. */
    fun hablar(actions: DesktopActions) {
        if (!actions.open(intent(null, true), WindowMode.WINDOW, quiet = true)) {
            actions.toast("Instala Jarvis para hablarle desde el escritorio")
        }
    }

    /** ¿El texto del buscador va para Jarvis? («jarvis, …», «?…», o una pregunta con signos). */
    fun esPregunta(q: String): Boolean {
        val t = q.trim().lowercase(Locale.ROOT)
        return t.startsWith("jarvis") || t.startsWith("?") || t.startsWith("¿") || t.endsWith("?")
    }

    /** Quita «jarvis,» del comienzo. */
    fun limpiar(q: String): String =
        q.trim().replace(Regex("^(?i)jarvis[,:\\s]+"), "").removePrefix("?").trim().ifEmpty { q.trim() }

    internal fun recibir(pregunta: String, texto: String) {
        _respuesta.value = Respuesta(pregunta, texto.take(4000), System.currentTimeMillis())
    }

    /** Le contesta a Jarvis cómo salió una orden (con el estado del escritorio). */
    fun responder(ctx: Context, id: String?, ok: Boolean, mensaje: String, codigo: String = if (ok) "ok" else "error") {
        val monitor = Displays.external(ctx)
        val desk = DesktopActivity.current?.get()
        val i = Intent(ACCION_RESULTADO)
            .setPackage(PKG)
            .putExtra("id", id ?: "")
            .putExtra("ok", ok)
            .putExtra("codigo", codigo)
            .putExtra("mensaje", mensaje)
            .putExtra("monitor", monitor?.name ?: "")
            .putExtra("monitorTam", monitor?.let { val p = Displays.sizeOf(it); "${p.x}×${p.y}" } ?: "")
            .putExtra("abierto", desk != null && !desk.isFinishing)
            .putExtra("dondeAbierto", if (desk == null) "" else if (desk.currentDisplayId() == Display.DEFAULT_DISPLAY) "celular" else "monitor")
            .putExtra("version", runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "")
            .putExtra("ventanas", runCatching { WindowControls.state.value.all.mapNotNull { it.pkg }.distinct().size }.getOrDefault(0))
        runCatching { ctx.sendBroadcast(i) }
    }
}

/** Jarvis → Nexo: la respuesta a una pregunta hecha desde el escritorio. */
class JarvisRespuestaReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Jarvis.ACCION_RESPUESTA) return
        val texto = intent.getStringExtra("texto")?.trim().orEmpty()
        if (texto.isEmpty()) return
        Jarvis.recibir(intent.getStringExtra("pregunta").orEmpty(), texto)
    }
}

/**
 * La puerta de Nexo para Jarvis (invisible). Intent: action «com.nexo.escritorio.ORDEN» con
 *  · accion: abrir | aqui | mostrar | inicio | panel | ajustes | touchpad | cerrar | buscar | app | estado
 *  · app (nombre o paquete) y modo (ventana | maximizada | izquierda | derecha | completa) para «app»
 *  · q para «buscar», id para emparejar la respuesta.
 * Solo abre cosas: no desinstala, no paga, no escribe en otras apps.
 */
class NexoApiActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.getStringExtra("id")
        val esperar = runCatching { manejar(intent ?: Intent(), id) }.getOrElse {
            Jarvis.responder(this, id, false, "Nexo no pudo hacerlo: ${it.message ?: it.javaClass.simpleName}")
            0L
        }
        if (esperar <= 0L) finish()
    }

    /** → milisegundos que hay que esperar antes de cerrar (cuando primero se abre el escritorio). */
    private fun manejar(i: Intent, id: String?): Long {
        val accion = i.getStringExtra("accion")?.lowercase(Locale.ROOT) ?: "abrir"
        val monitor = Displays.external(this)
        val desk = DesktopActivity.current?.get()?.takeIf { !it.isFinishing }
        val sinMonitor = "No veo ningún monitor ni TV conectado. Conecta el cable o la pantalla inalámbrica, o pide el escritorio en el celular."

        fun abrirEscritorio(action: String? = null): Boolean = when {
            desk != null -> Displays.bringDesktop(this, action) || run { desk.handleAction(action); true }
            monitor != null -> Displays.openDesktop(this, monitor, action)
            else -> false
        }

        when (accion) {
            "estado" -> Jarvis.responder(this, id, true, "")
            "abrir" -> when {
                monitor == null && desk == null -> Jarvis.responder(this, id, false, sinMonitor, "sin_monitor")
                abrirEscritorio() -> Jarvis.responder(this, id, true, "Escritorio abierto en ${monitor?.name ?: "la pantalla"}.")
                else -> Jarvis.responder(this, id, false, "No pude abrir el escritorio en el monitor.")
            }
            "aqui" -> {
                Displays.openDesktopHere(this)
                Jarvis.responder(this, id, true, "Escritorio abierto en el celular (para duplicar en la TV).")
            }
            "mostrar", "inicio", "panel", "ajustes" -> {
                val a = when (accion) {
                    "inicio" -> DesktopActivity.ACTION_START
                    "panel" -> DesktopActivity.ACTION_PANEL
                    "ajustes" -> DesktopActivity.ACTION_SETTINGS
                    else -> null
                }
                if (abrirEscritorio(a)) Jarvis.responder(this, id, true, when (accion) {
                    "inicio" -> "Menú de inicio abierto."
                    "panel" -> "Centro de control abierto."
                    "ajustes" -> "Ajustes de Nexo abiertos."
                    else -> "Listo: el escritorio al frente."
                }) else Jarvis.responder(this, id, false, sinMonitor, "sin_monitor")
            }
            "touchpad" -> {
                DesktopActions(this) { desk?.currentDisplayId() ?: Display.DEFAULT_DISPLAY }.touchpad()
                Jarvis.responder(this, id, true, "Touchpad abierto en el celular.")
            }
            "cerrar" -> {
                if (desk != null) { desk.finish(); Jarvis.responder(this, id, true, "Cerré el escritorio.") }
                else Jarvis.responder(this, id, true, "El escritorio no estaba abierto.")
            }
            "buscar" -> {
                val q = i.getStringExtra("q")?.trim().orEmpty()
                if (q.isEmpty()) { Jarvis.responder(this, id, false, "¿Qué busco?"); return 0 }
                if (desk == null && monitor == null) { Jarvis.responder(this, id, false, sinMonitor, "sin_monitor"); return 0 }
                return despues(desk == null, { abrirEscritorio() }) { d ->
                    d.webSearch(q); Jarvis.responder(this, id, true, "Busqué «$q» en el monitor.")
                }
            }
            "app" -> {
                val nombre = i.getStringExtra("app")?.trim().orEmpty()
                val app = buscarApp(nombre)
                if (app == null) { Jarvis.responder(this, id, false, "No encontré la app «$nombre» en el celular.", "sin_app"); return 0 }
                if (desk == null && monitor == null) { Jarvis.responder(this, id, false, sinMonitor, "sin_monitor"); return 0 }
                val modo = modo(i.getStringExtra("modo"))
                return despues(desk == null, { abrirEscritorio() }) { d ->
                    d.launch(app, modo)
                    Jarvis.responder(this, id, true, "Abrí ${app.label}${nombreModo(modo)} en el monitor.")
                }
            }
            else -> Jarvis.responder(this, id, false, "Nexo no conoce la orden «$accion».")
        }
        return 0
    }

    /** Hace algo en el escritorio; si no estaba abierto, primero lo abre y espera a que esté listo. */
    private fun despues(abrirAntes: Boolean, abrir: () -> Boolean, hacer: (DesktopActions) -> Unit): Long {
        if (!abrirAntes) {
            hacer(acciones())
            return 0
        }
        if (!abrir()) {
            Jarvis.responder(this, intent?.getStringExtra("id"), false, "No pude abrir el escritorio en el monitor.")
            return 0
        }
        var intentos = 0
        val paso = object : Runnable {
            override fun run() {
                val listo = DesktopActivity.current?.get() != null && WindowLauncher.screenW > 0
                if (listo || intentos++ > 12) {
                    runCatching { hacer(acciones()) }
                    finish()
                } else handler.postDelayed(this, 250)
            }
        }
        handler.postDelayed(paso, 600)
        return 4000
    }

    private fun acciones(): DesktopActions =
        DesktopActions(applicationContext) {
            DesktopActivity.current?.get()?.currentDisplayId() ?: Displays.external(this)?.displayId ?: Display.DEFAULT_DISPLAY
        }

    private fun buscarApp(nombre: String): AppEntry? {
        if (nombre.isEmpty()) return null
        val apps = AppRepository.apps.value
        apps.firstOrNull { it.packageName.equals(nombre, ignoreCase = true) }?.let { return it }
        val n = norm(nombre)
        apps.firstOrNull { norm(it.label) == n }?.let { return it }
        return com.nexo.escritorio.ui.searchApps(apps, nombre).firstOrNull()
    }

    private fun norm(s: String) =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase(Locale.ROOT).trim()

    private fun modo(m: String?): WindowMode = when (norm(m ?: "")) {
        "ventana", "window" -> WindowMode.WINDOW
        "maximizada", "maximizado", "grande", "maximized" -> WindowMode.MAXIMIZED
        "izquierda", "left" -> WindowMode.LEFT
        "derecha", "right" -> WindowMode.RIGHT
        "completa", "pantalla completa", "fullscreen" -> WindowMode.FULLSCREEN
        else -> WindowMode.DEFAULT
    }

    private fun nombreModo(m: WindowMode) = when (m) {
        WindowMode.WINDOW -> " en ventana"
        WindowMode.MAXIMIZED -> " maximizada"
        WindowMode.LEFT -> " a la izquierda"
        WindowMode.RIGHT -> " a la derecha"
        WindowMode.FULLSCREEN -> " a pantalla completa"
        else -> ""
    }
}
