package com.nexo.escritorio

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

/** Actividad invisible: abre el escritorio en el monitor y se cierra. */
class LaunchDesktopActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val display = Displays.external(this)
        if (display == null) {
            Toast.makeText(applicationContext, "Conecta un monitor para abrir el escritorio", Toast.LENGTH_LONG).show()
        } else if (!Displays.openDesktop(this, display)) {
            Toast.makeText(applicationContext, "No se pudo abrir el escritorio en el monitor", Toast.LENGTH_LONG).show()
        }
        finish()
    }
}

/** Mosaico «Escritorio» en los ajustes rápidos del teléfono. */
class DesktopTileService : TileService() {

    override fun onStartListening() {
        val tile = qsTile ?: return
        tile.state = if (Displays.external(this) != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    @Suppress("DEPRECATION")
    override fun onClick() {
        val intent = Intent(this, LaunchDesktopActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            val pi = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pi)
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
