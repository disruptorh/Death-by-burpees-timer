# Death by Burpees Timer ⏱️

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green.svg" alt="Platform">
  <img src="https://img.shields.io/badge/Language-Kotlin-blue.svg" alt="Language">
  <img src="https://img.shields.io/badge/Min%20SDK-24-orange.svg" alt="Min SDK">
  <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License">
</p>

<p align="center">
  <a href="https://github.com/reimen-cpu/Death-by-burpees-timer/releases/latest">
    <img src="https://img.shields.io/badge/Download_APK-Latest_Release-2EA44F?style=for-the-badge&logo=android" alt="Download APK">
  </a>
</p>

<p align="center">
  <strong>A minimalist Android timer with premium audio feedback for interval training</strong>
</p>

---

## 📱 Screenshots

<p align="center">
  <img src="https://github.com/reimen-cpu/Death-by-burpees-timer/blob/main/images/mode%20selection.png" alt="Mode Selection" width="250"/>
  <img src="https://github.com/reimen-cpu/Death-by-burpees-timer/blob/main/images/routine%20mode.png" alt="Routine Mode" width="250"/>
  <img src="https://github.com/reimen-cpu/Death-by-burpees-timer/blob/main/images/death%20by%20burpees.png" alt="Death by Burpees" width="250"/>
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

All sounds are **synthesized in real-time** using `AudioTrack` for maximum quality and control.

| Sound | Description |
|-------|-------------|
| **Warning** | 10 progressive beeps with increasing frequency (400→1000 Hz) and volume |
| **Work/Minute** | Long "beeeeep" (900ms) with harmonics, like a race start whistle |
| **Rest** | Soft descending tone for relaxation |
| **End** | Clear resolution sound |

**Note:** All sounds respect the user's **media volume** settings.

---

### 📐 UI/UX Features

- 🌙 **Dark theme** - Easy on the eyes
- 📊 **Circular progress bar** with gradient colors
- 🔄 **Background operation** - Works with screen off
- 💾 **Auto-save settings** - Remembers your configuration
- 🎨 **Material Design 3** components
- ⏸️ **Pause/Resume** functionality

---

## 🚀 Getting Started

### Prerequisites

- Android Studio (latest version recommended)
- Android SDK 24 or higher
- Kotlin plugin

### Installation

1. **Clone the repository**

   ```bash
   git clone https://github.com/reimen-cpu/Death-by-burpees-timer.git
   cd Death-by-burpees-timer
   ```

2. **Open in Android Studio**
   - Launch Android Studio
   - Select "Open an existing project"
   - Navigate to the cloned directory

3. **Build the project**
   ```bash
   ./gradlew assembleDebug
   ```

4. **Install the APK**
   - APK location: `app/build/outputs/apk/debug/app-debug.apk`

---

## 📱 How to Use

### Mode Selection
1. Open the app
2. Choose between **Modo Rutina** or **Muerte por Burpees**

### Routine Mode
1. Set work duration (seconds or minutes)
2. Set rest duration (seconds or minutes)
3. Set number of sets
4. Tap **Play** to start
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
| **Kotlin** | Main programming language |
| **MVVM** | Architecture pattern |
| **AudioTrack** | Custom sound synthesis |
| **Foreground Service** | Background operation |
| **SharedPreferences** | Settings persistence |
| **Material Design 3** | UI components |

### Key Components

```text
app/src/main/java/com/timer/minimal/
├── MainActivity.kt           # Routine mode UI
├── DeathBurpeesActivity.kt   # Death by Burpees UI
├── ModeSelectionActivity.kt  # Mode selection screen
├── TimerViewModel.kt         # Business logic & state
├── TimerService.kt           # Foreground service
└── SoundManager.kt           # Custom audio synthesis
```

---

## 🎯 Use Cases

- **Death by Burpees** - Classic CrossFit workout
- **EMOM** - Every Minute On the Minute
- **HIIT Training** - High-Intensity Interval Training
- **Tabata Workouts** - Customized intervals
- **Boxing Rounds** - Minute-based round training

---

##  License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 👤 Author

**Reimen**

- GitHub: [@reimen-cpu](https://github.com/reimen-cpu)
- Project Link: [Death-by-burpees-timer](https://github.com/reimen-cpu/Death-by-burpees-timer)

---

<p align="center">
  Made with 💪 for athletes everywhere
</p>

<p align="center">
  <strong>Don't forget to ⭐ this repo if you found it useful!</strong>
</p>
