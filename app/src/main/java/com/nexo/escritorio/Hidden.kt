package com.nexo.escritorio

import android.app.ActivityOptions
import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Acceso a funciones de ventanas que Android tiene pero no publica oficialmente
 * (las mismas que usan Samsung DeX y el modo escritorio de Google).
 * Si el teléfono no las tiene, todo sigue funcionando con el comportamiento normal.
 */
object Hidden {
    const val WINDOWING_MODE_FULLSCREEN = 1
    const val WINDOWING_MODE_FREEFORM = 5

    private var ready = false

    fun init() {
        if (ready) return
        ready = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { HiddenApiBypass.addHiddenApiExemptions("L") }
        }
    }

    /** Pide a Android abrir la app como ventana libre o a pantalla completa. */
    fun setWindowingMode(options: ActivityOptions, mode: Int): Boolean = try {
        ActivityOptions::class.java
            .getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
            .invoke(options, mode)
        true
    } catch (e: Throwable) {
        false
    }
}
