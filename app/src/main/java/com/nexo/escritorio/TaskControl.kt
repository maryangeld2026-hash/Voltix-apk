package com.nexo.escritorio

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/** Una ventana (tarea) abierta en alguna pantalla. */
data class TaskWin(
    val taskId: Int,
    val displayId: Int,
    val pkg: String?,
    val bounds: Rect?,
    val windowingMode: Int,
    val visible: Boolean,
)

/**
 * Control real de las ventanas de otras apps a través de Shizuku (funciona sin root:
 * Shizuku se inicia con la «Depuración inalámbrica» del propio teléfono).
 * Permite: ver las ventanas abiertas, cerrarlas, redimensionarlas, minimizarlas y restaurarlas.
 * Si Shizuku no está, Nexo sigue funcionando con los botones propios de Android.
 */
object TaskControl {
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val REQUEST_CODE = 4242
    private const val ACTIVITY_TYPE_HOME = 2
    private const val ACTIVITY_TYPE_RECENTS = 3

    /** Shizuku está en marcha y Nexo tiene permiso. */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    /** Shizuku está en marcha (aunque falte el permiso). */
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private var atm: Any? = null
    private var started = false

    fun init() {
        if (started) return
        started = true
        runCatching {
            Shizuku.addBinderReceivedListenerSticky { refresh() }
            Shizuku.addBinderDeadListener {
                atm = null
                _running.value = false
                _ready.value = false
            }
            Shizuku.addRequestPermissionResultListener { _, _ -> refresh() }
        }
    }

    fun refresh() {
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        _running.value = alive
        _ready.value = alive && runCatching {
            !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (!_ready.value) atm = null
    }

    fun isInstalled(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (e: Exception) {
        false
    }

    /** Pide el permiso de Shizuku (aparece un diálogo de Shizuku). */
    fun requestPermission(): Boolean {
        refresh()
        if (!_running.value) return false
        return runCatching {
            if (Shizuku.isPreV11()) return false
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                refresh()
            } else {
                Shizuku.requestPermission(REQUEST_CODE)
            }
            true
        }.getOrDefault(false)
    }

    // ---------- Acceso al gestor de ventanas de Android ----------

    private fun service(): Any? {
        if (!_ready.value) return null
        atm?.let { return it }
        return runCatching {
            val binder: IBinder = SystemServiceHelper.getSystemService("activity_task") ?: return null
            val stub = Class.forName("android.app.IActivityTaskManager\$Stub")
            stub.getMethod("asInterface", IBinder::class.java).invoke(null, ShizukuBinderWrapper(binder))
        }.getOrNull().also { atm = it }
    }

    private fun field(obj: Any, name: String): Any? = runCatching {
        var c: Class<*>? = obj.javaClass
        while (c != null) {
            try {
                return@runCatching c.getDeclaredField(name).apply { isAccessible = true }.get(obj)
            } catch (e: NoSuchFieldException) {
                c = c.superclass
            }
        }
        null
    }.getOrNull()

    /** Todas las ventanas abiertas, de la más reciente (delante) a la más antigua. */
    fun tasks(): List<TaskWin> {
        val s = service() ?: return emptyList()
        val raw: List<*> = runCatching {
            val m = s.javaClass.methods
                .filter { it.name == "getTasks" }
                .maxByOrNull { it.parameterTypes.size } ?: return emptyList()
            var firstInt = true
            val args = m.parameterTypes.map { t ->
                when (t) {
                    Int::class.javaPrimitiveType -> if (firstInt) { firstInt = false; 60 } else -1
                    Boolean::class.javaPrimitiveType -> false
                    else -> null
                }
            }.toTypedArray()
            m.invoke(s, *args) as? List<*>
        }.getOrNull() ?: return emptyList()

        return raw.mapNotNull { info ->
            if (info !is ActivityManager.RunningTaskInfo) return@mapNotNull null
            val config = field(info, "configuration")
            val wc = config?.let { field(it, "windowConfiguration") }
            val type = wc?.let { runCatching { it.javaClass.getMethod("getActivityType").invoke(it) as Int }.getOrNull() } ?: 0
            if (type == ACTIVITY_TYPE_HOME || type == ACTIVITY_TYPE_RECENTS) return@mapNotNull null
            val bounds = wc?.let { runCatching { it.javaClass.getMethod("getBounds").invoke(it) as Rect }.getOrNull() }
            val mode = wc?.let { runCatching { it.javaClass.getMethod("getWindowingMode").invoke(it) as Int }.getOrNull() } ?: 0
            val top: ComponentName? = info.topActivity ?: info.baseActivity
            TaskWin(
                taskId = info.taskId,
                displayId = (field(info, "displayId") as? Int) ?: 0,
                pkg = top?.packageName,
                bounds = bounds?.let { Rect(it) },
                windowingMode = mode,
                visible = (field(info, "isVisible") as? Boolean) ?: true,
            )
        }
    }

    /** Cierra la ventana (y la tarea) de una app. */
    fun close(taskId: Int): Boolean {
        val s = service() ?: return false
        return runCatching {
            s.javaClass.getMethod("removeTask", Int::class.javaPrimitiveType).invoke(s, taskId) as? Boolean ?: true
        }.getOrDefault(false)
    }

    /** Cambia la posición y el tamaño de una ventana. */
    fun resize(taskId: Int, bounds: Rect): Boolean {
        val s = service() ?: return false
        return runCatching {
            val m = s.javaClass.methods.firstOrNull { it.name == "resizeTask" } ?: return false
            val args = m.parameterTypes.map { t ->
                when (t) {
                    Int::class.javaPrimitiveType -> null
                    Rect::class.java -> bounds
                    else -> null
                }
            }.toMutableList()
            // Primer entero: id de la tarea; segundo (si existe): modo de cambio de tamaño (0 = sistema).
            var seenInt = false
            m.parameterTypes.forEachIndexed { i, t ->
                if (t == Int::class.javaPrimitiveType) {
                    args[i] = if (!seenInt) taskId else 0
                    seenInt = true
                }
            }
            m.invoke(s, *args.toTypedArray())
            true
        }.getOrDefault(false)
    }

    /** Trae una ventana al frente. */
    fun toFront(ctx: Context, taskId: Int): Boolean = runCatching {
        ctx.getSystemService(ActivityManager::class.java).moveTaskToFront(taskId, 0)
        true
    }.getOrDefault(false)
}
