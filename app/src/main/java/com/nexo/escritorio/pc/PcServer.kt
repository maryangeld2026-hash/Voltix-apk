package com.nexo.escritorio.pc

import android.content.Context
import android.util.Base64
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * Un servidor web mínimo dentro de Nexo: la página para el navegador del PC (assets/pc.html) y un WebSocket que
 * manda la imagen (JPEG) y recibe el ratón y el teclado (JSON). Sirve por la red del cable USB («Módem USB») o por Wi‑Fi.
 * Solo entra quien tiene la clave de 6 cifras que muestra el teléfono.
 */
class PcServer(
    private val ctx: Context,
    val clave: String,
    private val onMensaje: (String) -> String?,
    private val onClientes: (Int) -> Unit,
) {
    private var server: ServerSocket? = null
    @Volatile private var vivo = false
    private val clientes = CopyOnWriteArrayList<Cliente>()
    @Volatile var info: String = "{}"

    fun start(): Int {
        val s = ServerSocket()
        s.reuseAddress = true
        var puerto = PUERTO
        while (true) {
            try { s.bind(InetSocketAddress(puerto)); break } catch (e: Exception) { if (++puerto > PUERTO + 10) throw e }
        }
        server = s
        vivo = true
        Thread({
            while (vivo) {
                val c = try { s.accept() } catch (_: Exception) { break }
                Thread({ atender(c) }, "nexo-pc-http").start()
            }
        }, "nexo-pc-servidor").start()
        return puerto
    }

    val puerto: Int get() = server?.localPort ?: PUERTO

    fun stop() {
        vivo = false
        runCatching { server?.close() }
        clientes.forEach { it.cerrar() }
        clientes.clear()
    }

    /** Un cuadro nuevo para todos los navegadores conectados (cada uno toma el último: si va lento, se salta cuadros). */
    fun cuadro(jpeg: ByteArray) { ultimo.set(jpeg); clientes.forEach { it.pendiente.set(jpeg) ; synchronized(it.lock) { it.lock.notifyAll() } } }

    fun aviso(texto: String) { val j = org.json.JSONObject().put("t", "aviso").put("s", texto).toString(); clientes.forEach { it.texto(j) } }

    private fun atender(sock: Socket) {
        try {
            sock.tcpNoDelay = true
            sock.soTimeout = 15000
            val inp = BufferedInputStream(sock.getInputStream())
            val out = sock.getOutputStream()
            val cab = leerCabecera(inp) ?: return sock.close()
            val primera = cab.first().split(" ")
            val ruta = primera.getOrNull(1) ?: "/"
            val h = cab.drop(1).mapNotNull { l -> l.indexOf(':').takeIf { it > 0 }?.let { l.substring(0, it).trim().lowercase() to l.substring(it + 1).trim() } }.toMap()
            val camino = ruta.substringBefore('?')
            val k = Regex("[?&]k=(\\d+)").find(ruta)?.groupValues?.get(1)
            when {
                camino == "/ws" && h["upgrade"].equals("websocket", true) -> {
                    if (k != clave) return responder(out, 403, "text/plain", "Clave incorrecta".toByteArray()).also { sock.close() }
                    val key = h["sec-websocket-key"] ?: return sock.close()
                    val acc = Base64.encodeToString(MessageDigest.getInstance("SHA-1").digest((key + GUID).toByteArray()), Base64.NO_WRAP)
                    out.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $acc\r\n\r\n".toByteArray())
                    out.flush()
                    sock.soTimeout = 0
                    websocket(sock, inp, out)
                }
                camino == "/" || camino == "/index.html" -> {
                    val html = ctx.assets.open("pc.html").use { it.readBytes() }
                    responder(out, 200, "text/html; charset=utf-8", html)
                    sock.close()
                }
                camino == "/cuadro.jpg" && k == clave -> {
                    val j = ultimo.get() ?: ByteArray(0)
                    responder(out, 200, "image/jpeg", j)
                    sock.close()
                }
                else -> { responder(out, 404, "text/plain", "No encontrado".toByteArray()); sock.close() }
            }
        } catch (_: Exception) {
            runCatching { sock.close() }
        }
    }

    /** El último cuadro (para quien se conecta: lo ve de una vez, aunque la pantalla no cambie). */
    val ultimo = AtomicReference<ByteArray?>(null)

    private fun leerCabecera(inp: InputStream): List<String>? {
        val lineas = ArrayList<String>()
        val sb = StringBuilder()
        var total = 0
        while (true) {
            val c = inp.read()
            if (c < 0) return null
            if (++total > 16384) return null
            if (c == '\n'.code) {
                val l = sb.toString().trimEnd('\r')
                sb.setLength(0)
                if (l.isEmpty()) return lineas
                lineas.add(l)
            } else sb.append(c.toChar())
        }
    }

    private fun responder(out: OutputStream, codigo: Int, tipo: String, cuerpo: ByteArray) {
        val estado = when (codigo) { 200 -> "OK"; 403 -> "Forbidden"; else -> "Not Found" }
        out.write("HTTP/1.1 $codigo $estado\r\nContent-Type: $tipo\r\nContent-Length: ${cuerpo.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(cuerpo)
        out.flush()
    }

    private inner class Cliente(val sock: Socket, val out: OutputStream) {
        val pendiente = AtomicReference<ByteArray?>(null)
        val lock = Object()
        @Volatile var abierto = true
        private val escribir = Object()

        fun marco(op: Int, datos: ByteArray) {
            synchronized(escribir) {
                val n = datos.size
                out.write(0x80 or op)
                when {
                    n < 126 -> out.write(n)
                    n < 65536 -> { out.write(126); out.write(n shr 8 and 0xff); out.write(n and 0xff) }
                    else -> { out.write(127); for (i in 7 downTo 0) out.write(((n.toLong() shr (8 * i)) and 0xff).toInt()) }
                }
                out.write(datos)
                out.flush()
            }
        }
        fun texto(s: String) { if (abierto) runCatching { marco(0x1, s.toByteArray()) }.onFailure { cerrar() } }
        fun cerrar() {
            if (!abierto) return
            abierto = false
            synchronized(lock) { lock.notifyAll() }
            runCatching { sock.close() }
            clientes.remove(this)
            onClientes(clientes.size)
        }
    }

    private fun websocket(sock: Socket, inp: InputStream, out: OutputStream) {
        val c = Cliente(sock, out)
        clientes.add(c)
        onClientes(clientes.size)
        c.texto(info)
        ultimo.get()?.let { c.pendiente.set(it) }
        // quien escribe los cuadros
        Thread({
            try {
                while (c.abierto) {
                    val j = c.pendiente.getAndSet(null)
                    if (j == null) { synchronized(c.lock) { if (c.pendiente.get() == null && c.abierto) c.lock.wait(1000) }; continue }
                    c.marco(0x2, j)
                }
            } catch (_: Exception) { c.cerrar() }
        }, "nexo-pc-envio").start()
        // lo que llega del navegador
        try {
            while (c.abierto) {
                val b0 = inp.read(); if (b0 < 0) break
                val b1 = inp.read(); if (b1 < 0) break
                val op = b0 and 0x0f
                var n = (b1 and 0x7f).toLong()
                if (n == 126L) n = ((inp.read() shl 8) or inp.read()).toLong()
                else if (n == 127L) { n = 0; repeat(8) { n = (n shl 8) or inp.read().toLong() } }
                if (n > 1_000_000) break
                val mask = if (b1 and 0x80 != 0) ByteArray(4).also { leerTodo(inp, it) } else null
                val datos = ByteArray(n.toInt()).also { leerTodo(inp, it) }
                if (mask != null) for (i in datos.indices) datos[i] = (datos[i].toInt() xor mask[i % 4].toInt()).toByte()
                when (op) {
                    0x8 -> break
                    0x9 -> c.marco(0xA, datos)
                    0x1 -> onMensaje(String(datos))?.let { c.texto(org.json.JSONObject().put("t", "aviso").put("s", it).toString()) }
                }
            }
        } catch (_: SocketTimeoutException) {
        } catch (_: Exception) {
        }
        c.cerrar()
    }

    private fun leerTodo(inp: InputStream, b: ByteArray) {
        var i = 0
        while (i < b.size) { val r = inp.read(b, i, b.size - i); if (r < 0) throw java.io.EOFException(); i += r }
    }

    companion object {
        const val PUERTO = 8686
        private const val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

        /** Las direcciones del teléfono: la del cable USB («Módem USB») primero. [nombre visible, ip] */
        fun direcciones(): List<Pair<String, String>> {
            val l = ArrayList<Pair<String, String>>()
            runCatching {
                for (ni in NetworkInterface.getNetworkInterfaces()) {
                    if (!ni.isUp || ni.isLoopback) continue
                    val n = ni.name.lowercase()
                    val tipo = when {
                        n.startsWith("rndis") || n.startsWith("usb") || n.startsWith("ncm") -> "Cable USB (Módem USB)"
                        n.startsWith("wlan") -> "Wi‑Fi"
                        n.startsWith("ap") || n.startsWith("swlan") || n.startsWith("softap") -> "Zona Wi‑Fi del teléfono"
                        n.startsWith("eth") -> "Ethernet"
                        else -> null
                    } ?: continue
                    for (a in ni.inetAddresses) if (a is Inet4Address && !a.isLoopbackAddress) l.add(tipo to a.hostAddress!!)
                }
            }
            return l.sortedBy { if (it.first.startsWith("Cable")) 0 else 1 }
        }
    }
}
