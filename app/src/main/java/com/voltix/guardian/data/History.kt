package com.voltix.guardian.data

import android.content.Context
import java.io.File

data class Sample(val ts: Long, val level: Int, val tempC: Float, val charging: Boolean)

class History(ctx: Context) {
    private val file = File(ctx.applicationContext.filesDir, "history.csv")

    @Synchronized
    fun append(s: BatterySnapshot) {
        try {
            file.appendText("${s.timeMs},${s.level},${s.tempC},${if (s.charging) 1 else 0}\n")
            if (file.length() > 60_000) {
                val keep = file.readLines().takeLast(400)
                file.writeText(keep.joinToString("\n") + "\n")
            }
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun last24h(): List<Sample> {
        if (!file.exists()) return emptyList()
        val from = System.currentTimeMillis() - 24L * 3600_000L
        return try {
            file.readLines().mapNotNull { line ->
                val p = line.split(",")
                if (p.size < 4) return@mapNotNull null
                val ts = p[0].toLongOrNull() ?: return@mapNotNull null
                if (ts < from) return@mapNotNull null
                Sample(
                    ts,
                    p[1].toIntOrNull() ?: return@mapNotNull null,
                    p[2].toFloatOrNull() ?: 0f,
                    p[3] == "1"
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
