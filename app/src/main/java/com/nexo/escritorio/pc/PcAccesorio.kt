package com.nexo.escritorio.pc

import android.content.Context
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.ParcelFileDescriptor
import java.io.DataInputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicReference

/**
 * El cable USB directo al programa «Nexo PC» (modo accesorio de Android, AOA): sin red, sin depuración USB.
 * El programa del PC pide al teléfono que entre en modo accesorio («Nexo» / «Escritorio»), Android abre Nexo
 * y por el cable van los mensajes: [tipo 1 byte][largo 4 bytes][datos].
 *   teléfono → PC: 1 = cuadro JPEG · 2 = información (JSON) · 4 = aviso (texto)
 *   PC → teléfono: 3 = ratón o teclado (JSON, igual que el navegador)
 */
class PcAccesorio(
    private val ctx: Context,
    private val acc: UsbAccessory,
    private val onMensaje: (String) -> String?,
    private val onFin: () -> Unit,
) {
    private var pfd: ParcelFileDescriptor? = null
    private var out: FileOutputStream? = null
    @Volatile var abierto = false; private set
    private val pendiente = AtomicReference<ByteArray?>(null)
    private val lock = Object()
    private val escribir = Object()

    fun start(info: String): Boolean {
        val um = ctx.getSystemService(UsbManager::class.java) ?: return false
        val f = runCatching { um.openAccessory(acc) }.getOrNull() ?: return false
        pfd = f
        out = FileOutputStream(f.fileDescriptor)
        abierto = true
        enviar(2, info.toByteArray())
        Thread({ leer(FileInputStream(f.fileDescriptor)) }, "nexo-aoa-lee").start()
        Thread({
            try {
                while (abierto) {
                    val j = pendiente.getAndSet(null)
                    if (j == null) { synchronized(lock) { if (pendiente.get() == null && abierto) lock.wait(1000) }; continue }
                    enviar(1, j)
                }
            } catch (_: Exception) { cerrar() }
        }, "nexo-aoa-envia").start()
        return true
    }

    fun cuadro(jpeg: ByteArray) {
        pendiente.set(jpeg)
        synchronized(lock) { lock.notifyAll() }
    }

    fun aviso(s: String) = runCatching { enviar(4, s.toByteArray()) }

    private fun enviar(tipo: Int, datos: ByteArray) {
        val o = out ?: return
        synchronized(escribir) {
            val cab = byteArrayOf(tipo.toByte(), (datos.size ushr 24).toByte(), (datos.size ushr 16).toByte(), (datos.size ushr 8).toByte(), datos.size.toByte())
            o.write(cab)
            // en trozos de 16 KB: algunos teléfonos fallan con escrituras grandes en modo accesorio
            var i = 0
            while (i < datos.size) { val n = minOf(16384, datos.size - i); o.write(datos, i, n); i += n }
            o.flush()
        }
    }

    private fun leer(inp: FileInputStream) {
        // Android entrega el accesorio en paquetes: se lee con un búfer de 16 KB y se arma cada mensaje
        val buf = ByteArray(16384)
        val acum = java.io.ByteArrayOutputStream()
        try {
            while (abierto) {
                val n = inp.read(buf)
                if (n < 0) break
                if (n == 0) continue
                acum.write(buf, 0, n)
                var d = acum.toByteArray()
                var p = 0
                while (d.size - p >= 5) {
                    val tipo = d[p].toInt() and 0xff
                    val len = ((d[p + 1].toInt() and 0xff) shl 24) or ((d[p + 2].toInt() and 0xff) shl 16) or ((d[p + 3].toInt() and 0xff) shl 8) or (d[p + 4].toInt() and 0xff)
                    if (len < 0 || len > 1_000_000) { p = d.size; break }
                    if (d.size - p - 5 < len) break
                    val msg = String(d, p + 5, len)
                    p += 5 + len
                    if (tipo == 3) onMensaje(msg)?.let { aviso(it) }
                }
                acum.reset()
                if (p < d.size) acum.write(d, p, d.size - p)
            }
        } catch (_: Exception) {
        }
        cerrar()
    }

    fun cerrar() {
        if (!abierto) return
        abierto = false
        synchronized(lock) { lock.notifyAll() }
        runCatching { out?.close() }
        runCatching { pfd?.close() }
        onFin()
    }

    @Suppress("unused")
    private fun nada(d: DataInputStream) = d
}
