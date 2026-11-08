package com.nexo.escritorio

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Collator
import java.util.Locale

/**
 * Preferencias del escritorio. Cada valor es "estado de Compose": cuando cambia,
 * la interfaz se actualiza sola, y además se guarda en el teléfono.
 */
object NexoPrefs {
    private lateinit var sp: SharedPreferences
    private lateinit var files: File

    /** Índice del fondo integrado, o -1 para la foto del usuario. */
    var wallpaper by mutableIntStateOf(0); private set
    var animatedWallpaper by mutableStateOf(true); private set
    var accent by mutableIntStateOf(0); private set
    var openMaximized by mutableStateOf(false); private set
    /** 0 = pequeños, 1 = normales, 2 = grandes */
    var iconSize by mutableIntStateOf(1); private set
    var clock24 by mutableStateOf(true); private set
    var showDesktopIcons by mutableStateOf(true); private set
    var autoOpen by mutableStateOf(true); private set
    var customWallpaper by mutableStateOf<ImageBitmap?>(null); private set

    val taskbarPins = mutableStateListOf<String>()
    val desktopItems = mutableStateListOf<String>()

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.getSharedPreferences("nexo", Context.MODE_PRIVATE)
        files = ctx.filesDir
        wallpaper = sp.getInt(K_WALL, 0)
        animatedWallpaper = sp.getBoolean(K_ANIM, true)
        accent = sp.getInt(K_ACCENT, 0)
        openMaximized = sp.getBoolean(K_MAX, false)
        iconSize = sp.getInt(K_ICON, 1)
        clock24 = sp.getBoolean(K_24H, true)
        showDesktopIcons = sp.getBoolean(K_SHOW_ICONS, true)
        autoOpen = sp.getBoolean(K_AUTO, true)
        loadList(K_PINS)?.let { taskbarPins.addAll(it) }
        loadList(K_DESK)?.let { desktopItems.addAll(it) }
    }

    fun changeWallpaper(i: Int) { wallpaper = i; sp.edit().putInt(K_WALL, i).apply() }
    fun changeAnimated(v: Boolean) { animatedWallpaper = v; sp.edit().putBoolean(K_ANIM, v).apply() }
    fun changeAccent(i: Int) { accent = i; sp.edit().putInt(K_ACCENT, i).apply() }
    fun changeOpenMaximized(v: Boolean) { openMaximized = v; sp.edit().putBoolean(K_MAX, v).apply() }
    fun changeIconSize(v: Int) { iconSize = v; sp.edit().putInt(K_ICON, v).apply() }
    fun changeClock24(v: Boolean) { clock24 = v; sp.edit().putBoolean(K_24H, v).apply() }
    fun changeShowDesktopIcons(v: Boolean) { showDesktopIcons = v; sp.edit().putBoolean(K_SHOW_ICONS, v).apply() }
    fun changeAutoOpen(v: Boolean) { autoOpen = v; sp.edit().putBoolean(K_AUTO, v).apply() }

    fun togglePin(key: String) {
        if (key in taskbarPins) taskbarPins.remove(key) else taskbarPins.add(key)
        saveList(K_PINS, taskbarPins)
    }

    fun toggleDesktop(key: String) {
        if (key in desktopItems) desktopItems.remove(key) else desktopItems.add(key)
        saveList(K_DESK, desktopItems)
    }

    fun sortDesktop(labelOf: (String) -> String) {
        val collator = Collator.getInstance(Locale.getDefault())
        val sorted = desktopItems.sortedWith { a, b -> collator.compare(labelOf(a), labelOf(b)) }
        desktopItems.clear()
        desktopItems.addAll(sorted)
        saveList(K_DESK, desktopItems)
    }

    /** La primera vez, ancla apps conocidas a la barra y al escritorio. */
    fun ensureDefaults(apps: List<AppEntry>) {
        if (sp.getBoolean(K_INIT, false) || apps.isEmpty()) return
        val byPkg = apps.associateBy { it.packageName }
        val pinPkgs = listOf(
            "com.android.chrome", "org.mozilla.firefox", "com.sec.android.app.sbrowser",
            "com.google.android.apps.nbu.files", "com.sec.android.app.myfiles",
            "com.google.android.gm", "com.google.android.youtube", "com.whatsapp",
            "com.google.android.apps.docs.editors.docs", "com.microsoft.office.officehubrow",
            "com.spotify.music", "com.google.android.apps.photos"
        )
        val deskPkgs = listOf(
            "com.google.android.apps.nbu.files", "com.sec.android.app.myfiles",
            "com.google.android.apps.docs", "com.google.android.apps.photos",
            "com.google.android.calendar", "com.google.android.keep",
            "com.android.vending", "com.android.settings"
        )
        val pins = pinPkgs.mapNotNull { byPkg[it]?.key }.take(6).ifEmpty { apps.take(5).map { it.key } }
        val desk = deskPkgs.mapNotNull { byPkg[it]?.key }.take(6).ifEmpty { apps.take(4).map { it.key } }
        taskbarPins.clear(); taskbarPins.addAll(pins)
        desktopItems.clear(); desktopItems.addAll(desk)
        saveList(K_PINS, taskbarPins)
        saveList(K_DESK, desktopItems)
        sp.edit().putBoolean(K_INIT, true).apply()
    }

    suspend fun loadCustomWallpaper() {
        if (customWallpaper != null) return
        val bmp = withContext(Dispatchers.IO) {
            val f = File(files, WALL_FILE)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
        }
        if (bmp != null) customWallpaper = bmp.asImageBitmap()
    }

    /** Copia la foto elegida a la app (reducida para no gastar memoria) y la pone de fondo. */
    suspend fun setCustomWallpaper(ctx: Context, uri: Uri): Boolean {
        val bmp = withContext(Dispatchers.IO) {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 3000) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val decoded = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                if (decoded != null) {
                    File(files, WALL_FILE).outputStream().use { decoded.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                }
                decoded
            } catch (e: Exception) {
                null
            }
        } ?: return false
        customWallpaper = bmp.asImageBitmap()
        changeWallpaper(-1)
        return true
    }

    private fun saveList(key: String, list: List<String>) {
        sp.edit().putString(key, list.joinToString("\n")).apply()
    }

    private fun loadList(key: String): List<String>? =
        sp.getString(key, null)?.split("\n")?.filter { it.isNotBlank() }

    private const val WALL_FILE = "wallpaper.jpg"
    private const val K_WALL = "wallpaper"
    private const val K_ANIM = "animated"
    private const val K_ACCENT = "accent"
    private const val K_MAX = "open_max"
    private const val K_ICON = "icon_size"
    private const val K_24H = "clock24"
    private const val K_SHOW_ICONS = "show_icons"
    private const val K_AUTO = "auto_open"
    private const val K_PINS = "pins"
    private const val K_DESK = "desktop"
    private const val K_INIT = "defaults_done"
}
