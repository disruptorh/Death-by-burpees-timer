# Death by Burpees Timer ⏱️

Temporizador de intervalos para Android con aviso sonoro para que no tengas que
mirar el móvil. Dos modos: **Modo Rutina** (trabajo/descanso por series) y
**Death by Burpees** (un minuto por round, +1 burpee cada minuto). Kotlin, MVVM,
`minSdk 24` / `targetSdk 35`, interfaz en español.

<p align="center">
  <a href="https://github.com/disruptorh/Death-by-burpees-timer/releases/latest/download/Death.by.burpees.apk">
    <img alt="Descargar" src="https://img.shields.io/badge/%E2%AC%87%20Download-latest%20release-2f6feb?style=for-the-badge&logo=github&logoColor=white">
  </a>
  <a href="https://github.com/disruptorh/Death-by-burpees-timer/releases/latest">
    <img alt="Versiones" src="https://img.shields.io/github/v/release/disruptorh/Death-by-burpees-timer?label=release&style=flat&logo=github&logoColor=white">
  </a>
  <a href="./LICENSE">
    <img alt="Licencia" src="https://img.shields.io/badge/licencia-Apache--2.0-blue?style=flat">
  </a>
</p>

<p align="center">
  <a href="https://github.com/disruptorh/Death-by-burpees-timer/actions/workflows/ci.yml/badge.svg">
    <img alt="CI" src="https://img.shields.io/github/actions/workflow/status/disruptorh/Death-by-burpees-timer/ci.yml?label=CI&style=flat&logo=github&logoColor=white">
  </a>
</p>

## 📥 Descarga rápida

El botón de arriba descarga el APK **ya firmado** de la última release
(`Death.by.burpees.apk`). Necesitas Android 7.0 (API 24) o superior.

Para instalarlo a mano: abre el APK descargado y, si Android lo pide, activa
**Ajustes → Apps → Acceso especial → Instalar apps desconocidas** para la app
que lo abre (navegador o gestor de archivos). La primera vez la app te pedirá
permiso para las notificaciones: sin él no hay aviso en la pantalla de bloqueo.

Desde un ordenador con el móvil por cable o ADB Wi-Fi:

```bash
# 1. Descargar la última release publicada
curl -L -o Death.by.burpees.apk https://github.com/disruptorh/Death-by-burpees-timer/releases/latest/download/Death.by.burpees.apk

# 2. Instalar (o reinstalar) en el dispositivo conectado
adb install -r Death.by.burpees.apk
```

## 📱 Capturas

<p align="center">
  <img src="https://github.com/disruptorh/Death-by-burpees-timer/blob/main/images/mode%20selection.png" alt="Selección de modo" width="250"/>
  <img src="https://github.com/disruptorh/Death-by-burpees-timer/blob/main/images/routine%20mode.png" alt="Modo rutina" width="250"/>
  <img src="https://github.com/disruptorh/Death-by-burpees-timer/blob/main/images/death%20by%20burpees.png" alt="Death by Burpees" width="250"/>
</p>

## 🚀 Uso rápido

1. Abre la app y elige un preset (**Tabata**, **EMOM 10**, **AMRAP 20**) para
   saltar directo al temporizador, o pulsa **Modo Rutina** / **Muerte por
   Burpees** para configurar la sesión a mano.
2. **Modo Rutina:** trabajo de 5 s a 60 min, descanso de 0 a 60 min y de 1 a 99
   series. El conmutador seg/min solo cambia cómo se muestra el número: nunca
   reescala el valor por detrás.
3. Pulsa **Play**: una cuenta atrás de preparación de 5 segundos con ticks
   ascendentes y vibración, y luego empieza la sesión.
4. **Muerte por Burpees:** fija la duración total (1 a 999 minutos), pulsa Play y
   haz **1 burpee**. En cada marca de minuto suena un pitido largo y el contador
   sube a **+1 burpee**; el aviso de 10 segundos precede a cada minuto.
5. Pausa, reanuda o para desde la app o desde la notificación.

Casos de uso típicos: Death by Burpees (CrossFit), EMOM, HIIT, Tabata, rounds de
boxeo y cualquier rutina por minutos.

### Qué hace

| Área | Detalle |
|---|---|
| Modo Rutina | Intervalos de trabajo/descanso configurables en segundos o minutos, de 1 a 99 series, indicador de fase (ENTRENA! / DESCANSO) y contador de serie. Pausar en mitad del trabajo es seguro: la fase se reanuda exactamente donde estaba |
| Death by Burpees | Pitido en cada minuto con aviso de 10 s, contador de burpees por round, animación de color progresiva de cian (`#00BCD4`) a rojo (`#FF5252`) según se agota el tiempo, 1 a 999 minutos |
| Presets | Tres rutinas integradas (Tabata 20/10 ×8, EMOM 10 = 10×60/0, AMRAP 20 = 4×300/60) y guardado de la configuración actual con nombre |
| Historial | Cada sesión completada se guarda con su modo y duración, con total acumulado y racha de días consecutivos; conserva las 100 últimas |
| Sonido | **Sintetizado en tiempo real con `AudioTrack`**: la app no lleva ningún fichero de audio. Todos los búferes se generan una vez al arrancar y se sirven desde caché, así que el temporizador no recalcula nada |
| Ajustes | Sonido, vibración y audio focus con interruptores independientes, y se recuerdan entre sesiones |

Los sonidos concretos: ticks de preparación ascendentes 500→800 Hz con vibración
que acaban en un doble tono de "GO"; 10 pitidos de aviso escalonados en
frecuencia y volumen antes de cada cambio de fase o minuto; tono largo de ~900 ms
con armónicos para trabajo/minuto; secuencia descendente suave para descanso; y
resolución final de cuatro notas. Todo se enruta por `USAGE_ALARM`, y la app
pide y devuelve el **audio focus** alrededor de cada reproducción para que
corte por encima del resto de audio. Cada señal de audio va acompañada de
vibración (un buzz corto, o un patrón en la final).

El tema es Material 3 oscuro con layouts XML, con barra de progreso circular de
gradiente, y los controles tienen `contentDescription` para TalkBack.

## 📦 Compilar desde código

La raíz del repositorio **es** el proyecto Gradle: no hay subdirectorio
`android/`. Todos los comandos de esta sección se ejecutan desde ahí.

### Requisitos

- **JDK 17** (obligatorio: `sourceCompatibility` y `jvmTarget` son 17, y el
  build usa *core library desugaring*).
- **Android SDK** con la plataforma `android-35` instalada (o Android Studio,
  que la gestiona por ti).
- Nada más: el **Gradle Wrapper 8.5** va incluido, así que no hace falta
  instalar Gradle. Usa siempre `./gradlew`, no un Gradle del sistema.

### Clonar

```bash
# 1. Clonar el repositorio
git clone https://github.com/disruptorh/Death-by-burpees-timer.git
cd Death-by-burpees-timer
```

### Dependencias

Gradle necesita saber dónde está tu SDK de Android. Eso se guarda en
`local.properties`, en la raíz del repo, con una única línea `sdk.dir=`.

> **Aviso:** `local.properties` está en `.gitignore`, pero si copiaste el repo
> desde otra máquina puede venir con la ruta del SDK de esa otra máquina, que en
> tu equipo no existe. Si te aparece un error tipo "SDK location not found",
> sobrescribe el fichero con el bloque de abajo.

```bash
# 2. Crear local.properties apuntando a tu SDK de Android
printf 'sdk.dir=%s\n' "$HOME/Android/Sdk" > local.properties
```

Si tu SDK está en otro sitio, cambia la ruta: en macOS suele ser
`$HOME/Library/Android/sdk` y en Windows
`C:\Users\<tu-usuario>\AppData\Local\Android\Sdk`. Comprueba cuál es con
`ls $HOME/Android/Sdk` o, si usas Android Studio, con **Settings → Languages &
frameworks → Android SDK → SDK location**.

El build descarga las dependencias de `google()` y `mavenCentral()` la primera
vez, así que hace falta conexión a internet en el primer `./gradlew`.

### Compilar

```bash
# 3. Compilar el APK de depuración (va firmado con el keystore de debug que crea el SDK)
./gradlew :app:assembleDebug
```

Sale en `app/build/outputs/apk/debug/app-debug.apk`. Se instala en un
dispositivo conectado, sin configuración adicional:

```bash
# 4. Instalar el APK de depuración en el dispositivo conectado
./gradlew :app:installDebug
```

O a mano, con la ruta exacta del fichero:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

El APK de **release** (minificado con R8 y con los recursos reducidos) necesita
un keystore propio: mira [Firma del APK](#firma-del-apk).

```bash
# 5. Compilar el APK de release (sin firmar si no has configurado un keystore)
./gradlew :app:assembleRelease
```

### Ejecutar los tests

21 tests unitarios JVM sobre `TimerEngine`, sin emulador:

```bash
./gradlew :app:testDebugUnitTest
```

Cubren la cuenta atrás de preparación, las transiciones de fase, la contabilidad
de pausa/reanudación, las marcas de minuto y avisos de Death by Burpees, la
recuperación tras un congelamiento del proceso y la validación de rangos:

- La preparación dura exactamente 5 s y emite los ticks descendentes antes de
  empezar el trabajo.
- El trabajo avisa en los últimos 10 s, va seguido del descanso y el descanso
  incrementa la serie; con descanso 0 se salta directamente a la siguiente.
- La rutina termina tras el número de series configurado.
- La pausa congela el tiempo restante y la reanudación continúa desde ahí;
  pausa y reanudar se ignoran si el motor no está en ese estado.
- Death by Burpees pita en las marcas de minuto anunciando el nuevo conteo de
  burpees, **no** pita en el minuto cero, avisa 10 s antes de cada minuto, y
  termina una sola vez al final de la sesión.
- Un único tick enorme recupera todos los límites perdidos tras un congelamiento.
- `stop` devuelve el motor a un estado idle limpio y no se emiten eventos en
  pausa ni en reposo.
- Rechaza duraciones de trabajo por debajo del mínimo y series por encima del
  máximo, acepta los valores límite documentados, y soporta minutos de más de
  una hora.
- `ceilToSecond` redondea hacia arriba los milisegundos parciales.

Los informes XML quedan en `app/build/test-results/testDebugUnitTest/`.

El análisis estático va aparte, y es lo que corre el CI:

```bash
./gradlew :app:lintDebug
```

### Ejecutar la aplicación

Con Android Studio: abre la carpeta del repo y pulsa **Run** sobre la
configuración `app`. Sin Android Studio, `installDebug` (o el `adb install` de
arriba) la deja instalada y lista para lanzar con un toque en el icono.

## 🏗️ Arquitectura

| Pieza | Responsabilidad |
|---|---|
| `ModeSelectionActivity` | Launcher: presets, elección de modo, y enlaces a ajustes e historial |
| `MainActivity` | Modo Rutina |
| `DeathBurpeesActivity` | Death by Burpees |
| `SettingsActivity` | Sonido, vibración, audio focus |
| `HistoryActivity` | Historial de sesiones completadas |
| `TimerEngine` | Máquina de estados pura del temporizador, sin imports de Android |
| `TimerService` | Servicio en primer plano: alimenta el motor y traduce eventos en sonido, vibración y notificación |
| `TimerViewModel` | Configuración editable y persistencia de cada cambio |
| `SoundManager` | Síntesis de audio y gestión del audio focus |
| `PresetManager` / `HistoryManager` | Presets integrados y de usuario / registro de sesiones y racha |
| `SoundPreferences` / `PreferencesManager` | Ajustes de audio y hapticidad / configuración del modo Rutina |

Tecnologías: Kotlin, MVVM con `LiveData`, `AudioTrack`, servicio en primer plano
de tipo `mediaPlayback`, `SharedPreferences`, `RecyclerView`, Material 3 con
layouts XML (sin ViewBinding) y corrutinas.

**El motor no cuenta atrás.** `TimerEngine` no decrementa ningún contador:
guarda un **deadline absoluto** y lo compara con una lectura monótona
(`SystemClock.elapsedRealtime()`). De ahí salen tres cosas:

- **Sin deriva.** Un tick tardío, coalescido o perdido no hace que el
  temporizador pierda tiempo.
- **Aguanta congelamientos.** Si el proceso se congela, el siguiente tick
  recupera todos los límites perdidos y la sesión termina igualmente a su hora.
- **La pausa es exacta.** Al reanudar, el deadline se recalcula desde la
  lectura actual, así que pausar no cuesta tiempo.

`TimerService` pasa una lectura al motor cada 50 ms y traduce los `TimerEvent`
en sonido, vibración y notificación. Publica `TimerState` (IDLE/RUNNING/PAUSED)
y `TimerPhase` (PREPARE/WORK/REST) como `LiveData`; las dos Activities se
enlazan al servicio y observan directamente. `TimerViewModel` solo guarda la
configuración editable y persiste cada cambio.

El servicio devuelve **`START_NOT_STICKY`** a propósito: el estado del motor no
se persiste, así que un reinicio del sistema no podría reanudar una sesión, y
un arranque *sticky* solo dejaría un servicio zombi que nunca llama a
`startForeground`.

### Rangos admitidos

| Ajuste | Rango | Valor por defecto |
|---|---|---|
| Duración de trabajo | 5–3600 s | 60 s |
| Duración de descanso | 0–3600 s | 180 s |
| Número de series | 1–99 | 8 |
| Duración total (Burpees) | 1–999 min | 5 min |

`TimerEngine.Config.isValid()` rechaza cualquier valor fuera de rango,
`TimerService` valida antes de configurar, y el motor normaliza con `coerceIn`
antes de trabajar: nunca recibe una configuración inválida.

## 🧰 Comandos útiles

| Tarea | Comando | Qué hace |
|---|---|---|
| Compilar debug | `./gradlew :app:assembleDebug` | APK de depuración en `app/build/outputs/apk/debug/app-debug.apk` |
| Instalar debug | `./gradlew :app:installDebug` | Instala en el dispositivo conectado |
| Compilar release | `./gradlew :app:assembleRelease` | APK de release (R8 + shrink de recursos) |
| Tests | `./gradlew :app:testDebugUnitTest` | Los 21 tests unitarios de `TimerEngine` |
| Lint | `./gradlew :app:lintDebug` | Análisis estático (lo que corre el CI) |
| Compilar todo | `./gradlew :app:assemble` | Debug y release de una vez |
| Limpiar | `./gradlew clean` | Borra los ficheros generados |
| Ver tareas | `./gradlew :app:tasks --all` | Lista todas las tareas disponibles |

## ✅ CI

`.github/workflows/ci.yml` corre en cada `push` y `pull_request` a `main`, sobre
`ubuntu-latest`:

1. `actions/checkout@v4`
2. `actions/setup-java@v4` con Temurin 17
3. `gradle/actions/setup-gradle@v3`
4. `./gradlew testDebugUnitTest`
5. `./gradlew lintDebug`
6. `./gradlew assembleDebug`

Para replicarlo en local, esos mismos tres comandos:

```bash
./gradlew :app:testDebugUnitTest && ./gradlew :app:lintDebug && ./gradlew :app:assembleDebug
```

El CI no firma nada: construye el APK de depuración. Una release firmada sale
del flujo de firma que se explica a continuación.

## 🔐 Firma del APK

`app/build.gradle.kts` lee un fichero `app/keystore.properties` y solo aplica la
configuración de firma si ese fichero existe **y las cuatro propiedades están
presentes y no vacías**. Si falta una, el build no falla: simplemente sale sin
firmar.

**Qué pasa si no tienes keystore propio:** `./gradlew :app:assembleRelease`
termina sin errores, pero produce `app-release-unsigned.apk`. Android no lo
puede instalar: un APK sin firma se rechaza. Para instalar y probar, compila
`assembleDebug`, que va firmado con el keystore de depuración que genera el
propio SDK de Android.

Para firmar el release tienes que aportar tu propio keystore. Este bloque usa
valores **de prueba** (`clave-local-de-pruebas` / `mi-alias`) que funcionan tal
cual al pegar; cámbialos por los tuyos si prefieres.

```bash
# 1. Crear un keystore local de pruebas DENTRO de app/ (el del release publicado no está en el repo)
rm -f app/mi-keystore.jks
keytool -genkeypair -v -keystore app/mi-keystore.jks -alias mi-alias -keyalg RSA -keysize 2048 -validity 10000 -storepass 'clave-local-de-pruebas' -keypass 'clave-local-de-pruebas' -dname "CN=Pruebas locales, C=ES"

# 2. Crear app/keystore.properties con esos mismos valores
cat > app/keystore.properties <<'EOF'
storeFile=mi-keystore.jks
storePassword=clave-local-de-pruebas
keyAlias=mi-alias
keyPassword=clave-local-de-pruebas
EOF

# 3. Compilar el release ya firmado
./gradlew :app:assembleRelease
```

Qué significa cada propiedad:

- `storeFile` — ruta del keystore, **relativa al directorio `app/`**. Con el
  bloque de arriba queda en `app/mi-keystore.jks`.
- `storePassword` — contraseña del almacén del keystore.
- `keyAlias` — alias de la clave dentro del keystore (el que le diste a
  `keytool -alias`).
- `keyPassword` — contraseña de esa clave.

`app/keystore.properties`, `*.keystore` y `*.jks` están en `.gitignore`: **no los
subas nunca a Git**. Son las cuatro líneas que guardan tu clave de firma.

Con tu keystore, el resultado es `app/build/outputs/apk/release/app-release.apk`
(firmado). Ojo con esto: tu firma es distinta de la del APK publicado en
releases, así que Android no te deja instalarlo encima. Desinstala la versión
anterior primero:

```bash
adb uninstall com.timer.minimal
adb install -r app/build/outputs/apk/release/app-release.apk
```

## 🗂️ Estructura del proyecto

```text
.
├── app/
│   ├── build.gradle.kts              # SDK, firma release, desugaring, dependencias
│   ├── proguard-rules.pro            # reglas R8 del release
│   ├── keystore.properties           # NO se versiona: credenciales de firma (tú lo creas)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml    # foreground service, notificaciones, wake lock, vibrate
│       │   ├── java/com/timer/minimal/
│       │   │   ├── ModeSelectionActivity.kt  # launcher: presets, elección de modo
│       │   │   ├── MainActivity.kt           # modo rutina
│       │   │   ├── DeathBurpeesActivity.kt   # death by burpees
│       │   │   ├── SettingsActivity.kt       # sonido / vibración / audio focus
│       │   │   ├── HistoryActivity.kt        # historial de sesiones
│       │   │   ├── TimerEngine.kt            # máquina de estados pura
│       │   │   ├── TimerService.kt           # servicio en primer plano
│       │   │   ├── TimerViewModel.kt         # configuración editable
│       │   │   ├── SoundManager.kt           # síntesis de audio + audio focus
│       │   │   ├── PresetManager.kt          # presets
│       │   │   ├── HistoryManager.kt         # historial y racha
│       │   │   ├── SoundPreferences.kt
│       │   │   └── PreferencesManager.kt
│       │   └── res/
│       │       ├── drawable/                  # iconos y formas (incluye circular_progress)
│       │       ├── layout/                    # activities, item_history, item_preset
│       │       ├── mipmap-anydpi-v26/         # iconos adaptativos
│       │       ├── mipmap-hdpi/ … xxxhdpi/    # iconos por densidad
│       │       └── values/                    # colores, strings, tema
│       └── test/java/com/timer/minimal/
│           └── TimerEngineTest.kt    # 21 tests del motor
├── images/                          # capturas para el README
├── .github/workflows/ci.yml         # tests + lint + assembleDebug en push y PR
├── build.gradle.kts                 # versiones de AGP 8.2.2 y Kotlin 1.9.22
├── settings.gradle.kts              # repositorios google() + mavenCentral(); módulo :app
├── gradle.properties                # AndroidX, 2 GB de heap para Gradle
├── gradle/wrapper/                  # Gradle Wrapper 8.5 (incluido)
├── gradlew                          # siempre ./gradlew
├── local.properties                 # NO se versiona: la ruta de tu SDK (sdk.dir=)
├── LICENSE                          # Apache-2.0
└── README.md
```

## 📄 Licencia

Apache-2.0 — ver [`LICENSE`](./LICENSE).

Autor: [Reimen](https://github.com/disruptorh).