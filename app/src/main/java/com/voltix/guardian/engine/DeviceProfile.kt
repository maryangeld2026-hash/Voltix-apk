package com.voltix.guardian.engine

import android.os.Build

data class DeviceProfile(
    val title: String,
    val isTarget: Boolean,
    val designMah: Int,
    val highlights: List<Pair<String, String>>
)

object DeviceProfiles {

    fun detect(): DeviceProfile {
        val model = Build.MODEL ?: ""
        val isTarget = model.contains("23090RA98") ||
            model.contains("Note 13 Pro+", ignoreCase = true) ||
            model.contains("Note 13 Pro Plus", ignoreCase = true)

        return if (isTarget) {
            DeviceProfile(
                title = "Redmi Note 13 Pro+ 5G",
                isTarget = true,
                designMah = 5000,
                highlights = listOf(
                    "🔋 5000 mAh · 120 W HyperCharge" to
                        "La carga ultrarrápida genera calor. Voltix vigila la temperatura y te avisa al llegar a tu límite de carga.",
                    "🖥️ AMOLED 1.5K · 120 Hz" to
                        "El mayor ahorro de pantalla está en bajar de 120 Hz a 90 o 60 Hz. Los modos Medio, Alto y Extremo lo hacen solos.",
                    "⚙️ Dimensity 7200 Ultra · 4 nm" to
                        "Chip eficiente: el gasto extra suele venir de 5G con señal débil, pantalla y apps en segundo plano.",
                    "🧩 HyperOS" to
                        "Controla Autoinicio y 'Ahorro de batería' por app. Voltix te lleva directo a esas pantallas."
                )
            )
        } else {
            DeviceProfile(
                title = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
                isTarget = false,
                designMah = 4500,
                highlights = listOf(
                    "ℹ️ Perfil genérico" to
                        "Voltix está afinado para el Redmi Note 13 Pro+ 5G. En otros equipos funciona, pero las claves de refresco y de HyperOS pueden variar."
                )
            )
        }
    }
}
