package com.nexo.escritorio

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.lang.ref.WeakReference

/**
 * Asistente de Nexo (servicio de accesibilidad, opcional). Da superpoderes al escritorio:
 * - Saber qué apps están abiertas de verdad en cada pantalla (para la barra de tareas).
 * - La tecla Windows funciona aunque estés dentro de otra app.
 * - Captura de pantalla y bloqueo.
 * - Usar el teléfono como touchpad y teclado para controlar el monitor.
 * No lee ni guarda el contenido de tus apps.
 */
class NexoAccessibility : AccessibilityService() {

    companion object {
        private var instance: WeakReference<NexoAccessibility>? = null
        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected

        /** Apps abiertas por pantalla: id de pantalla → paquetes (de atrás hacia delante). */
        private val _running = MutableStateFlow<Map<Int, List<String>>>(emptyMap())
        val running: StateFlow<Map<Int, List<String>>> = _running

        fun get(): NexoAccessibility? = instance?.get()

        fun isEnabled(ctx: Context): Boolean {
            val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            return list.split(':').any { it.startsWith(ctx.packageName + "/") }
        }

        fun screenshot(): Boolean {
            val s = get() ?: return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            // Se espera a que se cierre el panel para que no salga en la captura.
            s.handler.postDelayed({ s.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT) }, 450)
            return true
        }

        fun lock(): Boolean {
            val s = get() ?: return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            return s.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }

        fun back(): Boolean = get()?.performGlobalAction(GLOBAL_ACTION_BACK) ?: false

        /** El touchpad necesita Android 11 o superior. */
        val pointerSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = Runnable { refreshWindows() }

    override fun onServiceConnected() {
        instance = WeakReference(this)
        _connected.value = true
        refreshWindows()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return
        if (type == AccessibilityEvent.TYPE_WINDOWS_CHANGED || type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            handler.removeCallbacks(refreshRunnable)
            handler.postDelayed(refreshRunnable, 250)
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        stopPointer()
        _connected.value = false
        _running.value = emptyMap()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopPointer()
        _connected.value = false
        instance = null
        super.onDestroy()
    }

    // ---------- Apps abiertas ----------

    private fun windowsByDisplay(): Map<Int, List<AccessibilityWindowInfo>> =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val all = windowsOnAllDisplays
                val map = HashMap<Int, List<AccessibilityWindowInfo>>()
                for (i in 0 until all.size()) map[all.keyAt(i)] = all.valueAt(i)
                map
            } else {
                mapOf(Display.DEFAULT_DISPLAY to windows)
            }
        } catch (e: Exception) {
            emptyMap()
        }

    private fun refreshWindows() {
        val result = HashMap<Int, List<String>>()
        for ((displayId, list) in windowsByDisplay()) {
            val pkgs = ArrayList<String>()
            // La lista viene de delante hacia atrás; la invertimos para conservar el orden de apertura.
            for (w in list.asReversed()) {
                if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                val pkg = try { w.root?.packageName?.toString() } catch (e: Exception) { null } ?: continue
                if (pkg == packageName || pkg in pkgs) continue
                pkgs += pkg
            }
            result[displayId] = pkgs
        }
        _running.value = result
    }

    // ---------- Tecla Windows en cualquier app ----------

    private var metaDown = false
    private var metaCombo = false

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!NexoPrefs.globalWinKey) return false
        val code = event.keyCode
        val isMeta = code == KeyEvent.KEYCODE_META_LEFT || code == KeyEvent.KEYCODE_META_RIGHT
        if (isMeta) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                metaDown = true
                metaCombo = false
            } else if (event.action == KeyEvent.ACTION_UP) {
                if (metaDown && !metaCombo) handler.post { onWinKey() }
                metaDown = false
            }
            return false
        }
        if (metaDown && event.action == KeyEvent.ACTION_DOWN) {
            metaCombo = true
            if (code == KeyEvent.KEYCODE_D && DesktopActivity.current?.get() != null) {
                handler.post { Displays.bringDesktop(this) }
                return true
            }
            // Win + flechas: maximizar, minimizar y ajustar a un lado (necesita Shizuku).
            val c = WindowControls.active
            val top = c?.top()
            if (c != null && top != null) {
                when (code) {
                    KeyEvent.KEYCODE_DPAD_UP -> { handler.post { c.toggleMaximize(top) }; return true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { handler.post { c.minimize(top) }; return true }
                    KeyEvent.KEYCODE_DPAD_LEFT -> { handler.post { c.snap(top, left = true) }; return true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { handler.post { c.snap(top, left = false) }; return true }
                }
            }
        }
        return false
    }

    private fun onWinKey() {
        val desktop = DesktopActivity.current?.get()
        if (desktop != null) {
            desktop.onGlobalWinKey()
        } else {
            Displays.external(this)?.let { Displays.openDesktop(this, it, DesktopActivity.ACTION_START) }
        }
    }

    // ---------- Touchpad: puntero y toques en el monitor ----------

    private var cursorView: View? = null
    private var cursorWm: WindowManager? = null
    private var cursorParams: WindowManager.LayoutParams? = null
    var pointerDisplay = -1
        private set
    private var cx = 0f
    private var cy = 0f
    private var maxX = 0f
    private var maxY = 0f

    /** Pantalla que controla el touchpad: la del escritorio, o el monitor conectado. */
    fun targetDisplay(): Display? {
        DesktopActivity.current?.get()?.let { d -> Displays.byId(this, d.currentDisplayId())?.let { return it } }
        return Displays.external(this) ?: Displays.byId(this, Display.DEFAULT_DISPLAY)
    }

    fun startPointer(display: Display): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        if (cursorView != null && pointerDisplay == display.displayId) return true
        stopPointer()
        return try {
            val type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            val windowCtx = createDisplayContext(display).createWindowContext(type, null)
            val wm = windowCtx.getSystemService(WindowManager::class.java)
            val size = Displays.sizeOf(display)
            maxX = (size.x - 1).toFloat()
            maxY = (size.y - 1).toFloat()
            cx = maxX / 2
            cy = maxY / 2
            val density = windowCtx.resources.displayMetrics.density
            val sizePx = (26 * density).toInt()
            val view = CursorView(windowCtx)
            val lp = WindowManager.LayoutParams(
                sizePx, sizePx, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = cx.toInt()
                y = cy.toInt()
                title = "Nexo puntero"
            }
            wm.addView(view, lp)
            cursorView = view
            cursorWm = wm
            cursorParams = lp
            pointerDisplay = display.displayId
            true
        } catch (e: Throwable) {
            stopPointer()
            false
        }
    }

    fun stopPointer() {
        cursorView?.let { v -> runCatching { cursorWm?.removeViewImmediate(v) } }
        cursorView = null
        cursorWm = null
        cursorParams = null
        pointerDisplay = -1
    }

    fun moveBy(dx: Float, dy: Float) {
        cx = (cx + dx).coerceIn(0f, maxX)
        cy = (cy + dy).coerceIn(0f, maxY)
        val v = cursorView ?: return
        val lp = cursorParams ?: return
        lp.x = cx.toInt()
        lp.y = cy.toInt()
        runCatching { cursorWm?.updateViewLayout(v, lp) }
    }

    private fun dispatch(builder: GestureDescription.Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && pointerDisplay >= 0) {
            builder.setDisplayId(pointerDisplay)
        }
        runCatching { dispatchGesture(builder.build(), null, null) }
    }

    private fun tapStroke(start: Long, duration: Long): GestureDescription.StrokeDescription {
        val p = Path().apply { moveTo(cx, cy) }
        return GestureDescription.StrokeDescription(p, start, duration)
    }

    fun click() = dispatch(GestureDescription.Builder().addStroke(tapStroke(0, 40)))

    fun doubleClick() = dispatch(
        GestureDescription.Builder()
            .addStroke(tapStroke(0, 40))
            .addStroke(tapStroke(110, 40))
    )

    /** «Clic derecho»: una pulsación larga, que en Nexo y en muchas apps abre el menú. */
    fun longPress() = dispatch(GestureDescription.Builder().addStroke(tapStroke(0, 650)))

    /** Desplaza el contenido bajo el puntero (dos dedos en el touchpad). */
    fun scroll(dx: Float, dy: Float) {
        val path = Path().apply {
            moveTo(cx, cy)
            lineTo((cx + dx).coerceIn(0f, maxX), (cy + dy).coerceIn(0f, maxY))
        }
        dispatch(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 140)))
    }

    // ---------- Escribir desde el teléfono ----------

    private fun focusedInput(displayId: Int): AccessibilityNodeInfo? {
        val list = windowsByDisplay()[displayId].orEmpty()
        for (w in list) {
            val root = try { w.root } catch (e: Exception) { null } ?: continue
            val focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focus != null && focus.isEditable) return focus
        }
        return null
    }

    private fun currentText(n: AccessibilityNodeInfo): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && n.isShowingHintText) return ""
        return n.text?.toString().orEmpty()
    }

    private fun setText(n: AccessibilityNodeInfo, text: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun typeText(displayId: Int, text: String): Boolean {
        val n = focusedInput(displayId) ?: return false
        return setText(n, currentText(n) + text)
    }

    fun backspace(displayId: Int): Boolean {
        val n = focusedInput(displayId) ?: return false
        val cur = currentText(n)
        if (cur.isEmpty()) return true
        return setText(n, cur.substring(0, cur.offsetByCodePoints(cur.length, -1)))
    }

    fun enter(displayId: Int): Boolean {
        val n = focusedInput(displayId) ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return n.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        }
        return false
    }

    /** Flecha del puntero. */
    private class CursorView(ctx: Context) : View(ctx) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); style = Paint.Style.FILL }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0B0E17.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 2f * ctx.resources.displayMetrics.density
            strokeJoin = Paint.Join.ROUND
        }
        private val path = Path()

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val pad = stroke.strokeWidth
            path.reset()
            path.moveTo(pad, pad)
            path.lineTo(pad, h * 0.78f)
            path.lineTo(w * 0.24f, h * 0.60f)
            path.lineTo(w * 0.40f, h * 0.92f)
            path.lineTo(w * 0.54f, h * 0.85f)
            path.lineTo(w * 0.39f, h * 0.54f)
            path.lineTo(w * 0.66f, h * 0.54f)
            path.close()
            canvas.drawPath(path, fill)
            canvas.drawPath(path, stroke)
        }
    }
}
