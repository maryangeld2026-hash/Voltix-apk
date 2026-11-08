package com.nexo.escritorio

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nexo.escritorio.ui.NexoTheme
import com.nexo.escritorio.ui.TouchpadScreen
import com.nexo.escritorio.ui.accentColor
import kotlin.math.abs
import kotlin.math.hypot

/** El teléfono se convierte en touchpad y teclado para el escritorio del monitor. */
class TouchpadActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // El botón «Atrás» del touchpad se envía al monitor; aquí no debe cerrar el touchpad.
        onBackPressedDispatcher.addCallback(this) { }
        setContent {
            NexoTheme(accentColor(NexoPrefs.accent)) {
                TouchpadScreen(
                    onEnableAssistant = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onClose = { finish() },
                )
            }
        }
    }

    override fun onStop() {
        NexoAccessibility.get()?.stopPointer()
        super.onStop()
    }
}

/**
 * Superficie del touchpad (gestos como en un portátil):
 * un dedo mueve, tocar = clic, doble toque = doble clic, mantener = menú,
 * dos dedos = desplazar, toque con dos dedos = clic derecho.
 */
@SuppressLint("ViewConstructor")
class TouchpadView(ctx: Context, private val service: () -> NexoAccessibility?) : View(ctx) {
    private val handler = Handler(Looper.getMainLooper())
    private val slop = 10f * resources.displayMetrics.density

    private var downTime = 0L
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var maxPointers = 1
    private var moved = false
    private var longPressed = false
    private var scrollAccX = 0f
    private var scrollAccY = 0f
    private var lastScroll = 0L
    private var pendingTap: Runnable? = null

    private val longPress = Runnable {
        if (!moved && maxPointers == 1) {
            longPressed = true
            service()?.longPress()
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        }
    }

    private fun avgX(e: MotionEvent): Float { var s = 0f; for (i in 0 until e.pointerCount) s += e.getX(i); return s / e.pointerCount }
    private fun avgY(e: MotionEvent): Float { var s = 0f; for (i in 0 until e.pointerCount) s += e.getY(i); return s / e.pointerCount }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val s = service() ?: return true
        val speed = NexoPrefs.pointerSpeed
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTime = SystemClock.uptimeMillis()
                downX = e.x; downY = e.y
                lastX = e.x; lastY = e.y
                maxPointers = 1
                moved = false
                longPressed = false
                handler.postDelayed(longPress, 550)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                maxPointers = maxOf(maxPointers, e.pointerCount)
                lastX = avgX(e); lastY = avgY(e)
                scrollAccX = 0f; scrollAccY = 0f
                handler.removeCallbacks(longPress)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // Evita un salto del puntero al levantar uno de los dedos.
                lastX = avgX(e); lastY = avgY(e)
            }
            MotionEvent.ACTION_MOVE -> {
                val x = avgX(e)
                val y = avgY(e)
                if (!moved && hypot(e.x - downX, e.y - downY) > slop) {
                    moved = true
                    handler.removeCallbacks(longPress)
                }
                val dx = x - lastX
                val dy = y - lastY
                lastX = x; lastY = y
                if (e.pointerCount == 1 && maxPointers == 1) {
                    // Aceleración suave: los movimientos rápidos llegan más lejos.
                    val accel = 1f + (abs(dx) + abs(dy)) / 40f
                    s.moveBy(dx * speed * accel, dy * speed * accel)
                } else if (e.pointerCount >= 2) {
                    moved = true
                    scrollAccX += dx
                    scrollAccY += dy
                    val now = SystemClock.uptimeMillis()
                    if (now - lastScroll > 90 && (abs(scrollAccX) > 4 || abs(scrollAccY) > 4)) {
                        s.scroll(scrollAccX * 3f, scrollAccY * 3f)
                        scrollAccX = 0f; scrollAccY = 0f
                        lastScroll = now
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPress)
                val quick = SystemClock.uptimeMillis() - downTime < 250
                if (!moved && !longPressed && quick) {
                    if (maxPointers >= 2) {
                        s.longPress()
                    } else {
                        val pending = pendingTap
                        if (pending != null) {
                            handler.removeCallbacks(pending)
                            pendingTap = null
                            s.doubleClick()
                        } else {
                            val r = Runnable { pendingTap = null; s.click() }
                            pendingTap = r
                            handler.postDelayed(r, 230)
                        }
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(longPress)
        }
        return true
    }
}
