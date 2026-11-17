package com.nexo.escritorio.pc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.usb.UsbAccessory
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import com.nexo.escritorio.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.security.SecureRandom

/** Cómo va la conexión con el PC (para la pantalla de Nexo y para Jarvis). */
data class PcEstado(
    val activo: Boolean = false,
    val clave: String = "",
    val puerto: Int = PcServer.PUERTO,
    val direcciones: List<Pair<String, String>> = emptyList(),
    val navegadores: Int = 0,
    val programa: Boolean = false,
    val aviso: String = "",
) {
    /** La dirección para escribir en el navegador del PC (la del cable USB primero). */
    val url: String get() = direcciones.firstOrNull()?.let { "http://${it.second}:$puerto/?k=$clave" } ?: ""
}

/**
 * Compartir el escritorio con un PC por el cable USB (o por Wi‑Fi):
 *  · navegador: el PC abre la dirección que muestra el teléfono (con «Módem USB» encendido no hace falta internet);
 *  · programa «Nexo PC»: por el cable, en modo accesorio (AOA), sin red.
 * La imagen sale de la pantalla del teléfono (con el escritorio de Nexo abierto en el teléfono, se ve el escritorio).
 */
class PcService : Service() {

    companion object {
        const val EXTRA_CODIGO = "codigo"
        const val EXTRA_DATOS = "datos"
        const val ACCION_PARAR = "com.nexo.escritorio.PC_PARAR"
        private const val CANAL = "nexo_pc"
        private const val ID = 4242

        private val _estado = MutableStateFlow(PcEstado())
        val estado: StateFlow<PcEstado> = _estado

        /** Un programa «Nexo PC» conectado por el cable antes de dar el permiso de la pantalla. */
        @Volatile var accesorioPendiente: UsbAccessory? = null
        @Volatile private var actual: PcService? = null

        fun conectarAccesorio(acc: UsbAccessory): Boolean {
            val s = actual ?: run { accesorioPendiente = acc; return false }
            s.abrirAccesorio(acc)
            return true
        }

        fun parar(ctx: Context) {
            ctx.startService(Intent(ctx, PcService::class.java).setAction(ACCION_PARAR))
        }
    }

    private var captura: PcCapture? = null
    private var servidor: PcServer? = null
    private var accesorio: PcAccesorio? = null
    private val entrada = PcInput { captura?.let { it.screenW to it.screenH } ?: (1080 to 1920) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACCION_PARAR) { stopSelf(); return START_NOT_STICKY }
        val codigo = intent?.getIntExtra(EXTRA_CODIGO, 0) ?: 0
        @Suppress("DEPRECATION")
        val datos: Intent? = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(EXTRA_DATOS, Intent::class.java) else intent?.getParcelableExtra(EXTRA_DATOS)
        if (datos == null || captura != null) return START_NOT_STICKY
        val clave = String.format("%06d", SecureRandom().nextInt(1_000_000))
        // primero en primer plano (Android 14 lo exige antes de usar el permiso de la pantalla)
        val n = notificacion(clave)
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(ID, n)
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val proj = runCatching { mpm.getMediaProjection(codigo, datos) }.getOrNull()
        if (proj == null) { _estado.value = PcEstado(aviso = "Android no dio el permiso para compartir la pantalla."); stopSelf(); return START_NOT_STICKY }
        actual = this
        val srv = PcServer(this, clave, ::mensaje) { nav -> _estado.value = _estado.value.copy(navegadores = nav) }
        val puerto = runCatching { srv.start() }.getOrElse { PcServer.PUERTO }
        servidor = srv
        val cap = PcCapture(this, proj) { jpeg, w, h ->
            servidor?.cuadro(jpeg)
            accesorio?.cuadro(jpeg)
            val inf = info(w, h)
            if (inf != srv.info) srv.info = inf
        }
        captura = cap
        cap.start()
        _estado.value = PcEstado(activo = true, clave = clave, puerto = puerto, direcciones = PcServer.direcciones())
        accesorioPendiente?.let { accesorioPendiente = null; abrirAccesorio(it) }
        reloj.postDelayed(refrescar, 3000)
        return START_NOT_STICKY
    }

    private fun info(w: Int, h: Int) = JSONObject().put("t", "info").put("w", w).put("h", h)
        .put("shizuku", com.nexo.escritorio.TaskControl.ready.value)
        .put("asistente", com.nexo.escritorio.NexoAccessibility.get() != null)
        .put("modelo", Build.MANUFACTURER + " " + Build.MODEL).toString()

    private fun mensaje(s: String): String? = runCatching { entrada.handle(JSONObject(s)) }.getOrNull()

    fun abrirAccesorio(acc: UsbAccessory) {
        accesorio?.cerrar()
        val c = captura
        val a = PcAccesorio(this, acc, ::mensaje) {
            _estado.value = _estado.value.copy(programa = false)
        }
        if (a.start(info(c?.frameW ?: 0, c?.frameH ?: 0))) {
            accesorio = a
            _estado.value = _estado.value.copy(programa = true)
            c?.lastJpeg?.let { a.cuadro(it) }
        }
    }

    /** Las direcciones cambian al encender «Módem USB» o cambiar de Wi‑Fi: se vuelven a leer cada 3 s. */
    private val reloj = android.os.Handler(android.os.Looper.getMainLooper())
    private val refrescar = object : Runnable {
        override fun run() {
            if (captura == null) return
            val d = PcServer.direcciones()
            if (d != _estado.value.direcciones) _estado.value = _estado.value.copy(direcciones = d)
            reloj.postDelayed(this, 3000)
        }
    }

    private fun notificacion(clave: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CANAL) == null) {
            nm.createNotificationChannel(NotificationChannel(CANAL, "Escritorio en el PC", NotificationManager.IMPORTANCE_LOW))
        }
        val parar = PendingIntent.getService(this, 1, Intent(this, PcService::class.java).setAction(ACCION_PARAR), PendingIntent.FLAG_IMMUTABLE)
        val abrir = PendingIntent.getActivity(this, 2, Intent(this, PcActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CANAL) else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("Nexo: escritorio en el PC")
            .setContentText("Clave $clave · toca para ver la dirección")
            .setContentIntent(abrir)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Detener", parar).build())
            .build()
    }

    override fun onDestroy() {
        reloj.removeCallbacksAndMessages(null)
        actual = null
        accesorio?.cerrar(); accesorio = null
        servidor?.stop(); servidor = null
        captura?.stop(); captura = null
        _estado.value = PcEstado()
        super.onDestroy()
    }
}
