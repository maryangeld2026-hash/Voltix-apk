# ⚡ Voltix — Guardián de batería para Redmi Note 13 Pro+ 5G

App Android (Kotlin + Jetpack Compose) que **cuida y alarga la autonomía** de la batería, con modos de ahorro
automáticos, protección de carga, vigilancia de temperatura y atajos a los ajustes de HyperOS que sí marcan
diferencia. Afinada para el Redmi Note 13 Pro+ 5G (5000 mAh, 120 W HyperCharge, AMOLED 1.5K 120 Hz,
Dimensity 7200 Ultra, HyperOS); en otros equipos funciona con perfil genérico.

---

## 1. Qué hace

| Pestaña | Contenido |
|---|---|
| **Inicio** | Medidor animado, temperatura, corriente, voltaje, salud, ciclos (Android 14+), capacidad estimada en mAh, autonomía estimada, gráficas de 24 h y guardián en segundo plano. |
| **Modos** | Modo automático inteligente + 5 modos. Cada tarjeta muestra **qué restringe** y **qué permiso necesita**, con indicador de si ya lo tienes. |
| **Carga** | Límite de carga (60–100 %), alerta de calor, reglas de cuidado y asistente de precisión del medidor. |
| **Apps** | Ranking de apps por tiempo en pantalla (24 h) y acceso directo al control de batería por app de HyperOS. |
| **Permisos** | Asistente que explica y concede cada permiso, incluido el comando ADB de una sola vez. |

### Los modos

| Modo | Restricciones que aplica |
|---|---|
| 🟢 **Normal** | Ninguna. Restaura todo a como estaba. |
| 🌱 **Moderado** | Tope de brillo 80 % · apagado de pantalla a 60 s |
| ⚖️ **Medio** | Brillo 65 % · pantalla 30 s · **90 Hz** · animaciones 50 % · vibración táctil off |
| 🔥 **Alto** | Brillo 45 % · pantalla 15 s · **60 Hz** · animaciones 50 % · vibración off · **ahorro de batería del sistema ON** |
| 🛡️ **Extremo** | Brillo 25 % · pantalla 15 s · 60 Hz · sin animaciones · vibración off · ahorro del sistema ON · **sincronización automática pausada** |

El brillo y el apagado de pantalla **solo se bajan, nunca se suben**, y todo valor original se guarda y se
restaura al volver a Normal (o al cambiar de modo, para que no se acumulen cambios).

### Modo automático

`≥70 %` Normal · `50–69 %` Moderado · `35–49 %` Medio · `20–34 %` Alto · `<20 %` Extremo.
Al conectar el cargador vuelve a Normal. Si la batería pasa de 42 °C, o es de noche (23:00–06:00), sube a Medio como
mínimo. Subir de severidad es inmediato; relajar espera 10 min para evitar parpadeos.

### Protección de carga
- Notificación al llegar a **tu** límite (por defecto 80 %). Con 120 W se alcanza en pocos minutos.
- Alerta de calor configurable (por defecto 41 °C), con consejo según estés cargando o no.
- Todo se ejecuta por **eventos** del sistema (`ACTION_BATTERY_CHANGED`), sin sondeo continuo.

---

## 2. Qué NO puede hacer (y por qué lo digo claro)

- **No “calibra” la batería para darle más capacidad.** Los móviles modernos tienen un chip medidor con
  autoaprendizaje; recalibrar solo corrige el *porcentaje que ves*, no la capacidad ni la duración. Por eso Voltix incluye
  un asistente **seguro** (una carga completa e ininterrumpida) y evita la vieja receta de descargar a 0 %, que
  perjudica a las baterías de litio. También calcula la capacidad real estimada para que veas el estado de tu batería.
- **No puede cortar la carga al 80 %.** Sin root, Android no lo permite: Voltix te avisa. Si tu HyperOS trae una opción
  nativa de protección de carga, el botón de la pestaña Carga te lleva a los ajustes de batería.
- **No puede cambiar Wi-Fi, Bluetooth, datos ni GPS** automáticamente (Android lo bloquea para apps desde la versión 10).
  Para red móvil y pantalla Voltix te lleva directo al ajuste.
- **Frecuencia de refresco:** cada fabricante usa claves distintas. Voltix prueba las estándar de Android y las de
  Xiaomi, pero **debes verificar en Ajustes > Pantalla** que cambió. Si no, usa el atajo manual.
- Los porcentajes de ahorro reales dependen de tu uso; la app **no promete cifras**, solo muestra impacto relativo.

---

## 3. Permisos

| Permiso | Cómo se concede | Qué desbloquea |
|---|---|---|
| Notificaciones | Diálogo normal | Alertas de carga y calor |
| Modificar ajustes del sistema | Ajustes (la app te lleva) | Brillo, apagado de pantalla, vibración |
| Acceso de uso | Ajustes (la app te lleva) | Ranking de apps |
| Sin optimización de batería | Diálogo del sistema | Que HyperOS no mate al guardián |
| Autoinicio (Xiaomi) | Pantalla de seguridad de MIUI/HyperOS | Arranque tras reiniciar |
| **Ajustes seguros** | **Una vez por ADB desde un PC** | Refresco 90/60 Hz, animaciones, ahorro de batería del sistema |

Comando (Opciones de desarrollador → activa *Depuración USB* y *Depuración USB (ajustes de seguridad)*):

```bash
adb shell pm grant com.voltix.guardian android.permission.WRITE_SECURE_SETTINGS
```

> Sin este último permiso la app funciona igual, pero los modos Medio/Alto/Extremo aplican solo la parte de brillo,
> pantalla y vibración. La app te lo indica con ○ ámbar en cada restricción.

---

## 4. Cómo obtener el APK (elige una)

### A) GitHub Actions — sin instalar nada
1. Crea un repositorio en GitHub y sube esta carpeta (`git init && git add . && git commit -m init && git push`).
2. Pestaña **Actions → Build APK → Run workflow** (o se lanza solo al hacer push).
3. Al terminar, descarga el artefacto **voltix-apk** y copia `app-debug.apk` al móvil.

### B) Android Studio
1. *File → Open* → selecciona esta carpeta y espera la sincronización de Gradle.
2. *Build → Build Bundle(s) / APK(s) → Build APK(s)*. Queda en `app/build/outputs/apk/debug/`.

### C) Línea de comandos (JDK 17 + Android SDK)
```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Al instalar, el móvil pedirá permitir “instalar apps de origen desconocido” para el navegador o gestor de archivos.

---

## 5. Investigación en la que se basa

- **Ficha del equipo** (mi.com y reseñas): 5000 mAh, 120 W HyperCharge, Dimensity 7200 Ultra 4 nm, AMOLED 1.5K 120 Hz.
- **Ajustes de HyperOS que más pesan** (guías de soporte y comunidad): Autoinicio por app, “Ahorro de batería” por app
  (sin restricciones / equilibrado / restringir), bajar brillo y refresco a 60 Hz, ahorro de batería, y vigilar 5G/GPS/AOD.
- **Cuidado de la vida útil** (Battery University y resúmenes de la Universidad de Michigan): mantener 20–80 %, evitar el calor,
  no dejar la batería en 100 % por horas y limitar la carga rápida cuando sube la temperatura.
- **Mito de la calibración**: los medidores con *impedance tracking* reducen o eliminan la necesidad de calibrar;
  borrar `batterystats.bin` no hace nada.
- **Apps y repositorios de referencia** estudiados para ver qué permisos usan y qué es realista sin root:
  *Essentials* (sameerasw), *Patchwork* y *SaverTuner/BatElysium* — todas dependen de `WRITE_SECURE_SETTINGS` por ADB o Shizuku
  para tocar refresco, animaciones y ahorro de batería. Voltix usa el mismo camino, pero **lo explica, lo detecta y degrada con elegancia**.

## 6. Estructura

```
app/src/main/java/com/voltix/guardian/
├── MainActivity.kt
├── data/      BatteryReader · Prefs · History
├── engine/    Modes · ModeApplier · AutoEngine(+Calibration) · Perms(+Launch) · DeviceProfile
├── service/   GuardianService · Notifier · BootReceiver
└── ui/        UiKit (tema neón, medidor, gráficas) · Screens · MainViewModel
```

Ideas futuras: integración opcional con **Shizuku** para restringir apps en segundo plano sin ADB, programación por
horarios y perfiles por app.
