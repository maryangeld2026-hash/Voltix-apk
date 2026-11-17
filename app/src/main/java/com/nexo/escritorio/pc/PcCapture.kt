package com.nexo.escritorio.pc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.Display
import com.nexo.escritorio.Displays
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * La imagen de la pantalla del teléfono para el PC: MediaProjection → pantalla virtual → ImageReader → JPEG.
 * JPEG (y no H.264) porque el navegador del PC lo muestra sin nada especial, también por http en la red del cable
 * USB (WebCodecs solo funciona en https). Solo se generan cuadros cuando algo cambia en la pantalla.
 */
class PcCapture(
    private val ctx: Context,
    private val projection: MediaProjection,
    private val onFrame: (ByteArray, Int, Int) -> Unit,
) {
    @Volatile var maxWidth = 1600
    @Volatile var quality = 70
    @Volatile var maxFps = 24

    /** Tamaño real de la pantalla del teléfono (para pasar los clics del PC). */
    @Volatile var screenW = 0; private set
    @Volatile var screenH = 0; private set

    private val thread = HandlerThread("nexo-pc-captura").apply { start() }
    private val handler = Handler(thread.looper)
    private var vd: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var lastFrame = 0L
    private var bmp: Bitmap? = null
    private val out = ByteArrayOutputStream(256 * 1024)
    @Volatile private var running = false
    @Volatile var lastJpeg: ByteArray? = null; private set
    @Volatile var frameW = 0; private set
    @Volatile var frameH = 0; private set

    private val dm = ctx.getSystemService(DisplayManager::class.java)
    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) handler.post { resizeIfNeeded() }
        }
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
    }

    fun start() {
        running = true
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stop() }
        }, handler)
        handler.post { create() }
        dm?.registerDisplayListener(listener, handler)
    }

    private fun target(): Triple<Int, Int, Int> {
        val d = Displays.byId(ctx, Display.DEFAULT_DISPLAY)
        val p = if (d != null) Displays.sizeOf(d) else android.graphics.Point(1080, 2400)
        screenW = p.x
        screenH = p.y
        val k = minOf(1f, maxWidth.toFloat() / max(p.x, p.y))
        // múltiplos de 2 para los decodificadores
        val w = ((p.x * k).roundToInt() / 2) * 2
        val h = ((p.y * k).roundToInt() / 2) * 2
        val dpi = ctx.resources.displayMetrics.densityDpi
        return Triple(w, h, dpi)
    }

    private fun create() {
        val (w, h, dpi) = target()
        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3)
        r.setOnImageAvailableListener({ grab(it) }, handler)
        reader = r
        frameW = w
        frameH = h
        vd = projection.createVirtualDisplay(
            "nexo-pc", w, h, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            r.surface, null, handler,
        )
    }

    /** Se giró el teléfono o cambió su resolución: la misma pantalla virtual, con el nuevo tamaño (Android 14 no deja crear otra). */
    private fun resizeIfNeeded() {
        if (!running) return
        val (w, h, dpi) = target()
        if (w == frameW && h == frameH) return
        val old = reader
        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3)
        r.setOnImageAvailableListener({ grab(it) }, handler)
        reader = r
        frameW = w
        frameH = h
        bmp?.recycle(); bmp = null
        runCatching {
            vd?.resize(w, h, dpi)
            vd?.surface = r.surface
        }
        old?.close()
    }

    private fun grab(r: ImageReader) {
        val img = runCatching { r.acquireLatestImage() }.getOrNull() ?: return
        try {
            val now = SystemClock.uptimeMillis()
            if (now - lastFrame < 1000L / maxFps.coerceIn(1, 60)) return
            lastFrame = now
            val plane = img.planes[0]
            val pxStride = plane.pixelStride
            val rowW = plane.rowStride / pxStride
            var b = bmp
            if (b == null || b.width != rowW || b.height != img.height) {
                b?.recycle()
                b = Bitmap.createBitmap(rowW, img.height, Bitmap.Config.ARGB_8888)
                bmp = b
            }
            plane.buffer.rewind()
            b.copyPixelsFromBuffer(plane.buffer)
            val src = if (rowW != img.width) Bitmap.createBitmap(b, 0, 0, img.width, img.height) else b
            out.reset()
            src.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(30, 95), out)
            if (src !== b) src.recycle()
            val jpeg = out.toByteArray()
            lastJpeg = jpeg
            onFrame(jpeg, img.width, img.height)
        } catch (_: Throwable) {
        } finally {
            img.close()
        }
    }

    fun stop() {
        if (!running) return
        running = false
        runCatching { dm?.unregisterDisplayListener(listener) }
        handler.post {
            runCatching { vd?.release() }
            vd = null
            runCatching { reader?.close() }
            reader = null
            bmp?.recycle(); bmp = null
            thread.quitSafely()
        }
        runCatching { projection.stop() }
    }
}
