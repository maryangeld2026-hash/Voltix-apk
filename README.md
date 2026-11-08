# Nexo Escritorio

Convierte tu teléfono Android en un computador: conectas un monitor, teclado y ratón, y obtienes un escritorio con ventanas, barra de tareas, menú de inicio y notificaciones. **Sin root.**

## Qué incluye

- **Escritorio** con fondo animado (6 temas o tu propia foto), iconos que se abren con doble clic y un widget de reloj y batería.
- **Barra de tareas flotante** con apps ancladas, indicador de apps abiertas, red, batería, hora y notificaciones.
- **Menú de inicio** con búsqueda instantánea (sin importar tildes), apps fijadas y todas las apps. Si no hay app, busca en la web.
- **Ventanas**: cada app se abre en una ventana que puedes mover y cambiar de tamaño. Clic derecho para abrir maximizada, en la mitad izquierda o derecha, o en una ventana nueva.
- **Centro de control** con accesos a Wi‑Fi, Bluetooth, sonido, pantalla, batería y ajustes, más tus notificaciones (ábrelas o descártalas).
- **Clic derecho en todas partes**, y pulsación larga si usas pantalla táctil.
- **Atajos de teclado**: tecla Windows (inicio), escribir para buscar, Win+A (centro de control), Win+E (archivos), Win+I (ajustes), Ctrl+Esc, Esc, Intro, Supr.
- **Ajustes**: fondo, color de acento, cómo se abren las ventanas, tamaño de iconos, reloj de 24 h.
- **Asistente en el teléfono** que comprueba cada ajuste, abre la pantalla correcta y puede activarlo todo con un botón.
- **Mosaico de ajustes rápidos** «Escritorio» para abrirlo con un toque.

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

## Requisitos y límites

- Android 10 o superior.
- El teléfono debe **sacar video por USB‑C** (DisplayPort Alt Mode). Muchos de gama media y baja no lo tienen; en ese caso el monitor se queda en negro.
- Android es quien dibuja las ventanas (barra de título, mover, redimensionar). Nexo pone el escritorio, la barra, el inicio y decide dónde se abre cada app. Su aspecto depende de la versión de Android.
- Algunas apps (juegos, algunas de bancos) se niegan a abrirse en ventana y aparecen a pantalla completa.
- La barra muestra como «abiertas» las apps que lanzaste desde Nexo en esta sesión.

## Estructura del código

```
app/src/main/java/com/nexo/escritorio/
├── DesktopActivity.kt       Escritorio en el monitor
├── MainActivity.kt          Asistente en el teléfono
├── WindowLauncher.kt        Ventanas, monitor y acciones (abrir apps, ajustes…)
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
    ├── SetupScreen.kt       Pantalla del teléfono
    ├── Wallpaper.kt         Fondos
    ├── Common.kt            Menú contextual y piezas comunes
    ├── Status.kt            Hora, batería y red
    └── Theme.kt             Colores y efecto cristal
```
