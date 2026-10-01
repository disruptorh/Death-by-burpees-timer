package com.timer.minimal

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.button.MaterialButton

class DeathBurpeesActivity : AppCompatActivity() {

    private lateinit var viewModel: TimerViewModel

    private lateinit var inputMinutes: EditText
    private lateinit var timerDisplay: TextView
    private lateinit var burpeeCounter: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnStart: MaterialButton
    private lateinit var btnStop: MaterialButton

    private val colorStart by lazy { ContextCompat.getColor(this, R.color.burpee_gradient_start) }
    private val colorEnd by lazy { ContextCompat.getColor(this, R.color.burpee_gradient_end) }
    private val colorWarning by lazy { ContextCompat.getColor(this, R.color.timer_warning) }

    private var hasLoadedInitialValue = false

    private var timerService: TimerService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TimerService.TimerBinder
            timerService = binder.getService()
            serviceBound = true
            observeServiceState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            timerService = null
            serviceBound = false
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission result handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_death_burpees)

        initViews()
        initViewModel()
        requestNotificationPermission()
    }

    override fun onStart() {
        super.onStart()
        // Keep the screen on only while this Activity is visible, so the
        // service can keep running in the background with the screen off.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ensureServiceBound()
    }

    override fun onStop() {
        super.onStop()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        releaseServiceBinding()
    }

    private fun ensureServiceBound() {
        if (serviceBound) return
        bindService(Intent(this, TimerService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun releaseServiceBinding() {
        if (!serviceBound) return
        unbindService(serviceConnection)
        serviceBound = false
        timerService = null
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun initViews() {
        inputMinutes = findViewById(R.id.inputMinutes)
        timerDisplay = findViewById(R.id.timerDisplay)
        burpeeCounter = findViewById(R.id.burpeeCounter)
        progressBar = findViewById(R.id.progressBar)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)

        btnStart.setOnClickListener { onStartClicked() }
        btnStop.setOnClickListener { onStopClicked() }

        inputMinutes.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: ""
                if (text.isNotEmpty()) {
                    val minutes = text.toIntOrNull()
                    if (minutes != null && minutes in TimerEngine.MIN_MINUTES..TimerEngine.MAX_MINUTES) {
                        viewModel.setInputMinutes(minutes)
                    }
                }
            }
        })
    }

    private fun initViewModel() {
        viewModel = ViewModelProvider(this)[TimerViewModel::class.java]
        viewModel.setTimerMode(TimerMode.DEATH_BURPEES)

        // A preset chosen on the mode selector becomes the starting duration.
        intent.getStringExtra(ModeSelectionActivity.EXTRA_PRESET_ID)?.let { presetId ->
            PresetManager(this).findById(presetId)?.let(viewModel::applyPreset)
        }

        viewModel.inputMinutes.observe(this) { minutes ->
            if (inputMinutes.text.isNullOrEmpty() && !hasLoadedInitialValue) {
                inputMinutes.setText(minutes.toString())
                hasLoadedInitialValue = true
            }
        }
    }

    private fun observeServiceState() {
        val service = timerService ?: return

        service.timeRemainingMs.observe(this) { timeMs ->
            // Skip when stopped so the burpee count is not shown for a finished run.
            if (service.timerState.value == TimerState.IDLE) return@observe

            timerDisplay.text = viewModel.formatTime(timeMs)
            timerDisplay.contentDescription = getString(R.string.timer_remaining, timerDisplay.text)

            val totalMs = service.totalTimeMs.value ?: 0L
            val phase = service.currentPhase.value ?: TimerPhase.WORK

            if (phase == TimerPhase.PREPARE) {
                burpeeCounter.setText(R.string.burpees_placeholder)
                applyColorToProgress(colorWarning)
                updateProgress(timeMs, totalMs)
                return@observe
            }

            val elapsedMs = (totalMs - timeMs).coerceAtLeast(0L)
            val burpeesToDo = (elapsedMs / 60_000L).toInt() + 1
            burpeeCounter.text = burpeesToDo.toString()
            burpeeCounter.contentDescription = getString(R.string.burpees_to_do, burpeesToDo)

            val totalProgress = if (totalMs > 0) elapsedMs.toFloat() / totalMs.toFloat() else 0f
            val color = ColorUtils.blendARGB(colorStart, colorEnd, totalProgress.coerceIn(0f, 1f))
            applyColorToProgress(color)
            burpeeCounter.setTextColor(color)
            updateProgress(timeMs, totalMs)
        }

        service.timerState.observe(this) { state ->
            when (state) {
                TimerState.IDLE -> {
                    btnStart.setIconResource(R.drawable.ic_play)
                    btnStart.contentDescription = getString(R.string.cd_start_timer)
                    inputMinutes.isEnabled = true
                    applyColorToProgress(colorStart)
                    burpeeCounter.setTextColor(colorStart)
                    burpeeCounter.text = "0"
                }
                TimerState.RUNNING -> {
                    btnStart.setIconResource(R.drawable.ic_pause)
                    btnStart.contentDescription = getString(R.string.btn_pause)
                    inputMinutes.isEnabled = false
                }
                TimerState.PAUSED -> {
                    btnStart.setIconResource(R.drawable.ic_play)
                    btnStart.contentDescription = getString(R.string.btn_resume)
                    inputMinutes.isEnabled = false
                }
                null -> Unit
            }
        }
    }

    private fun updateProgress(timeMs: Long, totalMs: Long) {
        if (totalMs <= 0) return
        progressBar.progress = ((timeMs.toFloat() / totalMs.toFloat()) * 100).toInt()
    }

    private fun applyColorToProgress(color: Int) {
        val drawable = progressBar.progressDrawable ?: return
        val filter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        if (drawable is LayerDrawable) {
            drawable.getDrawable(1)?.colorFilter = filter
        } else {
            drawable.colorFilter = filter
        }
    }

    private fun onStartClicked() {
        when (timerService?.timerState?.value ?: TimerState.IDLE) {
            TimerState.IDLE -> {
                val intent = Intent(this, TimerService::class.java).apply {
                    action = TimerService.ACTION_START
                    putExtra(TimerService.EXTRA_MODE, TimerMode.DEATH_BURPEES.name)
                    putExtra(
                        TimerService.EXTRA_INPUT_MINUTES,
                        viewModel.inputMinutes.value ?: TimerEngine.MIN_MINUTES
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                ensureServiceBound()
            }
            TimerState.PAUSED -> startService(
                Intent(this, TimerService::class.java).setAction(TimerService.ACTION_RESUME)
            )
            TimerState.RUNNING -> startService(
                Intent(this, TimerService::class.java).setAction(TimerService.ACTION_PAUSE)
            )
        }
    }

    private fun onStopClicked() {
        startService(
            Intent(this, TimerService::class.java).setAction(TimerService.ACTION_STOP)
        )
        burpeeCounter.text = "0"
        applyColorToProgress(colorStart)
        burpeeCounter.setTextColor(colorStart)
    }
}
