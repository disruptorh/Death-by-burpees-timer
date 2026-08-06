package com.timer.minimal

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.sin

/**
 * SoundManager con audio sintetizado premium.
 * Genera tonos distintivos y agradables usando AudioTrack.
 */
class SoundManager(private val context: Context) {

    private val sampleRate = 44100
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: Any? = null // AudioFocusRequest on API 26+

    // Cache generated sounds to avoid re-generating on every tick
    private val soundCache = mutableMapOf<SoundType, ShortArray>()

    private enum class SoundType {
        MINUTE,
        WORK_START,
        REST_START,
        FINAL,
        WARNING_SHORT,
        WARNING_MEDIUM,
        WARNING_LONG,
        PREPARE_GO
    }

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    init {
        // Pre-generate common sounds synchronously to guarantee availability
        soundCache[SoundType.MINUTE] = generateLongToneRich(880.0, 900, 0.65f)
        soundCache[SoundType.WORK_START] = generateLongToneRich(880.0, 900, 0.65f)
        soundCache[SoundType.REST_START] = generateToneSequence(
            listOf(659.25, 523.25, 392.0),
            listOf(150, 150, 200),
            0.5f
        )
        soundCache[SoundType.FINAL] = generateToneSequence(
            listOf(523.25, 659.25, 783.99, 523.25),
            listOf(200, 200, 200, 400),
            0.6f
        )
        // "GO" tone for end of prepare countdown
        soundCache[SoundType.PREPARE_GO] = generateToneSequence(
            listOf(587.33, 880.0),
            listOf(150, 350),
            0.7f
        )
    }

    /**
     * Prepare countdown tick. Ascending frequency as seconds decrease.
     * Final second (1) plays a distinctive "GO" tone.
     */
    fun playPrepareTick(secondsRemaining: Int) {
        scope.launch {
            if (secondsRemaining <= 1) {
                // "GO!" — distinctive double-tone
                soundCache[SoundType.PREPARE_GO]?.let { playBuffer(it) }
                vibrateShort(200)
            } else {
                // Ascending tick: 5s=500Hz, 4s=600Hz, 3s=700Hz, 2s=800Hz
                val frequency = 500.0 + (300.0 * (1.0 - (secondsRemaining - 2).toDouble() / 3.0))
                val buffer = generateShortBeep(frequency, 80, 0.55f)
                playBuffer(buffer)
                vibrateShort(40)
            }
        }
    }

    /**
     * Request audio focus to duck other apps (e.g. Spotify)
     */
    private fun requestAudioFocus(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener { /* No-op */ }
                .build()
            audioFocusRequest = request
            return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            return audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                if (it is android.media.AudioFocusRequest) {
                    audioManager.abandonAudioFocusRequest(it)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    fun playWarningBeep(secondsToMinute: Int = 5) {
        scope.launch {
            // Calculate progress (10s=0.0, 1s=1.0)
            val progress = 1f - (secondsToMinute - 1) / 9f

            // Generate on the fly as parameters change dynamically,
            // but we could cache specific steps if needed.
            // For now, generating these short beeps is cheap.
            val frequency = 400.0 + (600.0 * progress)
            val duration = (100 - (50 * progress)).toInt()
            val amplitude = 0.5f + (0.4f * progress)

            val buffer = generateShortBeep(frequency, duration, amplitude)
            playBuffer(buffer)
        }
    }

    fun playMinuteBeep() {
        scope.launch {
            soundCache[SoundType.MINUTE]?.let { playBuffer(it) } ?: run {
                // Fallback if not cached yet
                val buffer = generateLongToneRich(880.0, 900, 0.65f)
                playBuffer(buffer)
            }
            vibrateShort(120)
        }
    }

    fun playFinalBeep() {
        scope.launch {
            val buffer = soundCache[SoundType.FINAL] ?: generateToneSequence(
                listOf(523.25, 659.25, 783.99, 523.25),
                listOf(200, 200, 200, 400),
                0.6f
            )
            playBuffer(buffer)
            vibratePattern(longArrayOf(0, 150, 100, 150))
        }
    }

    fun playWorkStartBeep() {
        scope.launch {
            val buffer = soundCache[SoundType.WORK_START] ?: generateLongToneRich(880.0, 900, 0.65f)
            playBuffer(buffer)
            vibrateShort(120)
        }
    }

    fun playRestStartBeep() {
        scope.launch {
            val buffer = soundCache[SoundType.REST_START] ?: generateToneSequence(
                listOf(659.25, 523.25, 392.0),
                listOf(150, 150, 200),
                0.5f
            )
            playBuffer(buffer)
        }
    }

    // --- Sound Generation Logic (Extracted from play methods) ---

    private fun generateShortBeep(frequency: Double, duration: Int, amplitude: Float): ShortArray {
        val numSamples = duration * sampleRate / 1000
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = when {
                i < numSamples * 0.1 -> i / (numSamples * 0.1)
                i > numSamples * 0.7 -> (numSamples - i) / (numSamples * 0.3)
                else -> 1.0
            }
            val sample = sin(2 * PI * frequency * t) * amplitude * envelope
            buffer[i] = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    private fun generateLongToneRich(baseFrequency: Double, duration: Int, amplitude: Float): ShortArray {
        val numSamples = duration * sampleRate / 1000
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = when {
                i < numSamples * 0.03 -> i / (numSamples * 0.03)
                i > numSamples * 0.85 -> (numSamples - i) / (numSamples * 0.15)
                else -> 1.0
            }
            val fundamental = sin(2 * PI * baseFrequency * t)
            val harmonic2 = sin(2 * PI * baseFrequency * 2 * t) * 0.3
            val harmonic3 = sin(2 * PI * baseFrequency * 3 * t) * 0.15
            val sample = (fundamental + harmonic2 + harmonic3) / 1.45 * amplitude * envelope
            buffer[i] = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    private fun generateToneSequence(frequencies: List<Double>, durations: List<Int>, amplitude: Float): ShortArray {
        val totalSamples = durations.sum() * sampleRate / 1000
        val buffer = ShortArray(totalSamples)

        var sampleIndex = 0
        for (i in frequencies.indices) {
            val freq = frequencies[i]
            val durationSamples = durations[i] * sampleRate / 1000

            for (j in 0 until durationSamples) {
                if (sampleIndex >= totalSamples) break
                val t = j.toDouble() / sampleRate
                val envelope = when {
                    j < durationSamples * 0.1 -> j / (durationSamples * 0.1)
                    j > durationSamples * 0.8 -> (durationSamples - j) / (durationSamples * 0.2)
                    else -> 1.0
                }
                val sample = sin(2 * PI * freq * t) * amplitude * envelope
                buffer[sampleIndex++] = (sample * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return buffer
    }

    private fun playBuffer(buffer: ShortArray) {
        try {
            // Request focus before playing
            requestAudioFocus()

            val bufferSize = buffer.size * 2
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()

            // Release focus after playback (with a small delay to ensure it finishes)
            scope.launch {
                val durationMs = (buffer.size.toLong() * 1000 / sampleRate) + 200
                delay(durationMs)
                audioTrack.release()
                abandonAudioFocus()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun vibrateShort(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun vibratePattern(pattern: LongArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        abandonAudioFocus()
        scope.cancel()
    }
}
