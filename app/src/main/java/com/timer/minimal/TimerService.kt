package com.timer.minimal

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * TimerService: foreground service that owns all timer logic.
 * Runs independently of Activity lifecycle — survives screen-off, app background, etc.
 */
class TimerService : Service() {

    companion object {
        const val CHANNEL_ID = "timer_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.timer.minimal.ACTION_START"
        const val ACTION_PAUSE = "com.timer.minimal.ACTION_PAUSE"
        const val ACTION_RESUME = "com.timer.minimal.ACTION_RESUME"
        const val ACTION_STOP = "com.timer.minimal.ACTION_STOP"

        // Extras for configuring the timer via Intent
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_WORK_DURATION = "extra_work_duration"
        const val EXTRA_REST_DURATION = "extra_rest_duration"
        const val EXTRA_TOTAL_SETS = "extra_total_sets"
        const val EXTRA_INPUT_MINUTES = "extra_input_minutes"

        private const val WARMUP_DURATION_MS = 5000L
    }

    private val binder = TimerBinder()
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var soundManager: SoundManager

    // --- Timer state (observable by Activities) ---
    private val _timeRemainingMs = MutableLiveData(0L)
    val timeRemainingMs: LiveData<Long> = _timeRemainingMs

    private val _totalTimeMs = MutableLiveData(0L)
    val totalTimeMs: LiveData<Long> = _totalTimeMs

    private val _timerState = MutableLiveData(TimerState.IDLE)
    val timerState: LiveData<TimerState> = _timerState

    private val _currentPhase = MutableLiveData(TimerPhase.WORK)
    val currentPhase: LiveData<TimerPhase> = _currentPhase

    private val _currentSet = MutableLiveData(1)
    val currentSet: LiveData<Int> = _currentSet

    // --- Configuration ---
    private var timerMode: TimerMode = TimerMode.ROUTINE
    private var workDurationSec: Int = 60
    private var restDurationSec: Int = 180
    private var totalSets: Int = 1
    private var inputMinutes: Int = 10

    private var countDownTimer: CountDownTimer? = null
    private var pausedTimeMs: Long = 0L

    inner class TimerBinder : Binder() {
        fun getService(): TimerService = this@TimerService
    }

    override fun onCreate() {
        super.onCreate()
        soundManager = SoundManager(this)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // Read configuration from intent
                timerMode = TimerMode.valueOf(
                    intent.getStringExtra(EXTRA_MODE) ?: TimerMode.ROUTINE.name
                )
                workDurationSec = intent.getIntExtra(EXTRA_WORK_DURATION, 60)
                restDurationSec = intent.getIntExtra(EXTRA_REST_DURATION, 180)
                totalSets = intent.getIntExtra(EXTRA_TOTAL_SETS, 1)
                inputMinutes = intent.getIntExtra(EXTRA_INPUT_MINUTES, 10)

                // Start foreground immediately
                startForegroundWithNotification(getString(R.string.notification_status_running))
                acquireWakeLock()

                // Start the timer
                startTimerFromBeginning()
            }
            ACTION_PAUSE -> {
                pauseTimer()
            }
            ACTION_RESUME -> {
                resumeTimer()
            }
            ACTION_STOP -> {
                stopTimer()
                stopSelf()
            }
        }
        return START_STICKY
    }

    // ========================
    // Timer Logic
    // ========================

    private fun startTimerFromBeginning() {
        startPrepareCountdown()
    }

    /**
     * 5-second preparation countdown before the actual timer starts.
     * Plays ascending ticks each second, then transitions to the real timer.
     */
    private fun startPrepareCountdown() {
        _currentPhase.postValue(TimerPhase.PREPARE)
        _totalTimeMs.postValue(WARMUP_DURATION_MS)
        _timerState.postValue(TimerState.RUNNING)

        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(WARMUP_DURATION_MS, 50) {
            private var lastSecond = -1

            override fun onTick(millisUntilFinished: Long) {
                _timeRemainingMs.postValue(millisUntilFinished)

                val currentSecond = (millisUntilFinished / 1000).toInt()
                if (currentSecond != lastSecond) {
                    lastSecond = currentSecond
                    soundManager.playPrepareTick(currentSecond)
                    updateLiveNotification("%d".format(currentSecond + 1))
                }
            }

            override fun onFinish() {
                _timeRemainingMs.postValue(0L)
                startActualTimer()
            }
        }.start()
    }

    /**
     * Starts the real timer after the prepare countdown finishes.
     */
    private fun startActualTimer() {
        when (timerMode) {
            TimerMode.ROUTINE -> {
                _currentSet.postValue(1)
                _currentPhase.postValue(TimerPhase.WORK)
                soundManager.playWorkStartBeep()
                startPhaseTimer(TimerPhase.WORK)
            }
            TimerMode.DEATH_BURPEES -> {
                val durationMs = inputMinutes * 60 * 1000L
                _totalTimeMs.postValue(durationMs)
                _currentPhase.postValue(TimerPhase.WORK)
                soundManager.playWorkStartBeep()
                startCountdown(durationMs)
            }
        }
    }

    private fun startPhaseTimer(phase: TimerPhase) {
        _currentPhase.postValue(phase)

        val durationMs = when (phase) {
            TimerPhase.WORK -> workDurationSec * 1000L
            TimerPhase.REST -> restDurationSec * 1000L
            TimerPhase.PREPARE -> return // PREPARE is handled by startPrepareCountdown
        }

        _totalTimeMs.postValue(durationMs)

        if (phase == TimerPhase.WORK) {
            soundManager.playWorkStartBeep()
        } else {
            soundManager.playRestStartBeep()
        }

        startCountdown(durationMs)
    }

    private fun startCountdown(durationMs: Long) {
        _timerState.postValue(TimerState.RUNNING)

        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(durationMs, 50) {
            private var lastSecond = -1

            override fun onTick(millisUntilFinished: Long) {
                _timeRemainingMs.postValue(millisUntilFinished)

                val currentSecond = (millisUntilFinished / 1000).toInt()

                if (currentSecond != lastSecond) {
                    lastSecond = currentSecond
                    handleSecondTick(currentSecond)
                    // Update notification with live countdown
                    updateLiveNotification(formatTime(millisUntilFinished))
                }
            }

            override fun onFinish() {
                _timeRemainingMs.postValue(0L)
                onPhaseComplete()
            }
        }.start()
    }

    private fun handleSecondTick(secondsRemaining: Int) {
        when (timerMode) {
            TimerMode.ROUTINE -> {
                // Progressive warning beeps 10 to 1 seconds before phase end
                if (secondsRemaining in 1..10) {
                    soundManager.playWarningBeep(secondsRemaining)
                }
            }
            TimerMode.DEATH_BURPEES -> {
                val totalSeconds = (_totalTimeMs.value ?: 0L) / 1000
                val elapsedSeconds = totalSeconds - secondsRemaining
                val secondsIntoCurrentMinute = elapsedSeconds % 60

                // Beep at the START of each minute
                if (secondsIntoCurrentMinute == 0L && elapsedSeconds > 0) {
                    soundManager.playMinuteBeep()
                }

                // Warning 10 seconds before each minute (seconds 50-59 into each minute)
                if (secondsIntoCurrentMinute in 50..59) {
                    val secondsToMinute = (60 - secondsIntoCurrentMinute).toInt()
                    soundManager.playWarningBeep(secondsToMinute)
                }
            }
        }
    }

    private fun onPhaseComplete() {
        when (timerMode) {
            TimerMode.DEATH_BURPEES -> {
                // Death by Burpees: single long timer, done
                soundManager.playFinalBeep()
                _timerState.postValue(TimerState.IDLE)
                stopSelf()
                return
            }
            TimerMode.ROUTINE -> {
                val phase = _currentPhase.value ?: TimerPhase.WORK
                val set = _currentSet.value ?: 1

                when (phase) {
                    TimerPhase.WORK -> {
                        if (set >= totalSets) {
                            // Last set completed
                            soundManager.playFinalBeep()
                            _timerState.postValue(TimerState.IDLE)
                            stopSelf()
                        } else if (restDurationSec > 0) {
                            startPhaseTimer(TimerPhase.REST)
                        } else {
                            _currentSet.postValue(set + 1)
                            startPhaseTimer(TimerPhase.WORK)
                        }
                    }
                    TimerPhase.REST -> {
                        _currentSet.postValue(set + 1)
                        startPhaseTimer(TimerPhase.WORK)
                    }
                    TimerPhase.PREPARE -> { /* handled by prepare countdown */ }
                }
            }
        }
    }

    fun pauseTimer() {
        if (_timerState.value == TimerState.RUNNING) {
            countDownTimer?.cancel()
            pausedTimeMs = _timeRemainingMs.value ?: 0L
            _timerState.postValue(TimerState.PAUSED)
        }
    }

    fun resumeTimer() {
        if (_timerState.value == TimerState.PAUSED && pausedTimeMs > 0) {
            startCountdown(pausedTimeMs)
        }
    }

    fun stopTimer() {
        countDownTimer?.cancel()
        _timerState.postValue(TimerState.IDLE)
        _timeRemainingMs.postValue(0L)
        _currentSet.postValue(1)
        _currentPhase.postValue(TimerPhase.WORK)
        pausedTimeMs = 0L
    }

    // ========================
    // Foreground Service / Notification
    // ========================

    private fun getNotificationIcon(): Int {
        return when (timerMode) {
            TimerMode.ROUTINE -> R.drawable.ic_dumbbell
            TimerMode.DEATH_BURPEES -> R.drawable.ic_skull
        }
    }

    private fun getNotificationTitle(): String {
        val phase = _currentPhase.value ?: TimerPhase.WORK
        if (phase == TimerPhase.PREPARE) {
            return getString(R.string.phase_prepare)
        }
        return when (timerMode) {
            TimerMode.ROUTINE -> {
                val set = _currentSet.value ?: 1
                when (phase) {
                    TimerPhase.WORK -> getString(R.string.phase_work) + " " + getString(R.string.set_counter, set, totalSets)
                    TimerPhase.REST -> getString(R.string.phase_rest) + " " + getString(R.string.set_counter, set, totalSets)
                    else -> getString(R.string.phase_prepare)
                }
            }
            TimerMode.DEATH_BURPEES -> {
                val totalMs = _totalTimeMs.value ?: 0L
                val remainingMs = _timeRemainingMs.value ?: 0L
                val elapsedMs = totalMs - remainingMs
                val currentMinute = (elapsedMs / 60000).toInt()
                val burpees = currentMinute + 1
                getString(R.string.burpees_label) + ": $burpees"
            }
        }
    }

    private fun startForegroundWithNotification(text: String) {
        val notification = buildNotification(text, silent = false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateLiveNotification(timeText: String) {
        val notification = buildNotification(timeText, silent = true)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(timeRemaining: String, silent: Boolean = false): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, ModeSelectionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TimerService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getNotificationTitle())
            .setContentText(timeRemaining)
            .setSmallIcon(getNotificationIcon())
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.btn_stop), stopIntent)
            .setOngoing(true)
            .setSilent(silent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun formatTime(timeMs: Long): String {
        val totalSeconds = (timeMs / 1000).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    // ========================
    // WakeLock
    // ========================

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MinimalTimer::TimerWakeLock"
        ).apply {
            acquire(60 * 60 * 1000L) // 1 hour max
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        soundManager.release()
        releaseWakeLock()
    }
}
