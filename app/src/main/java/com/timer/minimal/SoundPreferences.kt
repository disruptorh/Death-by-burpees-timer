package com.timer.minimal

import android.content.Context

/** User-tunable audio and haptics, persisted across sessions. */
data class SoundSettings(
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val audioFocusEnabled: Boolean = true
)

/**
 * Stores the [SoundSettings] in SharedPreferences.
 */
class SoundPreferences(context: Context) {

    companion object {
        private const val PREFS_NAME = "timer_sound_prefs"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_AUDIO_FOCUS_ENABLED = "audio_focus_enabled"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): SoundSettings = SoundSettings(
        soundEnabled = prefs.getBoolean(KEY_SOUND_ENABLED, true),
        vibrationEnabled = prefs.getBoolean(KEY_VIBRATION_ENABLED, true),
        audioFocusEnabled = prefs.getBoolean(KEY_AUDIO_FOCUS_ENABLED, true)
    )

    fun save(settings: SoundSettings) {
        prefs.edit()
            .putBoolean(KEY_SOUND_ENABLED, settings.soundEnabled)
            .putBoolean(KEY_VIBRATION_ENABLED, settings.vibrationEnabled)
            .putBoolean(KEY_AUDIO_FOCUS_ENABLED, settings.audioFocusEnabled)
            .apply()
    }
}
