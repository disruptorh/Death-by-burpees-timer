package com.timer.minimal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.Locale

enum class TimerState {
    IDLE,
    RUNNING,
    PAUSED
}

enum class TimerPhase {
    PREPARE,
    WORK,
    REST
}

enum class TimerMode {
    ROUTINE,       // Intervalos trabajo/descanso
    DEATH_BURPEES  // Beep cada minuto con aviso 10s
}

/**
 * TimerViewModel: holds the editable configuration and persists every change.
 * It contains no timer logic — the countdown lives in [TimerEngine] and is
 * driven by [TimerService].
 */
class TimerViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)

    // --- Configuration (editable by UI) ---
    private val _workDuration = MutableLiveData(preferencesManager.getWorkDuration())
    val workDuration: LiveData<Int> = _workDuration

    private val _restDuration = MutableLiveData(preferencesManager.getRestDuration())
    val restDuration: LiveData<Int> = _restDuration

    private val _totalSets = MutableLiveData(preferencesManager.getTotalSets())
    val totalSets: LiveData<Int> = _totalSets

    private val _inputMinutes = MutableLiveData(preferencesManager.getLastDuration())
    val inputMinutes: LiveData<Int> = _inputMinutes

    private var _timerMode: TimerMode = TimerMode.ROUTINE
    val timerMode: TimerMode get() = _timerMode

    fun setTimerMode(mode: TimerMode) {
        _timerMode = mode
    }

    /**
     * Applies a saved preset, which also makes it the new default for that mode.
     * Out-of-range values are rejected so the service never sees an invalid config.
     */
    fun applyPreset(preset: Preset) {
        when (preset.mode) {
            TimerMode.ROUTINE -> {
                setWorkDuration(preset.workDurationSec)
                setRestDuration(preset.restDurationSec)
                setTotalSets(preset.totalSets)
            }
            TimerMode.DEATH_BURPEES -> setInputMinutes(preset.inputMinutes)
        }
    }

    fun setWorkDuration(seconds: Int) {
        if (seconds in TimerEngine.MIN_WORK_SEC..TimerEngine.MAX_PHASE_SEC) {
            _workDuration.value = seconds
            preferencesManager.saveWorkDuration(seconds)
        }
    }

    fun setRestDuration(seconds: Int) {
        if (seconds in 0..TimerEngine.MAX_PHASE_SEC) {
            _restDuration.value = seconds
            preferencesManager.saveRestDuration(seconds)
        }
    }

    fun setTotalSets(sets: Int) {
        if (sets in TimerEngine.MIN_SETS..TimerEngine.MAX_SETS) {
            _totalSets.value = sets
            preferencesManager.saveTotalSets(sets)
        }
    }

    fun setInputMinutes(minutes: Int) {
        if (minutes in TimerEngine.MIN_MINUTES..TimerEngine.MAX_MINUTES) {
            _inputMinutes.value = minutes
            preferencesManager.saveLastDuration(minutes)
        }
    }

    fun formatTime(timeMs: Long): String {
        val totalSeconds = (timeMs / 1000).coerceAtLeast(0L)
        return String.format(Locale.getDefault(), "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
    }
}
