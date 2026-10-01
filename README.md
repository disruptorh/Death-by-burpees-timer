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
- Visual phase indicator (**ENTRENA!** / **DESCANSO**)
- Set counter showing current progress
- Pausing mid-work is safe: the phase resumes exactly where it stopped

#### 2. Death by Burpees Mode
- Timer sounds every minute with a **10-second warning**
- **Burpee counter** showing how many burpees to do each round
- **Progressive color animation** from cyan to red as time runs out
- Configurable duration (1-999 minutes)
- Minimalist skull icon interface

### ⚡ Quick Presets

- Three built-in routines (**Tabata**, **EMOM 10**, **AMRAP 20**) available in one tap
- Saving your own configuration as a named preset for repeat sessions
- A preset opens straight into its timer screen with the values pre-filled

### 📜 Session History

- Every completed session is logged with its mode and duration
- Running total and current **consecutive-day streak**
- Keeps the last 100 sessions

### 🔊 Audio System

All sounds are **synthesized in real-time** using `AudioTrack` — no audio assets ship with the app. Every buffer is generated once at startup and served from a cache, so nothing is recomputed while the timer runs.

| Sound | Description |
|-------|-------------|
| **Prepare ticks** | 5-second pre-start countdown: ascending 500→800 Hz ticks with vibration, ending on a double-tone "GO!" |
| **Warning** | 10 escalating beeps before each phase or minute ends (rising frequency and volume) |
| **Work/Minute** | Long tone (~900 ms) with harmonics, like a race start whistle |
| **Rest** | Soft descending three-tone sequence |
| **End** | Clear four-note resolution |

**Note:** tones are routed through `USAGE_ALARM` and the app requests and abandons Android **audio focus** around playback, so they cut through other audio. Haptic feedback pairs with every cue (short buzz, or a pattern for the final one).

### 🔧 Settings

- Toggle sound, vibration, and audio focus (ducking other apps) independently
- Settings persist across sessions

### 📐 UI/UX Features

- 🌙 **Dark theme** — Material 3 (`Theme.Material3.Dark.NoActionBar`) with XML layouts
- 📊 **Circular progress bar** with gradient colors
- ⏱️ **5-second prepare countdown** with ascending ticks before every session
- 🔄 **Background operation** — a `FOREGROUND_SERVICE` of type `mediaPlayback` keeps the timer alive with the screen off
- 🔔 **Live notification** — remaining time updates in-place, with **Pause / Resume / Stop** actions so the timer is fully controllable from the lock screen
- 💾 **Auto-save settings** — remembers your configuration across sessions
- ⏸️ **Pause/Resume** from the app or the notification
- ♿ **Accessible** — labelled controls and live descriptions for the timer and burpee counter
- 🌍 **Spanish UI**

---

## 🚀 Getting Started

### Prerequisites

- Android Studio (latest version recommended) or a standalone JDK **17**
- Android SDK 24+ (`compileSdk` / `targetSdk` 35)
- Gradle 8.5 (bundled via `gradlew`, no local install needed)
- Kotlin 1.9.22

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/disruptorh/Death-by-burpees-timer.git
   cd Death-by-burpees-timer
   ```

2. **Open in Android Studio** — or build from the command line:
   ```bash
   ./gradlew assembleDebug     # APK de depuración, sin firmar
   ./gradlew testDebugUnitTest # tests unitarios
   ./gradlew lintDebug         # análisis estático
   ```

3. **Install the APK**
   - APK location: `app/build/outputs/apk/debug/app-debug.apk`
   - Or `./gradlew installDebug` to install it on a connected device

### Releasing (optional)

`assembleDebug` works on a fresh clone with no setup. To produce a **signed** release build, create `app/keystore.properties` (gitignored):

```properties
storeFile=/ruta/a/tu/keystore.jks
storePassword=...
keyAlias=...
keyPassword=...
```

The build script only applies the signing config when that file exists and contains all four keys, so every other task keeps working without it. Never commit the keystore or its credentials.

---

## 📱 How to Use

### Mode Selection
1. Open the app
2. Tap a quick preset (Tabata / EMOM 10 / AMRAP 20) to jump straight in, or choose **Modo Rutina** / **Muerte por Burpees** to configure a session manually

### Routine Mode
1. Set work duration (5 s – 60 min)
2. Set rest duration (0 – 60 min)
3. Set number of sets (1-99)
4. Tap **Play**; a 5-second prepare countdown ticks up before the first work phase
5. Listen for audio cues during phase transitions

The sec/min toggle only changes how the number is *displayed* — it never rescales the value behind your back.

### Death by Burpees Mode
1. Set total duration in minutes
2. Tap **Play** to start
3. Do **1 burpee** when the timer starts
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
| **SharedPreferences** | Settings, presets and history persistence |
| **RecyclerView** | Preset and history lists |
| **Material 3** | XML UI components |
| **Coroutines** | Off-thread sound generation and playback |

### Key Components

```text
app/src/main/java/com/timer/minimal/
├── ModeSelectionActivity.kt  # Launcher: presets, mode picker, links
├── MainActivity.kt           # Routine mode UI
├── DeathBurpeesActivity.kt   # Death by Burpees UI
├── SettingsActivity.kt       # Sound / vibration / audio focus
├── HistoryActivity.kt        # Completed session log
├── TimerEngine.kt            # Pure timer state machine (no Android deps)
├── TimerService.kt           # Foreground service: drives the engine
├── TimerViewModel.kt         # Editable config, persists every change
├── SoundManager.kt           # Audio synthesis + audio focus
├── PresetManager.kt          # Built-in and user-saved presets
├── HistoryManager.kt         # Session log and streak
├── SoundPreferences.kt       # Audio/haptics settings
└── PreferencesManager.kt     # Routine configuration storage
```

### Architecture note

The countdown lives in **`TimerEngine`**, a plain Kotlin class with no Android imports, which makes it fully unit-testable. The engine never decrements a counter: it stores an **absolute deadline** and compares it against a monotonic reading (`SystemClock.elapsedRealtime()`). Consequences:

- **No drift.** Late, coalesced or dropped ticks cannot make the timer lose time.
- **Freeze-safe.** If the process is frozen, the next tick catches up across every missed boundary and the session still ends on schedule.
- **Pause is exact.** Resuming recomputes the deadline from the current reading, so the pause itself costs no time.

`TimerService` feeds the engine a reading every 50 ms and translates the emitted `TimerEvent`s into sound, vibration and notification updates. It publishes `TimerState` (IDLE/RUNNING/PAUSED) and `TimerPhase` (PREPARE/WORK/REST) as `LiveData`; both Activities bind to the service and observe those directly. `TimerViewModel` holds only the editable configuration (work 5–3600 s, rest 0–3600 s, 1–99 sets, 1–999 min) and persists each change.

The service returns **`START_NOT_STICKY`** on purpose: engine state is not persisted, so a system restart could not resume a session, and a sticky restart would only leave a zombie service that never calls `startForeground`.

### Validation ranges

| Setting | Accepted range | Default |
|---|---|---|
| Work duration | 5–3600 s | 60 s |
| Rest duration | 0–3600 s | 180 s |
| Total sets | 1–99 | 8 |
| Total duration | 1–999 min | 5 min |

`TimerEngine.Config.isValid()` rejects anything outside these ranges, and `TimerService` validates before configuring, so the engine never receives an invalid configuration.

### Tests

`app/src/test/` contains unit tests for `TimerEngine` covering the prepare countdown, phase transitions, pause/resume accounting, Death by Burpees minute marks and warnings, tick catch-up after a freeze, and configuration validation.

```bash
./gradlew testDebugUnitTest
```

---

## 🎯 Use Cases

- **Death by Burpees** - Classic CrossFit workout
- **EMOM** - Every Minute On the Minute
- **HIIT Training** - High-Intensity Interval Training
- **Tabata Workouts** - built-in preset
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
