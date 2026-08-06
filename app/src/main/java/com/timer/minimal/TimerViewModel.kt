package com.timer.minimal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

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
 * TimerViewModel: thin proxy that stores preferences and relays service state to the UI.
 * NO timer logic here — all countdown runs in TimerService.
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

    // Modo actual del temporizador
    private var _timerMode: TimerMode = TimerMode.ROUTINE
    val timerMode: TimerMode get() = _timerMode

    // --- Service reference (set by Activity after binding) ---
    private var _service: TimerService? = null

    fun bindService(service: TimerService) {
        _service = service
    }

    fun unbindService() {
        _service = null
    }

    val service: TimerService? get() = _service

    // --- Convenience accessors for service LiveData ---
    // Activities should observe these directly from the service once bound.

    fun setTimerMode(mode: TimerMode) {
        _timerMode = mode
    }

    fun setWorkDuration(seconds: Int) {
        if (seconds in 5..3600) {
            _workDuration.value = seconds
            preferencesManager.saveWorkDuration(seconds)
        }
    }

    fun setRestDuration(seconds: Int) {
        if (seconds in 0..3600) {
            _restDuration.value = seconds
            preferencesManager.saveRestDuration(seconds)
        }
    }

    fun setTotalSets(sets: Int) {
        if (sets in 1..99) {
            _totalSets.value = sets
            preferencesManager.saveTotalSets(sets)
        }
    }

    fun setInputMinutes(minutes: Int) {
        if (minutes in 1..999) {
            _inputMinutes.value = minutes
            preferencesManager.saveLastDuration(minutes)
        }
    }

    fun formatTime(timeMs: Long): String {
        val totalSeconds = (timeMs / 1000).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}
