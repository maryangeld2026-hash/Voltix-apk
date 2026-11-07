package com.voltix.guardian.engine

/** Permiso necesario para que una restricción se aplique de verdad. */
enum class Need { NONE, WRITE_SETTINGS, SECURE }

data class Restriction(val title: String, val detail: String, val need: Need)

data class ModeDef(
    val id: String,
    val name: String,
    val emoji: String,
    val tagline: String,
    val impact: String,
    val accent: Long,
    val brightnessCapPct: Int? = null,
    val timeoutSec: Int? = null,
    val refreshHz: Int? = null,
    val animScale: Float? = null,
    val hapticsOff: Boolean = false,
    val lowPower: Boolean = false,
    val syncOff: Boolean = false
) {
    /** La lista visible se deriva de los mismos campos que usa el aplicador: nunca se desincroniza. */
    val restrictions: List<Restriction>
        get() {
            val list = mutableListOf<Restriction>()
            brightnessCapPct?.let {
                list += Restriction(
                    "Tope de brillo al $it%",
                    "Si el brillo está por encima, se baja. Nunca lo sube y puedes subirlo a mano.",
                    Need.WRITE_SETTINGS
                )
            }
            timeoutSec?.let {
                list += Restriction(
                    "Pantalla se apaga a los $it s",
                    "Solo se acorta; si ya lo tenías más corto, se respeta.",
                    Need.WRITE_SETTINGS
                )
            }
            refreshHz?.let {
                list += Restriction(
                    "Refresco de pantalla a $it Hz",
                    "Tu AMOLED llega a 120 Hz: menos Hz, menos consumo al mover la pantalla.",
                    Need.SECURE
                )
            }
            animScale?.let {
                list += Restriction(
                    if (it == 0f) "Animaciones desactivadas" else "Animaciones al ${(it * 100).toInt()}%",
                    "Menos trabajo de GPU en cada transición.",
                    Need.SECURE
                )
            }
            if (hapticsOff) {
                list += Restriction(
                    "Vibración táctil desactivada",
                    "El motor de vibración deja de activarse al tocar.",
                    Need.WRITE_SETTINGS
                )
            }
            if (lowPower) {
                list += Restriction(
                    "Ahorro de batería del sistema activado",
                    "Limita procesos en segundo plano y rendimiento sostenido.",
                    Need.SECURE
                )
            }
            if (syncOff) {
                list += Restriction(
                    "Sincronización automática pausada",
                    "Contactos, calendario y cuentas no se sincronizan solos. Las notificaciones push siguen llegando.",
                    Need.NONE
                )
            }
            if (list.isEmpty()) {
                list += Restriction(
                    "Sin restricciones",
                    "Todo vuelve a como lo tenías. Voltix solo monitoriza y protege la carga.",
                    Need.NONE
                )
            }
            return list
        }
}

object Modes {
    val all: List<ModeDef> = listOf(
        ModeDef(
            id = "normal", name = "Normal", emoji = "🟢",
            tagline = "Sin restricciones. Solo monitoreo y protección de carga.",
            impact = "Sin ahorro", accent = 0xFF00E5FF
        ),
        ModeDef(
            id = "moderate", name = "Moderado", emoji = "🌱",
            tagline = "Ahorro suave que casi no notas.",
            impact = "Ahorro bajo", accent = 0xFFB6FF3B,
            brightnessCapPct = 80, timeoutSec = 60
        ),
        ModeDef(
            id = "medium", name = "Medio", emoji = "⚖️",
            tagline = "Equilibrio: 90 Hz, animaciones reducidas y brillo contenido.",
            impact = "Ahorro medio", accent = 0xFFFFD60A,
            brightnessCapPct = 65, timeoutSec = 30, refreshHz = 90, animScale = 0.5f, hapticsOff = true
        ),
        ModeDef(
            id = "high", name = "Alto", emoji = "🔥",
            tagline = "60 Hz fijos, brillo bajo y ahorro del sistema activado.",
            impact = "Ahorro alto", accent = 0xFFFF9F1C,
            brightnessCapPct = 45, timeoutSec = 15, refreshHz = 60, animScale = 0.5f,
            hapticsOff = true, lowPower = true
        ),
        ModeDef(
            id = "extreme", name = "Extremo", emoji = "🛡️",
            tagline = "Supervivencia: todo al mínimo para llegar al cargador.",
            impact = "Ahorro máximo", accent = 0xFFFF3B5C,
            brightnessCapPct = 25, timeoutSec = 15, refreshHz = 60, animScale = 0f,
            hapticsOff = true, lowPower = true, syncOff = true
        )
    )

    fun byId(id: String): ModeDef = all.firstOrNull { it.id == id } ?: all.first()

    fun indexOf(id: String): Int = all.indexOfFirst { it.id == id }.coerceAtLeast(0)
}
