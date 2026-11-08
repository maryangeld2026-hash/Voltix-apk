package com.nexo.escritorio

import android.app.Application

class NexoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NexoPrefs.init(this)
        AppRepository.init(this)
    }
}
