# Death by Burpees Timer ⏱️

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green.svg" alt="Platform">
  <img src="https://img.shields.io/badge/Language-Kotlin-blue.svg" alt="Language">
  <img src="https://img.shields.io/badge/Min%20SDK-24-orange.svg" alt="Min SDK">
  <img src="https://img.shields.io/badge/License-Apache--2.0-yellow.svg" alt="License">
</p>

<p align="center">
  <a href="https://github.com/disruptorh/Death-by-burpees-timer/releases/latest">
    <img src="https://img.shields.io/badge/Download_APK-Latest_Release-2EA44F?style=for-the-badge&logo=android" alt="Download APK">
  </a>
</p>

<p align="center">
  <strong>A minimalist Android timer with premium audio feedback for interval training</strong>
</p>

---

## 📱 Screenshots

<p align="center">
  <img src="https://github.com/disruptorh/Death-by-burpees-timer/blob/main/images/mode%20selection.png" alt="Mode Selection" width="250"/>
  <img src="https://github.com/disruptorh/Death-by-burpees-timer/blob/main/images/routine%20mode.png" alt="Routine Mode" width="250"/>
  <img src="https://github.com/disruptorh/Death-by-burpees-timer/blob/main/images/death%20by%20burpees.png" alt="Death by Burpees" width="250"/>
</p>

---

## 📋 Overview

**Death by Burpees Timer** is a minimalist Android application designed for interval training workouts. The app provides precise audio feedback without requiring you to look at your phone, making it perfect for exercises like burpees, HIIT, EMOM, or any minute-based workout routine.

---

## ✨ Features

### 🎯 Two Training Modes

#### 1. Routine Mode (Modo Rutina)
- Customizable **work** and **rest** intervals
- Support for **seconds or minutes** in each phase
- Configurable number of **sets** (1-99)
- Visual phase indicator (**TRABAJO** / **DESCANSO**)
- Set counter showing current progress

#### 2. Death by Burpees Mode
- Timer sounds every minute with **10-second warning**
- **Burpee counter** showing how many burpees to do each round
- **Progressive color animation** from blue to red as time runs out
- Configurable duration (1-999 minutes)
- Minimalist skull icon interface

---

### 🔊 Premium Audio System

All sounds are **synthesized in real-time** using `AudioTrack` — no audio assets ship with the app. Generated buffers are cached per sound type so they are not recomputed on every tick.

| Sound | Description |
|-------|-------------|
| **Prepare ticks** | 5-second pre-start countdown: ascending 500→800 Hz ticks with vibration, ending on a double-tone "GO!" |
| **Warning** | Progressive beeps before each minute (rising frequency and volume) |
| **Work/Minute** | Long tone (~900 ms) with harmonics, like a race start whistle |
| **Rest** | Soft descending three-tone sequence |
| **End** | Clear four-note resolution |

**Note:** All sounds respect the user's **media volume** settings. The app requests and abandons Android **audio focus** around playback, and pairs tones with **haptic feedback** (short buzz, or a pattern for the final cue).

---

### 📐 UI/UX Features

- 🌙 **Dark theme** — Material 3 (`Theme.Material3.Dark.NoActionBar`) with XML layouts and ViewBinding
- 📊 **Circular progress bar** with gradient colors
- ⏱️ **5-second prepare countdown** with ascending ticks before every session
- 🔄 **Background operation** — a `FOREGROUND_SERVICE` of type `mediaPlayback` keeps the timer alive with the screen off
- 🔔 **Live notification** — the remaining time is updated in-place while the timer runs
- 💾 **Auto-save settings** — remembers your configuration across sessions
- ⏸️ **Pause/Resume** functionality
- 📳 **Vibration** on every cue, for training with the phone in a pocket

---

## 🚀 Getting Started

### Prerequisites

- Android Studio (latest version recommended) or a standalone JDK **17**
- Android SDK 24+ (`compileSdk` / `targetSdk` 34)
- Gradle 8.5 (bundled via `gradlew`, no local install needed)
- Kotlin 1.9.22

### ⚠️ Before the first build: `keystore.properties`

`app/build.gradle.kts` reads `app/keystore.properties` at **configuration time**, unconditionally — before it even decides which build type you asked for. That file is gitignored, so on a fresh clone **every** Gradle command fails with a `FileNotFoundException` until you create it, including plain `assembleDebug`:

```properties
storeFile=/ruta/a/tu/keystore.jks
storePassword=...
keyAlias=...
keyPassword=...
```

It is only actually used by the `release` build type. Keep it out of version control.

### Installation

1. **Clone the repository**

   ```bash
   git clone https://github.com/disruptorh/Death-by-burpees-timer.git
   cd Death-by-burpees-timer
   ```

2. **Create `app/keystore.properties`** (see above)

3. **Open in Android Studio**
   - Launch Android Studio
   - Select "Open an existing project"
   - Navigate to the cloned directory

4. **Build the project**
   ```bash
   ./gradlew assembleDebug     # APK de depuración, sin firmar
   ./gradlew assembleRelease   # APK firmado (necesita el keystore)
   ```

5. **Install the APK**
   - APK location: `app/build/outputs/apk/debug/app-debug.apk`
   - O bien `./gradlew installDebug` para instalarlo directamente en el dispositivo

---

## 📱 How to Use

### Mode Selection
1. Open the app
2. Choose between **Modo Rutina** or **Muerte por Burpees**

### Routine Mode
1. Set work duration (5 s – 60 min)
2. Set rest duration (0 – 60 min)
3. Set number of sets (1-99)
4. Tap **Play**; a 5-second prepare countdown ticks up before the first work phase
5. Listen for audio cues during phase transitions

### Death by Burpees Mode
1. Set total duration in minutes
2. Tap **Play** to start
3. Do **1 burpee** when timer starts
4. At each minute mark (long beep), add **+1 burpee**
5. The burpee counter shows how many to do each round

---

## 🏗️ Technical Architecture

### Technologies Used

| Technology | Purpose |
|------------|---------|
| **Kotlin** | Main programming language (JVM target 17) |
| **MVVM** | Architecture pattern |
| **LiveData** | State observation between service, ViewModel and UI |
| **AudioTrack** | Custom sound synthesis |
| **Foreground Service** (`mediaPlayback`) | Background operation |
| **SharedPreferences** | Settings persistence |
| **Material 3** + ViewBinding | XML UI components |
| **Coroutines** | Off-thread sound generation and playback |

### Permissions

| Permission | Why |
|------------|-----|
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep the timer running with the screen off |
| `WAKE_LOCK` | Prevent the CPU from sleeping mid-interval |
| `POST_NOTIFICATIONS` | Show the live countdown notification (Android 13+) |
| `VIBRATE` | Haptic feedback on every cue |

### Key Components

```text
app/src/main/java/com/timer/minimal/
├── ModeSelectionActivity.kt  # Launcher screen, picks the mode
├── MainActivity.kt           # Routine mode UI
├── DeathBurpeesActivity.kt   # Death by Burpees UI
├── TimerService.kt           # Foreground service: ALL countdown logic
├── TimerViewModel.kt         # Thin proxy — config + relays service state
├── SoundManager.kt           # Custom audio synthesis + audio focus
└── PreferencesManager.kt     # SharedPreferences wrapper
```

**Architecture note:** the countdown does **not** live in the ViewModel. `TimerService` owns a single `CountDownTimer` and publishes `TimerState` (IDLE/RUNNING/PAUSED) and `TimerPhase` (PREPARE/WORK/REST) as `LiveData`; both Activities bind to the service and observe those directly. `TimerViewModel` is deliberately logic-free — it holds the editable configuration (work 5–3600 s, rest 0–3600 s, 1–99 sets, 1–999 min) and persists each change through `PreferencesManager`.

### Validation ranges

| Setting | Accepted range | Default |
|---|---|---|
| Work duration | 5–3600 s | 60 s |
| Rest duration | 0–3600 s | 180 s |
| Total sets | 1–99 | 8 |
| Total duration | 1–999 min | 5 min |

Values outside these ranges are rejected by the ViewModel, so the service never receives an invalid configuration.

### Tests

There are no automated tests in this repository yet; verification has been manual on-device.

---

## 🎯 Use Cases

- **Death by Burpees** - Classic CrossFit workout
- **EMOM** - Every Minute On the Minute
- **HIIT Training** - High-Intensity Interval Training
- **Tabata Workouts** - Customized intervals
- **Boxing Rounds** - Minute-based round training

---

##  License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

---

## 👤 Author

**Reimen**

- GitHub: [@disruptorh](https://github.com/disruptorh)
- Project Link: [Death-by-burpees-timer](https://github.com/disruptorh/Death-by-burpees-timer)

---

<p align="center">
  Made with 💪 for athletes everywhere
</p>

<p align="center">
  <strong>Don't forget to ⭐ this repo if you found it useful!</strong>
</p>
