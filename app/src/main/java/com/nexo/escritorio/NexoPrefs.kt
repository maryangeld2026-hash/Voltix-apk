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
    /** Cómo se abren las apps: 0 = en ventana, 1 = maximizadas (con barra), 2 = pantalla completa. */
    var openMode by mutableIntStateOf(1); private set
    /** Formato al duplicar el teléfono en una TV: 0 = original, 1 = 16:9, 2 = 16:10, 3 = 21:9. */
    var mirrorFit by mutableIntStateOf(1); private set
    /** 0 = pequeños, 1 = normales, 2 = grandes */
    var iconSize by mutableIntStateOf(1); private set
    var clock24 by mutableStateOf(true); private set
    var showDesktopIcons by mutableStateOf(true); private set
    var autoOpen by mutableStateOf(true); private set
    var customWallpaper by mutableStateOf<ImageBitmap?>(null); private set
    var gamesFullscreen by mutableStateOf(true); private set
    var phoneLandscape by mutableStateOf(true); private set
    var floatingBar by mutableStateOf(true); private set
    var globalWinKey by mutableStateOf(true); private set
    /** Velocidad del puntero del touchpad (0.5 – 3.0). */
    var pointerSpeed by mutableStateOf(1.6f); private set
    /** Tamaño de la interfaz: 0 = más pequeño, 1 = automático, 2 = más grande. */
    var uiSize by mutableIntStateOf(1); private set
    /** Botones de minimizar, maximizar y cerrar sobre cada ventana (con Shizuku). */
    var windowButtons by mutableStateOf(true); private set
    /** Recolocar las ventanas cuando cambia el tamaño de la pantalla. */
    var autoFit by mutableStateOf(true); private set

    val taskbarPins = mutableStateListOf<String>()
    val desktopItems = mutableStateListOf<String>()

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.getSharedPreferences("nexo", Context.MODE_PRIVATE)
        files = ctx.filesDir
        wallpaper = sp.getInt(K_WALL, 0)
        animatedWallpaper = sp.getBoolean(K_ANIM, true)
        accent = sp.getInt(K_ACCENT, 0)
        openMode = sp.getInt(K_OPEN_MODE, 1)
        mirrorFit = sp.getInt(K_MIRROR_FIT, 1)
        iconSize = sp.getInt(K_ICON, 1)
        clock24 = sp.getBoolean(K_24H, true)
        showDesktopIcons = sp.getBoolean(K_SHOW_ICONS, true)
        autoOpen = sp.getBoolean(K_AUTO, true)
        gamesFullscreen = sp.getBoolean(K_GAMES, true)
        phoneLandscape = sp.getBoolean(K_LAND, true)
        floatingBar = sp.getBoolean(K_FLOAT, true)
        globalWinKey = sp.getBoolean(K_WINKEY, true)
        pointerSpeed = sp.getFloat(K_SPEED, 1.6f)
        uiSize = sp.getInt(K_UISIZE, 1)
        windowButtons = sp.getBoolean(K_WINBTN, true)
        autoFit = sp.getBoolean(K_AUTOFIT, true)
        loadList(K_PINS)?.let { taskbarPins.addAll(it) }
        loadList(K_DESK)?.let { desktopItems.addAll(it) }
    }

    fun changeWallpaper(i: Int) { wallpaper = i; sp.edit().putInt(K_WALL, i).apply() }
    fun changeAnimated(v: Boolean) { animatedWallpaper = v; sp.edit().putBoolean(K_ANIM, v).apply() }
    fun changeAccent(i: Int) { accent = i; sp.edit().putInt(K_ACCENT, i).apply() }
    fun changeOpenMode(v: Int) { openMode = v; sp.edit().putInt(K_OPEN_MODE, v).apply() }
    fun changeMirrorFit(v: Int) { mirrorFit = v; sp.edit().putInt(K_MIRROR_FIT, v).apply() }

    /** true mientras Nexo tiene cambiada la resolución del teléfono (para restaurarla siempre). */
    var fitActive: Boolean
        get() = sp.getBoolean(K_FIT_ACTIVE, false)
        set(v) { sp.edit().putBoolean(K_FIT_ACTIVE, v).commit() }

    const val OPEN_WINDOW = 0
    const val OPEN_MAXIMIZED = 1
    const val OPEN_FULLSCREEN = 2
    fun changeIconSize(v: Int) { iconSize = v; sp.edit().putInt(K_ICON, v).apply() }
    fun changeClock24(v: Boolean) { clock24 = v; sp.edit().putBoolean(K_24H, v).apply() }
    fun changeShowDesktopIcons(v: Boolean) { showDesktopIcons = v; sp.edit().putBoolean(K_SHOW_ICONS, v).apply() }
    fun changeAutoOpen(v: Boolean) { autoOpen = v; sp.edit().putBoolean(K_AUTO, v).apply() }

    fun changeGamesFullscreen(v: Boolean) { gamesFullscreen = v; sp.edit().putBoolean(K_GAMES, v).apply() }
    fun changePhoneLandscape(v: Boolean) { phoneLandscape = v; sp.edit().putBoolean(K_LAND, v).apply() }
    fun changeFloatingBar(v: Boolean) { floatingBar = v; sp.edit().putBoolean(K_FLOAT, v).apply() }
    fun changeGlobalWinKey(v: Boolean) { globalWinKey = v; sp.edit().putBoolean(K_WINKEY, v).apply() }
    fun changePointerSpeed(v: Float) { pointerSpeed = v; sp.edit().putFloat(K_SPEED, v).apply() }

    fun changeUiSize(v: Int) { uiSize = v; sp.edit().putInt(K_UISIZE, v).apply() }
    fun changeWindowButtons(v: Boolean) { windowButtons = v; sp.edit().putBoolean(K_WINBTN, v).apply() }
    fun changeAutoFit(v: Boolean) { autoFit = v; sp.edit().putBoolean(K_AUTOFIT, v).apply() }

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

    private var loadedFor = 0

    /**
     * Carga el fondo personalizado a la resolución justa de la pantalla:
     * nítido en una TV 4K y sin gastar memoria de más en un teléfono.
     */
    suspend fun loadCustomWallpaper(targetW: Int = 1920, targetH: Int = 1080) {
        val target = maxOf(targetW, targetH).coerceAtLeast(720)
        if (customWallpaper != null && loadedFor >= target) return
        val bmp = withContext(Dispatchers.IO) {
            val f = File(files, WALL_FILE)
            if (!f.exists()) return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
            BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        if (bmp != null) {
            customWallpaper = bmp.asImageBitmap()
            loadedFor = target
        }
    }

    /** Copia la foto elegida a la app (reducida para no gastar memoria) y la pone de fondo. */
    suspend fun setCustomWallpaper(ctx: Context, uri: Uri): Boolean {
        val bmp = withContext(Dispatchers.IO) {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 4096) sample *= 2
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
        loadedFor = maxOf(bmp.width, bmp.height)
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
    private const val K_GAMES = "games_fullscreen"
    private const val K_LAND = "phone_landscape"
    private const val K_FLOAT = "floating_bar"
    private const val K_WINKEY = "global_win_key"
    private const val K_SPEED = "pointer_speed"
    private const val K_OPEN_MODE = "open_mode"
    private const val K_MIRROR_FIT = "mirror_fit"
    private const val K_FIT_ACTIVE = "fit_active"
    private const val K_UISIZE = "ui_size"
    private const val K_WINBTN = "window_buttons"
    private const val K_AUTOFIT = "auto_fit"
    private const val K_PINS = "pins"
    private const val K_DESK = "desktop"
    private const val K_INIT = "defaults_done"
}
