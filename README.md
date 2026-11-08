# Nexo Escritorio

Convierte tu teléfono Android en un computador: conectas un monitor, teclado y ratón, y obtienes un escritorio con ventanas, barra de tareas, menú de inicio y notificaciones. **Sin root.**

## Qué incluye

- **Escritorio** con fondo animado (6 temas o tu propia foto), iconos con doble clic y widget de reloj y batería.
- **Barra de tareas siempre visible**, encima de las apps (como en Windows), con apps ancladas, apps abiertas de verdad, red, batería, hora, notificaciones y botón «Mostrar escritorio».
- **Menú de inicio** con búsqueda instantánea, apps fijadas, **sección de juegos** y todas las apps.
- **Ventanas**: cada app en una ventana que se mueve y redimensiona; clic derecho para maximizada, mitad izquierda/derecha, nueva ventana o **pantalla completa**. Funciona también en la pantalla del propio teléfono.
- **Juegos**: se detectan solos, se abren a pantalla completa y la barra se esconde mientras juegas. Los mandos funcionan directamente.
- **Centro de control**: Wi‑Fi, Bluetooth, sonido, pantalla, batería, ajustes, captura de pantalla, bloqueo y touchpad, más tus notificaciones.
- **Touchpad**: el teléfono mueve un puntero en el monitor, hace clic, desplaza y escribe.
- **Tecla Windows en cualquier app** y atajos: Win+A, Win+D, Win+E, Win+I, Win+T, Ctrl+Esc, Esc.
- **Asistente en el teléfono** con todas las formas de conexión, cada ajuste comprobado y activación con un botón.

## Formas de conexión

| Forma | Para quién | Cómo |
|---|---|---|
| Cable USB‑C a HDMI | Teléfonos con salida de video (DisplayPort) | Conecta y el escritorio se abre solo |
| Inalámbrica (Miracast) | Smart TV, Fire TV, PC con Windows | «Buscar pantallas» en la app |
| Computador (scrcpy) | Casi cualquier Android 10+, también sin salida de video | `scrcpy --new-display=1920x1080/160 --start-app=com.nexo.escritorio` |
| Escritorio en este teléfono | Cualquier teléfono | El teléfono en horizontal, duplicado a la TV o con teclado y ratón |

Con scrcpy el teléfono crea una pantalla virtual nueva que se ve en el PC, con sonido; Nexo la detecta y abre el escritorio en ella. Por Wi‑Fi: `adb tcpip 5555` una vez con cable y luego `scrcpy --tcpip=IP:5555 --new-display=1920x1080/160 --start-app=com.nexo.escritorio`.

## Cómo obtener el APK

### Opción A: GitHub (sin instalar nada)
1. Crea un repositorio nuevo en GitHub y sube esta carpeta tal cual.
2. GitHub compila la app solo (pestaña **Actions** › «Compilar APK»). Tarda unos 5 minutos.
3. Al terminar, descarga **NexoEscritorio-apk** desde esa ejecución, descomprímelo e instala el `.apk` en el teléfono.

### Opción B: Android Studio
1. Abre esta carpeta con Android Studio (Ladybug o más reciente).
2. Espera a que sincronice y pulsa **Run** con el teléfono conectado.

## Preparar el teléfono (una sola vez)

La app te guía, pero en resumen:

1. Activa las **opciones de desarrollador** (Ajustes › Acerca del teléfono › toca 7 veces «Número de compilación»).
2. En Opciones de desarrollador activa:
   - **Habilitar ventanas de formato libre** (*Enable freeform windows*)
   - **Forzar modo de escritorio** (*Force desktop mode*)
   - **Forzar actividades redimensionables** (*Force activities to be resizable*), recomendado
3. **Reinicia** el teléfono.

Con un PC puedes hacerlo de una vez:

```
adb shell settings put global enable_freeform_support 1
adb shell settings put global force_resizable_activities 1
adb shell settings put global force_desktop_mode_on_external_displays 1
adb reboot
```

O conceder un permiso para que la app lo active con un botón:

```
adb shell pm grant com.nexo.escritorio android.permission.WRITE_SECURE_SETTINGS
```

## Permisos opcionales

- **Mostrar sobre otras apps**: barra de tareas siempre visible.
- **Accesibilidad (asistente de Nexo)**: apps abiertas reales en la barra, tecla Windows global, touchpad, captura y bloqueo. En Android 13+, si sale «Ajuste restringido»: Información de la app › ⋮ › «Permitir ajustes restringidos».
- **Acceso a notificaciones**: notificaciones en el centro de control.
- **Xiaomi / Redmi / POCO**: en Otros permisos, activa «Mostrar ventanas emergentes mientras se ejecuta en segundo plano».

## Requisitos y límites

- Android 10 o superior.
- Para el **cable**, el teléfono debe sacar video por USB‑C (DisplayPort Alt Mode). Si no, usa Miracast, scrcpy o el modo teléfono.
- Android es quien dibuja las ventanas (barra de título, mover, redimensionar). Nexo pone el escritorio, la barra, el inicio y decide dónde se abre cada app. Su aspecto depende de la versión de Android.
- Algunas apps (juegos, algunas de bancos) se niegan a abrirse en ventana y aparecen a pantalla completa.
- Sin el asistente, la barra muestra como «abiertas» las apps lanzadas desde Nexo en esta sesión.
- Por Wi‑Fi (Miracast) hay algo de retraso; para juegos rápidos es mejor el cable o scrcpy.

## Estructura del código

```
app/src/main/java/com/nexo/escritorio/
├── DesktopActivity.kt       Escritorio en el monitor
├── MainActivity.kt          Asistente en el teléfono
├── WindowLauncher.kt        Ventanas, pantallas, detección automática y acciones
├── BarOverlay.kt            Barra de tareas superpuesta
├── NexoAccessibility.kt     Asistente: apps abiertas, tecla Windows, puntero
├── TouchpadActivity.kt      Touchpad en el teléfono
├── Hidden.kt                Modos de ventana avanzados
├── AppRepository.kt         Lista de apps instaladas
├── NexoPrefs.kt             Preferencias
├── NotificationService.kt   Notificaciones
├── LaunchDesktopActivity.kt Mosaico de ajustes rápidos
└── ui/                      Interfaz (Jetpack Compose)
    ├── DesktopScreen.kt     Escritorio, iconos, reloj, atajos
    ├── Taskbar.kt           Barra de tareas
    ├── StartMenu.kt         Menú de inicio y búsqueda
    ├── ControlPanel.kt      Centro de control
    ├── SettingsWindow.kt    Ajustes
    ├── SetupScreen.kt       Formas de conexión y preparación
    ├── Touchpad.kt          Pantalla del touchpad
    ├── Wallpaper.kt         Fondos
    ├── Common.kt            Menú contextual y piezas comunes
    ├── Status.kt            Hora, batería y red
    └── Theme.kt             Colores y efecto cristal
```
