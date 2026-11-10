package com.nexo.escritorio

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Estado de las ventanas en la pantalla del escritorio. */
data class DesktopWindows(
    /** Ventanas visibles, de delante hacia atrás. */
    val visible: List<TaskWin> = emptyList(),
    /** Ventanas minimizadas (detrás del escritorio). */
    val minimized: List<TaskWin> = emptyList(),
) {
    val all: List<TaskWin> get() = visible + minimized
    fun forPackage(pkg: String): TaskWin? = visible.firstOrNull { it.pkg == pkg } ?: minimized.firstOrNull { it.pkg == pkg }
    fun isMinimized(t: TaskWin): Boolean = minimized.any { it.taskId == t.taskId }
}

/** Cómo está colocada una ventana (para reacomodarla si cambia la pantalla). */
private enum class Placement { FREE, MAX, LEFT, RIGHT }

/**
 * Gestor de ventanas del escritorio (necesita Shizuku):
 * - Botones de minimizar, maximizar y cerrar sobre cada ventana.
 * - Minimizar y restaurar desde la barra de tareas, como en Windows.
 * - Reacomoda todas las ventanas automáticamente si cambia el tamaño de la pantalla.
 */
class WindowControls(
    private val ctx: Context,
    private val desktopTaskId: () -> Int,
    private val menuOpen: () -> Boolean,
) {
    companion object {
        var active: WindowControls? = null
            private set
        private val _state = MutableStateFlow(DesktopWindows())
        val state: StateFlow<DesktopWindows> = _state

        /** Paquetes cuya ventana se acaba de abrir desde Nexo, con el modo pedido. */
        private val pendingLaunch = HashMap<String, Pair<WindowMode, Long>>()

        fun noteLaunch(pkg: String, mode: WindowMode) {
            pendingLaunch[pkg] = mode to SystemClock.uptimeMillis()
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private val thread = HandlerThread("nexo-ventanas").apply { start() }
    private val worker = Handler(thread.looper)
    private var display: Display? = null
    private var wm: WindowManager? = null
    private var windowCtx: Context? = null
    private var density = 1f
    private var running = false

    private val pills = HashMap<Int, PillHolder>()
    private val placement = HashMap<Int, Placement>()
    private val restoreBounds = HashMap<Int, Rect>()
    private val seen = HashSet<Int>()

    private class PillHolder(val view: PillView, val lp: WindowManager.LayoutParams, var shown: Boolean)

    fun start(d: Display, scale: Float) {
        removeAllPills()
        display = d
        active = this
        density = ctx.resources.displayMetrics.density * scale
        if (BarOverlay.canUse(ctx)) {
            runCatching {
                val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                val displayCtx = ctx.applicationContext.createDisplayContext(d)
                val windowCtx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    displayCtx.createWindowContext(type, null)
                } else {
                    displayCtx
                }
                wm = windowCtx.getSystemService(WindowManager::class.java)
                this.windowCtx = windowCtx
                density = windowCtx.resources.displayMetrics.density * scale
            }
        }
        if (!running) {
            running = true
            worker.post(poll)
        }
    }

    fun stop() {
        running = false
        worker.removeCallbacksAndMessages(null)
        main.post { removeAllPills() }
        if (active === this) active = null
        _state.value = DesktopWindows()
        thread.quitSafely()
    }

    private val poll = object : Runnable {
        override fun run() {
            if (!running) return
            val d = display
            if (TaskControl.ready.value && d != null) {
                val all = TaskControl.tasks().filter { it.displayId == d.displayId }
                main.post { onTasks(all) }
            } else {
                main.post {
                    _state.value = DesktopWindows()
                    removeAllPills()
                }
            }
            worker.postDelayed(this, if (TaskControl.ready.value) 300 else 2000)
        }
    }

    /** Pide una actualización inmediata (tras una acción). */
    private fun pollSoon() {
        worker.removeCallbacks(poll)
        worker.postDelayed(poll, 120)
    }

    private fun onTasks(all: List<TaskWin>) {
        val desk = desktopTaskId()
        val idx = all.indexOfFirst { it.taskId == desk }
        val others = all.filter { it.taskId != desk && it.pkg != ctx.packageName }
        val visible: List<TaskWin>
        val minimized: List<TaskWin>
        if (idx < 0) {
            visible = others
            minimized = emptyList()
        } else {
            val above = all.subList(0, idx).map { it.taskId }.toSet()
            visible = others.filter { it.taskId in above }
            minimized = others.filter { it.taskId !in above }
        }
        // Recordar cómo se abrió cada ventana nueva
        val now = SystemClock.uptimeMillis()
        for (t in others) {
            if (seen.add(t.taskId)) {
                val p = t.pkg?.let { pendingLaunch.remove(it) }
                if (p != null && now - p.second < 10_000) {
                    placement[t.taskId] = when (p.first) {
                        WindowMode.MAXIMIZED -> Placement.MAX
                        WindowMode.LEFT -> Placement.LEFT
                        WindowMode.RIGHT -> Placement.RIGHT
                        else -> Placement.FREE
                    }
                }
            }
        }
        val alive = others.map { it.taskId }.toSet()
        seen.retainAll(alive)
        placement.keys.retainAll(alive)
        restoreBounds.keys.retainAll(alive)
        _state.value = DesktopWindows(visible, minimized)
        updatePills(visible)
    }

    // ---------- Acciones ----------

    private fun usable(): Rect {
        val w = WindowLauncher.screenW
        val h = WindowLauncher.screenH
        return Rect(0, 0, w, (h - WindowLauncher.reservedBottom).coerceAtLeast(h / 2))
    }

    private fun fullDisplay(): Rect? = display?.let { val p = Displays.sizeOf(it); Rect(0, 0, p.x, p.y) }

    private fun bg(block: () -> Unit) {
        worker.post {
            block()
            pollSoon()
        }
    }

    fun minimize(t: TaskWin) {
        val visible = _state.value.visible.filter { it.taskId != t.taskId }
        bg {
            TaskControl.toFront(ctx, desktopTaskId())
            // Las demás ventanas vuelven delante del escritorio, en su mismo orden.
            for (v in visible.asReversed()) TaskControl.toFront(ctx, v.taskId)
        }
    }

    fun restore(t: TaskWin) = bg { TaskControl.toFront(ctx, t.taskId) }

    /** Clic en la barra de tareas: restaurar, traer al frente o minimizar (como en Windows). */
    fun activate(t: TaskWin) {
        val s = _state.value
        when {
            s.isMinimized(t) -> restore(t)
            s.visible.firstOrNull()?.taskId == t.taskId -> minimize(t)
            else -> restore(t)
        }
    }

    fun toggleMaximize(t: TaskWin) {
        if (placement[t.taskId] == Placement.MAX) {
            val back = restoreBounds.remove(t.taskId) ?: WindowLauncher.bounds(WindowMode.WINDOW) ?: return
            placement[t.taskId] = Placement.FREE
            bg { TaskControl.resize(t.taskId, back); TaskControl.toFront(ctx, t.taskId) }
        } else {
            t.bounds?.let { restoreBounds[t.taskId] = Rect(it) }
            placement[t.taskId] = Placement.MAX
            val r = usable()
            bg { TaskControl.resize(t.taskId, r); TaskControl.toFront(ctx, t.taskId) }
        }
    }

    fun snap(t: TaskWin, left: Boolean) {
        t.bounds?.let { if (placement[t.taskId] == null || placement[t.taskId] == Placement.FREE) restoreBounds[t.taskId] = Rect(it) }
        placement[t.taskId] = if (left) Placement.LEFT else Placement.RIGHT
        val u = usable()
        val r = if (left) Rect(0, 0, u.width() / 2, u.bottom) else Rect(u.width() / 2, 0, u.right, u.bottom)
        bg { TaskControl.resize(t.taskId, r); TaskControl.toFront(ctx, t.taskId) }
    }

    fun close(t: TaskWin) = bg { TaskControl.close(t.taskId) }

    /** La ventana de delante (para los atajos de teclado). */
    fun top(): TaskWin? = _state.value.visible.firstOrNull()

    /**
     * La pantalla cambió de tamaño (otra resolución, ventana de scrcpy redimensionada, TV distinta):
     * cada ventana se recoloca para seguir viéndose bien.
     */
    fun onDisplayResized(oldW: Int, oldH: Int, newW: Int, newH: Int) {
        if (!NexoPrefs.autoFit || oldW <= 0 || oldH <= 0) return
        val reserve = WindowLauncher.reservedBottom
        val usableH = (newH - reserve).coerceAtLeast(newH / 2)
        val sx = newW.toFloat() / oldW
        val sy = newH.toFloat() / oldH
        val list = _state.value.all
        bg {
            for (t in list) {
                val b = t.bounds ?: continue
                val r = when (placement[t.taskId]) {
                    Placement.MAX -> Rect(0, 0, newW, usableH)
                    Placement.LEFT -> Rect(0, 0, newW / 2, usableH)
                    Placement.RIGHT -> Rect(newW / 2, 0, newW, usableH)
                    else -> {
                        val w = (b.width() * sx).toInt().coerceIn(minOf(320, newW), newW)
                        val h = (b.height() * sy).toInt().coerceIn(minOf(240, usableH), usableH)
                        val l = (b.left * sx).toInt().coerceIn(0, (newW - w).coerceAtLeast(0))
                        val top = (b.top * sy).toInt().coerceIn(0, (usableH - h).coerceAtLeast(0))
                        Rect(l, top, l + w, top + h)
                    }
                }
                TaskControl.resize(t.taskId, r)
            }
        }
    }

    // ---------- Botones sobre las ventanas ----------

    private fun updatePills(visible: List<TaskWin>) {
        val manager = wm
        val full = fullDisplay()
        if (manager == null || full == null || !NexoPrefs.windowButtons || menuOpen()) {
            removeAllPills()
            return
        }
        val pillW = (3 * 40 * density).toInt()
        val pillH = (30 * density).toInt()
        val inset = (6 * density).toInt()
        val wanted = HashSet<Int>()
        visible.forEachIndexed { i, t ->
            val b = t.bounds ?: return@forEachIndexed
            // Sin botones en pantalla completa (juegos, videos) ni en ventanas diminutas.
            if (b.width() >= full.width() && b.height() >= full.height()) return@forEachIndexed
            if (b.width() < pillW + 2 * inset || b.height() < pillH * 2) return@forEachIndexed
            val rect = Rect(b.right - pillW - inset, b.top + inset, b.right - inset, b.top + inset + pillH)
            // Si otra ventana tapa esta esquina, el botón se esconde.
            val covered = visible.subList(0, i).any { above -> above.bounds?.let { Rect.intersects(it, rect) } == true }
            if (covered) return@forEachIndexed
            wanted += t.taskId
            showPill(manager, t, rect)
        }
        val gone = pills.keys.filter { it !in wanted }
        for (id in gone) hidePill(id)
    }

    private fun showPill(manager: WindowManager, t: TaskWin, rect: Rect) {
        val existing = pills[t.taskId]
        val maximized = placement[t.taskId] == Placement.MAX
        if (existing != null) {
            existing.view.task = t
            existing.view.maximized = maximized
            existing.view.invalidate()
            if (existing.lp.x != rect.left || existing.lp.y != rect.top || !existing.shown) {
                existing.lp.x = rect.left
                existing.lp.y = rect.top
                runCatching {
                    if (existing.shown) manager.updateViewLayout(existing.view, existing.lp)
                    else manager.addView(existing.view, existing.lp)
                    existing.shown = true
                }
            }
            return
        }
        val vctx = windowCtx ?: return
        val view = PillView(vctx, density) { action, task ->
            when (action) {
                PillView.MIN -> minimize(task)
                PillView.MAX -> toggleMaximize(task)
                PillView.CLOSE -> close(task)
            }
        }
        view.task = t
        view.maximized = maximized
        val lp = WindowManager.LayoutParams(
            rect.width(), rect.height(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = rect.left
            y = rect.top
            title = "Nexo botones de ventana"
        }
        val ok = runCatching { manager.addView(view, lp) }.isSuccess
        pills[t.taskId] = PillHolder(view, lp, ok)
    }

    private fun hidePill(id: Int) {
        val p = pills.remove(id) ?: return
        if (p.shown) runCatching { wm?.removeViewImmediate(p.view) }
    }

    private fun removeAllPills() {
        for (id in pills.keys.toList()) hidePill(id)
    }

    /** Botones ─ ▢ ✕ dibujados a mano (ligeros y nítidos en cualquier pantalla). */
    @SuppressLint("ViewConstructor")
    private class PillView(
        context: Context,
        private val d: Float,
        private val onAction: (Int, TaskWin) -> Unit,
    ) : View(context) {
        companion object {
            const val MIN = 0
            const val MAX = 1
            const val CLOSE = 2
        }

        var task: TaskWin? = null
        var maximized = false
        private var hover = -1

        private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6161A26.toInt() }
        private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x33FFFFFF
            style = Paint.Style.STROKE
            strokeWidth = d
        }
        private val hoverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26FFFFFF }
        private val closePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE5484D.toInt() }
        private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF3F5FA.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.6f * d
            strokeCap = Paint.Cap.ROUND
        }
        private val r = RectF()

        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val radius = h / 2
            r.set(0f, 0f, w, h)
            c.drawRoundRect(r, radius, radius, bg)
            val cell = w / 3
            if (hover >= 0) {
                val left = hover * cell
                r.set(left + 2 * d, 2 * d, left + cell - 2 * d, h - 2 * d)
                c.drawRoundRect(r, radius, radius, if (hover == CLOSE) closePaint else hoverPaint)
            }
            r.set(0f, 0f, w, h)
            c.drawRoundRect(r, radius, radius, border)
            val s = 5.5f * d
            // Minimizar
            var cx = cell * 0.5f
            val cy = h / 2
            c.drawLine(cx - s, cy + s * 0.4f, cx + s, cy + s * 0.4f, icon)
            // Maximizar / restaurar
            cx = cell * 1.5f
            if (maximized) {
                c.drawRect(cx - s + 2 * d, cy - s, cx + s, cy + s - 2 * d, icon)
                c.drawRect(cx - s, cy - s + 2 * d, cx + s - 2 * d, cy + s, icon)
            } else {
                c.drawRect(cx - s, cy - s, cx + s, cy + s, icon)
            }
            // Cerrar
            cx = cell * 2.5f
            c.drawLine(cx - s, cy - s, cx + s, cy + s, icon)
            c.drawLine(cx + s, cy - s, cx - s, cy + s, icon)
        }

        private fun cellAt(x: Float): Int = (x / (width / 3f)).toInt().coerceIn(0, 2)

        override fun onHoverEvent(e: MotionEvent): Boolean {
            hover = if (e.actionMasked == MotionEvent.ACTION_HOVER_EXIT) -1 else cellAt(e.x)
            invalidate()
            return true
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { hover = cellAt(e.x); invalidate() }
                MotionEvent.ACTION_UP -> {
                    val t = task
                    val cell = cellAt(e.x)
                    hover = -1
                    invalidate()
                    if (t != null && e.x in 0f..width.toFloat() && e.y in 0f..height.toFloat()) onAction(cell, t)
                }
                MotionEvent.ACTION_CANCEL -> { hover = -1; invalidate() }
            }
            return true
        }
    }
}
