package com.timer.minimal

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "timer_prefs"
        private const val KEY_LAST_DURATION = "last_duration_minutes"
        private const val KEY_WORK_DURATION = "work_duration_seconds"
        private const val KEY_REST_DURATION = "rest_duration_seconds"
        private const val KEY_TOTAL_SETS = "total_sets"
        private const val DEFAULT_DURATION = 5
        private const val DEFAULT_WORK_DURATION = 60  // 1 minuto
        private const val DEFAULT_REST_DURATION = 180 // 3 minutos
        private const val DEFAULT_TOTAL_SETS = 8
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    fun saveLastDuration(minutes: Int) {
        prefs.edit().putInt(KEY_LAST_DURATION, minutes).apply()
    }
    
    fun getLastDuration(): Int {
        return prefs.getInt(KEY_LAST_DURATION, DEFAULT_DURATION)
    }
    
    // Duración de trabajo (segundos)
    fun saveWorkDuration(seconds: Int) {
        prefs.edit().putInt(KEY_WORK_DURATION, seconds).apply()
    }
    
    fun getWorkDuration(): Int {
        return prefs.getInt(KEY_WORK_DURATION, DEFAULT_WORK_DURATION)
    }
    
    // Duración de descanso (segundos)
    fun saveRestDuration(seconds: Int) {
        prefs.edit().putInt(KEY_REST_DURATION, seconds).apply()
    }
    
    fun getRestDuration(): Int {
        return prefs.getInt(KEY_REST_DURATION, DEFAULT_REST_DURATION)
    }
    
    // Número total de series
    fun saveTotalSets(sets: Int) {
        prefs.edit().putInt(KEY_TOTAL_SETS, sets).apply()
    }
    
    fun getTotalSets(): Int {
        return prefs.getInt(KEY_TOTAL_SETS, DEFAULT_TOTAL_SETS)
    }
}
