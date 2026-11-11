package com.nexo.escritorio

import android.app.Application

class NexoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Hidden.init()
        NexoPrefs.init(this)
        AppRepository.init(this)
        DisplayWatcher.start(this)
        TaskControl.init()
        ScreenFit.restoreIfLeftover(this)
    }
}
