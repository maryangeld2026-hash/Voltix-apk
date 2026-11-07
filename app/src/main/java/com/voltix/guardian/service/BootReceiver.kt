package com.voltix.guardian.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.voltix.guardian.data.Prefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs(context).guardianEnabled) return
        try {
            GuardianService.start(context)
        } catch (_: Exception) {
            Notifier.ensureChannels(context)
            Notifier.alert(
                context, Notifier.ID_INFO,
                "Voltix necesita reactivarse",
                "Abre la app para reanudar la protección. Activa el Autoinicio de Voltix para que sea automático."
            )
        }
    }
}
