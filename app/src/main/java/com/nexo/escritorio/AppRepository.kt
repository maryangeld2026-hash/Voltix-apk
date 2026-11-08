package com.nexo.escritorio

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

/** Una app que se puede abrir desde el escritorio. */
data class AppEntry(
    val key: String,
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val user: UserHandle,
    val icon: ImageBitmap,
    val isGame: Boolean = false,
)

/** Lista de apps instaladas, siempre al día (se recarga al instalar o desinstalar). */
object AppRepository {
    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loadJob: Job? = null

    fun init(ctx: Context) {
        if (::appContext.isInitialized) return
        appContext = ctx.applicationContext
        val launcherApps = appContext.getSystemService(LauncherApps::class.java)
        launcherApps.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) = reload()
            override fun onPackageAdded(packageName: String?, user: UserHandle?) = reload()
            override fun onPackageChanged(packageName: String?, user: UserHandle?) = reload()
            override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = reload()
            override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = reload()
        }, Handler(Looper.getMainLooper()))
        reload()
    }

    fun reload() {
        loadJob?.cancel()
        loadJob = scope.launch {
            val launcherApps = appContext.getSystemService(LauncherApps::class.java)
            val density = appContext.resources.displayMetrics.density
            val iconPx = (72 * density).toInt().coerceIn(96, 192)
            val me = Process.myUserHandle()
            val result = ArrayList<AppEntry>()
            for (user in launcherApps.profiles) {
                val list = try { launcherApps.getActivityList(null, user) } catch (e: Exception) { emptyList() }
                for (info in list) {
                    val component = info.componentName
                    if (component.packageName == appContext.packageName) continue
                    val icon = try {
                        info.getBadgedIcon(0).toBitmap(iconPx, iconPx).asImageBitmap()
                    } catch (e: Exception) {
                        continue
                    }
                    val ai = info.applicationInfo
                    @Suppress("DEPRECATION")
                    val game = ai.category == ApplicationInfo.CATEGORY_GAME ||
                        (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0
                    val key = component.flattenToString() + if (user == me) "" else "#${user.hashCode()}"
                    result += AppEntry(
                        key = key,
                        label = info.label?.toString()?.trim().orEmpty().ifEmpty { component.packageName },
                        packageName = component.packageName,
                        component = component,
                        user = user,
                        icon = icon,
                        isGame = game,
                    )
                }
            }
            val collator = Collator.getInstance(Locale.getDefault())
            val sorted = result.sortedWith { a, b -> collator.compare(a.label, b.label) }
            _apps.value = sorted
            withContext(Dispatchers.Main) { NexoPrefs.ensureDefaults(sorted) }
        }
    }

    fun findByPackages(packages: List<String>): AppEntry? {
        val list = _apps.value
        for (p in packages) list.firstOrNull { it.packageName == p }?.let { return it }
        return null
    }
}
