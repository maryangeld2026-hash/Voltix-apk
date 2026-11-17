package com.nexo.escritorio.pc

import android.accessibilityservice.AccessibilityService
import android.graphics.Path
import android.os.IBinder
import android.os.SystemClock
import android.view.Display
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import com.nexo.escritorio.DesktopActivity
import com.nexo.escritorio.NexoAccessibility
import com.nexo.escritorio.TaskControl
import org.json.JSONObject
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Method
import kotlin.math.abs
import kotlin.math.hypot

/**
 * El ratón y el teclado del PC → el teléfono.
 *  · Con Shizuku: eventos reales (toques, rueda, todas las teclas y atajos), como un ratón y un teclado de verdad.
 *  · Sin Shizuku: con el asistente de Nexo (accesibilidad): toques, arrastres, mantener, desplazar y escribir texto.
 * x e y llegan entre 0 y 1 (proporción de la imagen) y se pasan al tamaño real de la pantalla.
 */
class PcInput(private val size: () -> Pair<Int, Int>) {

    // ---------- Shizuku: inyectar eventos como el shell ----------
    private object Shell {
        private var im: Any? = null
        private var inject: Method? = null
        fun ready(): Boolean = TaskControl.ready.value
        fun send(ev: InputEvent): Boolean {
            if (!ready()) return false
            return runCatching {
                if (im == null) {
                    val b: IBinder = SystemServiceHelper.getSystemService("input") ?: return false
                    val stub = Class.forName("android.hardware.input.IInputManager\$Stub")
                    val obj = stub.getMethod("asInterface", IBinder::class.java).invoke(null, ShizukuBinderWrapper(b))
                    im = obj
                    inject = obj!!.javaClass.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
                }
                inject!!.invoke(im, ev, 0) as Boolean
            }.getOrElse { im = null; inject = null; false }
        }
    }

    private var downX = 0f
    private var downY = 0f
    private var downT = 0L
    private var downBtn = 0
    private var isDown = false
    private var shellDown = false
    private val path = ArrayList<Pair<Float, Float>>()

    private fun px(j: JSONObject): Pair<Float, Float> {
        val (w, h) = size()
        val x = (j.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f)) * (w - 1)
        val y = (j.optDouble("y", 0.5).toFloat().coerceIn(0f, 1f)) * (h - 1)
        return x to y
    }

    private fun acc(): NexoAccessibility? = NexoAccessibility.get()

    /** → mensaje para el PC si hace falta avisar algo (por ejemplo, que falta el asistente). */
    fun handle(j: JSONObject): String? {
        return when (j.optString("t")) {
            "down" -> { down(j); null }
            "move" -> { move(j); null }
            "up" -> up(j)
            "wheel" -> wheel(j)
            "key" -> key(j)
            "text" -> text(j.optString("s"))
            "btn" -> button(j.optString("a"))
            else -> null
        }
    }

    private fun motion(action: Int, x: Float, y: Float, downTime: Long, source: Int = InputDevice.SOURCE_TOUCHSCREEN): Boolean {
        val ev = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        ev.source = source
        val ok = Shell.send(ev)
        ev.recycle()
        return ok
    }

    private fun down(j: JSONObject) {
        val (x, y) = px(j)
        downX = x; downY = y; downT = SystemClock.uptimeMillis(); downBtn = j.optInt("b", 0); isDown = true
        path.clear(); path.add(x to y)
        shellDown = downBtn == 0 && Shell.ready() && motion(MotionEvent.ACTION_DOWN, x, y, downT)
    }

    private fun move(j: JSONObject) {
        if (!isDown) return
        val (x, y) = px(j)
        if (shellDown) { motion(MotionEvent.ACTION_MOVE, x, y, downT); return }
        val last = path.last()
        if (hypot(x - last.first, y - last.second) > 6f && path.size < 80) path.add(x to y)
    }

    private fun up(j: JSONObject): String? {
        if (!isDown) return null
        isDown = false
        val (x, y) = px(j)
        val dur = SystemClock.uptimeMillis() - downT
        if (shellDown) { motion(MotionEvent.ACTION_UP, x, y, downT); shellDown = false; return null }
        val a = acc() ?: return FALTA_ASISTENTE
        if (path.last() != (x to y)) path.add(x to y)
        val moved = hypot(x - downX, y - downY) > 12f
        val p = Path()
        p.moveTo(path[0].first, path[0].second)
        if (!moved) {
            // clic derecho o mantener: pulsación larga (abre el menú en Nexo y en muchas apps)
            val long = downBtn == 2 || dur > 550
            a.gesture(Display.DEFAULT_DISPLAY, p, if (long) 650 else 40)
        } else {
            for (i in 1 until path.size) p.lineTo(path[i].first, path[i].second)
            a.gesture(Display.DEFAULT_DISPLAY, p, dur.coerceIn(120L, 3000L))
        }
        return null
    }

    private fun wheel(j: JSONObject): String? {
        val (x, y) = px(j)
        val dy = j.optDouble("dy", 0.0).toFloat()
        if (Shell.ready()) {
            val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE })
            val coords = arrayOf(MotionEvent.PointerCoords().apply {
                this.x = x; this.y = y
                setAxisValue(MotionEvent.AXIS_VSCROLL, if (dy > 0) -1f else 1f)
            })
            val t = SystemClock.uptimeMillis()
            val ev = MotionEvent.obtain(t, t, MotionEvent.ACTION_SCROLL, 1, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
            val ok = Shell.send(ev)
            ev.recycle()
            if (ok) return null
        }
        val a = acc() ?: return FALTA_ASISTENTE
        val (_, h) = size()
        val dist = (h * 0.25f) * (if (dy > 0) -1 else 1)
        val p = Path().apply { moveTo(x, y); lineTo(x, (y + dist).coerceIn(1f, h - 2f)) }
        a.gesture(Display.DEFAULT_DISPLAY, p, 160)
        return null
    }

    private fun meta(j: JSONObject): Int {
        var m = 0
        if (j.optBoolean("ctrl")) m = m or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (j.optBoolean("shift")) m = m or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        if (j.optBoolean("alt")) m = m or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        if (j.optBoolean("meta")) m = m or KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON
        return m
    }

    private fun shellKey(code: Int, meta: Int): Boolean {
        val t = SystemClock.uptimeMillis()
        val d = KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, meta, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD)
        val u = KeyEvent(t, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0, meta, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD)
        return Shell.send(d) && Shell.send(u)
    }

    private fun key(j: JSONObject): String? {
        val code = KEYS[j.optString("code")] ?: 0
        val k = j.optString("key")
        val m = meta(j)
        val sinAtajo = (m and (KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON or KeyEvent.META_META_ON)) == 0
        // una letra o símbolo: se escribe el carácter (así sirve cualquier distribución de teclado: ñ, tildes…)
        if (k.length == 1 && sinAtajo) return text(k)
        // con Shizuku: la tecla de verdad (atajos incluidos)
        if (Shell.ready() && code != 0 && shellKey(code, m)) return null
        // sin Shizuku: lo que se puede con el asistente
        val a = acc() ?: return FALTA_ASISTENTE
        when (j.optString("code")) {
            "Backspace" -> a.backspace(Display.DEFAULT_DISPLAY)
            "Enter", "NumpadEnter" -> a.enter(Display.DEFAULT_DISPLAY)
            "Escape" -> a.globalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            "MetaLeft", "MetaRight" -> DesktopActivity.current?.get()?.let { d -> d.runOnUiThread { d.onGlobalWinKey() } }
            else -> if (k.length == 1 && (m and (KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON or KeyEvent.META_META_ON)) == 0) {
                if (!a.typeText(Display.DEFAULT_DISPLAY, k)) return "Toca primero un campo de texto para escribir."
            }
        }
        return null
    }

    private fun text(s: String): String? {
        if (s.isEmpty()) return null
        if (Shell.ready()) {
            val evs = runCatching { KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(s.toCharArray()) }.getOrNull()
            if (evs != null && evs.all { Shell.send(it) }) return null
        }
        val a = acc()
        if (a != null && a.typeText(Display.DEFAULT_DISPLAY, s)) return null
        return if (a == null) FALTA_ASISTENTE else "Toca primero un campo de texto para escribir."
    }

    private fun button(a: String): String? {
        if (Shell.ready()) {
            val code = when (a) { "home" -> KeyEvent.KEYCODE_HOME; "back" -> KeyEvent.KEYCODE_BACK; "recents" -> KeyEvent.KEYCODE_APP_SWITCH; else -> 0 }
            if (code != 0 && shellKey(code, 0)) return null
        }
        if (a == "start") {
            val d = DesktopActivity.current?.get() ?: return "Abre primero el escritorio en el teléfono."
            d.runOnUiThread { d.onGlobalWinKey() }
            return null
        }
        val s = acc() ?: return FALTA_ASISTENTE
        when (a) {
            "home" -> s.globalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            "back" -> s.globalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            "recents" -> s.globalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            "notifs" -> s.globalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
        }
        return null
    }

    companion object {
        const val FALTA_ASISTENTE = "Para usar el ratón y el teclado del PC activa el asistente de Nexo (accesibilidad) o Shizuku en el teléfono."

        /** KeyboardEvent.code del navegador → tecla de Android. */
        val KEYS: Map<String, Int> = HashMap<String, Int>().apply {
            for (c in 'A'..'Z') put("Key$c", KeyEvent.KEYCODE_A + (c - 'A'))
            for (d in 0..9) { put("Digit$d", KeyEvent.KEYCODE_0 + d); put("Numpad$d", KeyEvent.KEYCODE_NUMPAD_0 + d) }
            for (f in 1..12) put("F$f", KeyEvent.KEYCODE_F1 + (f - 1))
            put("Enter", KeyEvent.KEYCODE_ENTER); put("NumpadEnter", KeyEvent.KEYCODE_NUMPAD_ENTER)
            put("Backspace", KeyEvent.KEYCODE_DEL); put("Delete", KeyEvent.KEYCODE_FORWARD_DEL)
            put("Tab", KeyEvent.KEYCODE_TAB); put("Space", KeyEvent.KEYCODE_SPACE); put("Escape", KeyEvent.KEYCODE_ESCAPE)
            put("ArrowUp", KeyEvent.KEYCODE_DPAD_UP); put("ArrowDown", KeyEvent.KEYCODE_DPAD_DOWN)
            put("ArrowLeft", KeyEvent.KEYCODE_DPAD_LEFT); put("ArrowRight", KeyEvent.KEYCODE_DPAD_RIGHT)
            put("Home", KeyEvent.KEYCODE_MOVE_HOME); put("End", KeyEvent.KEYCODE_MOVE_END)
            put("PageUp", KeyEvent.KEYCODE_PAGE_UP); put("PageDown", KeyEvent.KEYCODE_PAGE_DOWN)
            put("Insert", KeyEvent.KEYCODE_INSERT); put("CapsLock", KeyEvent.KEYCODE_CAPS_LOCK)
            put("MetaLeft", KeyEvent.KEYCODE_META_LEFT); put("MetaRight", KeyEvent.KEYCODE_META_RIGHT)
            put("Minus", KeyEvent.KEYCODE_MINUS); put("Equal", KeyEvent.KEYCODE_EQUALS)
            put("BracketLeft", KeyEvent.KEYCODE_LEFT_BRACKET); put("BracketRight", KeyEvent.KEYCODE_RIGHT_BRACKET)
            put("Backslash", KeyEvent.KEYCODE_BACKSLASH); put("Semicolon", KeyEvent.KEYCODE_SEMICOLON)
            put("Quote", KeyEvent.KEYCODE_APOSTROPHE); put("Comma", KeyEvent.KEYCODE_COMMA)
            put("Period", KeyEvent.KEYCODE_PERIOD); put("Slash", KeyEvent.KEYCODE_SLASH); put("Backquote", KeyEvent.KEYCODE_GRAVE)
            put("NumpadAdd", KeyEvent.KEYCODE_NUMPAD_ADD); put("NumpadSubtract", KeyEvent.KEYCODE_NUMPAD_SUBTRACT)
            put("NumpadMultiply", KeyEvent.KEYCODE_NUMPAD_MULTIPLY); put("NumpadDivide", KeyEvent.KEYCODE_NUMPAD_DIVIDE)
            put("NumpadDecimal", KeyEvent.KEYCODE_NUMPAD_DOT)
        }
    }
}

@Suppress("unused")
private fun near(a: Float, b: Float) = abs(a - b) < 1f
