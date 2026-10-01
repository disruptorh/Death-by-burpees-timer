package com.timer.minimal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * Foreground service that owns the timer session.
 *
 * All timing decisions live in [TimerEngine]; this class only feeds it a monotonic
 * clock reading and turns the resulting [TimerEngine.TimerEvent]s into sound,
 * vibration and notification updates. It survives screen-off, app backgrounding and
 * short process freezes because the engine derives the remaining time from an
 * absolute deadline instead of decrementing a counter.
 */
class TimerService : Service() {

    companion object {
        const val CHANNEL_ID = "timer_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.timer.minimal.ACTION_START"
        const val ACTION_PAUSE = "com.timer.minimal.ACTION_PAUSE"
        const val ACTION_RESUME = "com.timer.minimal.ACTION_RESUME"
        const val ACTION_STOP = "com.timer.minimal.ACTION_STOP"

        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_WORK_DURATION = "extra_work_duration"
        const val EXTRA_REST_DURATION = "extra_rest_duration"
        const val EXTRA_TOTAL_SETS = "extra_total_sets"
        const val EXTRA_INPUT_MINUTES = "extra_input_minutes"

        /** How often the engine is polled. Fine-grained so the UI stays smooth. */
        private const val TICK_INTERVAL_MS = 50L

        /**
         * The WakeLock is taken without a timeout and released explicitly in
         * onDestroy. A timed lock silently expires mid-session, which is what used
         * to let the CPU sleep and the clock drift on long workouts.
         */
        private const val WAKE_LOCK_TAG = "MinimalTimer::TimerWakeLock"

        private const val PREFS_START_EPOCH = "session_start_epoch_ms"

        /**
         * Safety net only. The lock is released in onDestroy well before this;
         * the bound exists so a leaked reference cannot hold the CPU awake forever.
         */
        private const val MAX_WAKE_LOCK_MS = 6 * 60 * 60 * 1000L
    }

    private val binder = TimerBinder()
    private val handler = Handler(Looper.getMainLooper())
    private val engine = TimerEngine()
    private val historyManager by lazy { HistoryManager(this) }

    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var soundManager: SoundManager

    /** Wall-clock start of the session, recorded on START for the history log. */
    private var sessionStartEpochMs = 0L
    private var sessionConfig: TimerEngine.Config = TimerEngine.Config()

    // --- Timer state (observable by Activities) ---
    private val _timeRemainingMs = MutableLiveData(0L)
    val timeRemainingMs: LiveData<Long> = _timeRemainingMs

    private val _totalTimeMs = MutableLiveData(0L)
    val totalTimeMs: LiveData<Long> = _totalTimeMs

    private val _timerState = MutableLiveData(TimerState.IDLE)
    val timerState: LiveData<TimerState> = _timerState

    private val _currentPhase = MutableLiveData(TimerPhase.PREPARE)
    val currentPhase: LiveData<TimerPhase> = _currentPhase

    private val _currentSet = MutableLiveData(1)
    val currentSet: LiveData<Int> = _currentSet

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (engine.state != TimerState.RUNNING) return
            val now = SystemClock.elapsedRealtime()
            engine.onTick(now).forEach(::handleEvent)
            publishState()
            if (engine.state == TimerState.RUNNING) {
                handler.postDelayed(this, TICK_INTERVAL_MS)
            }
        }
    }

    inner class TimerBinder : Binder() {
        fun getService(): TimerService = this@TimerService
    }

    override fun onCreate() {
        super.onCreate()
        soundManager = SoundManager(this)
        createNotificationChannel()
        sessionStartEpochMs = getSharedPreferences(PREFS_START_EPOCH, MODE_PRIVATE)
            .getLong(PREFS_START_EPOCH, 0L)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startFromIntent(intent)
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_STOP -> stopTimer(recordHistory = false)
        }
        // The engine holds no persisted state, so a system restart after the
        // process is killed could not resume the session: do not ask for one.
        return START_NOT_STICKY
    }

    private fun startFromIntent(intent: Intent) {
        val mode = runCatching {
            TimerMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: TimerMode.ROUTINE.name)
        }.getOrDefault(TimerMode.ROUTINE)

        val config = when (mode) {
            TimerMode.ROUTINE -> TimerEngine.Config(
                mode = mode,
                workDurationSec = intent.getIntExtra(EXTRA_WORK_DURATION, 60),
                restDurationSec = intent.getIntExtra(EXTRA_REST_DURATION, 180),
                totalSets = intent.getIntExtra(EXTRA_TOTAL_SETS, 1)
            )
            TimerMode.DEATH_BURPEES -> TimerEngine.Config(
                mode = mode,
                inputMinutes = intent.getIntExtra(EXTRA_INPUT_MINUTES, 10)
            )
        }
        if (!config.isValid()) {
            stopTimer(recordHistory = false)
            return
        }

        sessionConfig = config
        engine.configure(config)
        sessionStartEpochMs = System.currentTimeMillis()
        getSharedPreferences(PREFS_START_EPOCH, MODE_PRIVATE)
            .edit()
            .putLong(PREFS_START_EPOCH, sessionStartEpochMs)
            .apply()

        startForegroundWithNotification(getString(R.string.notification_status_running))
        acquireWakeLock()
        engine.start(SystemClock.elapsedRealtime())
        publishState()
        handler.post(tickRunnable)
    }

    // ========================
    // Control
    // ========================

    fun pauseTimer() {
        if (!engine.pause(SystemClock.elapsedRealtime())) return
        handler.removeCallbacks(tickRunnable)
        publishState()
        updateLiveNotification()
    }

    fun resumeTimer() {
        if (engine.state != TimerState.PAUSED) return
        startForegroundWithNotification(getString(R.string.notification_status_paused))
        engine.resume(SystemClock.elapsedRealtime())
        publishState()
        handler.removeCallbacks(tickRunnable)
        handler.post(tickRunnable)
    }

    fun stopTimer(recordHistory: Boolean = false) {
        val wasRunning = engine.state == TimerState.RUNNING
        val completed = recordHistory && wasRunning

        handler.removeCallbacks(tickRunnable)
        engine.stop()
        publishState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        if (completed) {
            recordSession()
        }
    }

    private fun recordSession() {
        val durationSec = ((sessionStartEpochMs.let { System.currentTimeMillis() - it }) / 1000L)
            .toInt()
            .coerceAtLeast(1)
        historyManager.record(
            SessionRecord(
                startedAtEpochMs = sessionStartEpochMs,
                mode = sessionConfig.mode,
                durationSec = durationSec
            )
        )
        sessionStartEpochMs = 0L
        getSharedPreferences(PREFS_START_EPOCH, MODE_PRIVATE)
            .edit()
            .putLong(PREFS_START_EPOCH, 0L)
            .apply()
    }

    // ========================
    // Event handling
    // ========================

    private fun handleEvent(event: TimerEngine.TimerEvent) {
        when (event) {
            is TimerEngine.TimerEvent.PrepareTick ->
                soundManager.playPrepareTick(event.secondsRemaining)

            is TimerEngine.TimerEvent.WorkStarted -> {
                soundManager.playWorkStartBeep()
                updateLiveNotification()
            }

            is TimerEngine.TimerEvent.RestStarted -> {
                soundManager.playRestStartBeep()
                updateLiveNotification()
            }

            is TimerEngine.TimerEvent.Warning ->
                soundManager.playWarningBeep(event.secondsToPhaseEnd)

            is TimerEngine.TimerEvent.MinuteMark ->
                soundManager.playMinuteBeep()

            is TimerEngine.TimerEvent.Finished -> onSessionFinished()

            is TimerEngine.TimerEvent.PrepareFinished -> Unit
        }
    }

    private fun onSessionFinished() {
        handler.removeCallbacks(tickRunnable)
        soundManager.playFinalBeep()
        _timeRemainingMs.postValue(0L)
        _timerState.postValue(TimerState.IDLE)
        recordSession()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun publishState() {
        _timeRemainingMs.postValue(engine.timeRemainingMs)
        _totalTimeMs.postValue(engine.totalTimeMs)
        _timerState.postValue(engine.state)
        _currentPhase.postValue(engine.phase)
        _currentSet.postValue(engine.currentSet)
    }

    // ========================
    // Foreground Service / Notification
    // ========================

    private fun getNotificationIcon(): Int = when (sessionConfig.mode) {
        TimerMode.ROUTINE -> R.drawable.ic_dumbbell
        TimerMode.DEATH_BURPEES -> R.drawable.ic_skull
    }

    private fun getNotificationTitle(): String = when (engine.phase) {
        TimerPhase.PREPARE -> getString(R.string.phase_prepare)
        else -> when (sessionConfig.mode) {
            TimerMode.ROUTINE -> getString(R.string.phase_name_and_set,
                getString(if (engine.phase == TimerPhase.WORK) R.string.phase_work else R.string.phase_rest),
                getString(R.string.set_counter, engine.currentSet, sessionConfig.totalSets)
            )
            TimerMode.DEATH_BURPEES ->
                getString(R.string.burpees_count, burpeesToDisplay())
        }
    }

    private fun burpeesToDisplay(): Int {
        if (engine.state == TimerState.IDLE) return 0
        if (engine.phase == TimerPhase.PREPARE) return 0
        val elapsedMs = engine.totalTimeMs - engine.timeRemainingMs
        return (elapsedMs / 60_000L).toInt() + 1
    }

    private fun startForegroundWithNotification(text: String) {
        val notification = buildNotification(text, showActions = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateLiveNotification() {
        val text = if (engine.state == TimerState.RUNNING) {
            formatTime(engine.timeRemainingMs)
        } else {
            getString(R.string.notification_status_paused)
        }
        val notification = buildNotification(text, showActions = true)
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }

    /**
     * Builds the ongoing notification. [showActions] is false on the very first
     * publication so the actions are not built before the service is foreground.
     */
    private fun buildNotification(text: String, showActions: Boolean): Notification {
        // Tapping the notification returns to the screen that is actually running,
        // not to the mode selector.
        val targetActivity = when (sessionConfig.mode) {
            TimerMode.ROUTINE -> MainActivity::class.java
            TimerMode.DEATH_BURPEES -> DeathBurpeesActivity::class.java
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, targetActivity)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getNotificationTitle())
            .setContentText(text)
            .setSmallIcon(getNotificationIcon())
            .setContentIntent(contentIntent)
            .setOngoing(engine.state != TimerState.IDLE)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (showActions) {
            builder.addAction(
                R.drawable.ic_pause,
                getString(R.string.btn_pause),
                servicePendingIntent(ACTION_PAUSE, 1)
            )
            builder.addAction(
                R.drawable.ic_play,
                getString(R.string.btn_resume),
                servicePendingIntent(ACTION_RESUME, 2)
            )
            builder.addAction(
                R.drawable.ic_stop,
                getString(R.string.btn_stop),
                servicePendingIntent(ACTION_STOP, 3)
            )
        }
        return builder.build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, TimerService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun formatTime(timeMs: Long): String {
        val totalSeconds = (timeMs / 1000).coerceAtLeast(0L)
        return String.format(java.util.Locale.getDefault(), "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
    }

    // ========================
    // WakeLock
    // ========================

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKE_LOCK_TAG
        ).apply {
            setReferenceCounted(false)
            // Held until onDestroy; no timeout, so long sessions cannot lose it.
            acquire(MAX_WAKE_LOCK_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        soundManager.release()
        releaseWakeLock()
        super.onDestroy()
    }
}
