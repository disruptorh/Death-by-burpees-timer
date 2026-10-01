package com.timer.minimal

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * Sound and haptics preferences. Changes take effect the next time a session
 * starts, because [SoundManager] reads the stored settings when it is created.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var switchSound: MaterialSwitch
    private lateinit var switchVibration: MaterialSwitch
    private lateinit var switchAudioFocus: MaterialSwitch

    private val soundPreferences by lazy { SoundPreferences(this) }
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        switchSound = findViewById(R.id.switchSound)
        switchVibration = findViewById(R.id.switchVibration)
        switchAudioFocus = findViewById(R.id.switchAudioFocus)

        val settings = soundPreferences.load()
        isLoading = true
        switchSound.isChecked = settings.soundEnabled
        switchVibration.isChecked = settings.vibrationEnabled
        switchAudioFocus.isChecked = settings.audioFocusEnabled
        isLoading = false

        val listener = { _: Boolean -> if (!isLoading) persist() }
        switchSound.setOnCheckedChangeListener { _, checked -> listener(checked) }
        switchVibration.setOnCheckedChangeListener { _, checked -> listener(checked) }
        switchAudioFocus.setOnCheckedChangeListener { _, checked -> listener(checked) }
    }

    private fun persist() {
        soundPreferences.save(
            SoundSettings(
                soundEnabled = switchSound.isChecked,
                vibrationEnabled = switchVibration.isChecked,
                audioFocusEnabled = switchAudioFocus.isChecked
            )
        )
    }
}
