package com.timer.minimal

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: TimerViewModel

    private lateinit var inputWorkDuration: EditText
    private lateinit var inputRestDuration: EditText
    private lateinit var inputTotalSets: EditText
    private lateinit var timerDisplay: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var phaseIndicator: TextView
    private lateinit var setCounter: TextView

    private lateinit var toggleWorkUnit: MaterialButtonToggleGroup
    private lateinit var toggleRestUnit: MaterialButtonToggleGroup

    private lateinit var btnStart: MaterialButton
    private lateinit var btnStop: Button
    private lateinit var btnReset: Button

    private var timerService: TimerService? = null
    private var serviceBound = false

    private val colorWarning by lazy { ContextCompat.getColor(this, R.color.timer_warning) }
    private val colorRest by lazy { ContextCompat.getColor(this, R.color.timer_rest) }

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
        setContentView(R.layout.activity_main)

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

    private fun initViews() {
        inputWorkDuration = findViewById(R.id.inputWorkDuration)
        inputRestDuration = findViewById(R.id.inputRestDuration)
        inputTotalSets = findViewById(R.id.inputTotalSets)

        toggleWorkUnit = findViewById(R.id.toggleWorkUnit)
        toggleRestUnit = findViewById(R.id.toggleRestUnit)

        timerDisplay = findViewById(R.id.timerDisplay)
        progressBar = findViewById(R.id.progressBar)
        phaseIndicator = findViewById(R.id.phaseIndicator)
        setCounter = findViewById(R.id.setCounter)

        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnReset = findViewById(R.id.btnReset)

        btnStart.setOnClickListener { onStartClicked() }
        btnStop.setOnClickListener { onStopClicked() }
        btnReset.setOnClickListener { onResetClicked() }

        setupInputs()
        setupToggles()
    }

    private fun setupInputs() {
        inputWorkDuration.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isSyncingFromViewModel) applyWorkInput()
            }
        })

        inputRestDuration.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isSyncingFromViewModel) applyRestInput()
            }
        })

        inputTotalSets.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isSyncingFromViewModel) return
                val sets = s?.toString()?.toIntOrNull()
                if (sets != null && sets in TimerEngine.MIN_SETS..TimerEngine.MAX_SETS) {
                    viewModel.setTotalSets(sets)
                }
            }
        })
    }

    /**
     * Unit toggles only change how the current number is *rendered*. The stored
     * value in seconds is left untouched, so switching sec/min never rescales the
     * number by 60 behind the user's back.
     */
    private fun setupToggles() {
        toggleWorkUnit.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked && !isSyncingFromViewModel) {
                renderWork(viewModel.workDuration.value ?: 60)
            }
        }
        toggleRestUnit.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked && !isSyncingFromViewModel) {
                renderRest(viewModel.restDuration.value ?: 180)
            }
        }
    }

    private fun applyWorkInput() {
        val value = inputWorkDuration.text?.toString()?.toIntOrNull() ?: return
        val multiplier = currentWorkMultiplier()
        val totalSeconds = value * multiplier
        if (totalSeconds in TimerEngine.MIN_WORK_SEC..TimerEngine.MAX_PHASE_SEC) {
            viewModel.setWorkDuration(totalSeconds)
        }
    }

    private fun applyRestInput() {
        val value = inputRestDuration.text?.toString()?.toIntOrNull() ?: return
        val multiplier = currentRestMultiplier()
        val totalSeconds = value * multiplier
        if (totalSeconds in 0..TimerEngine.MAX_PHASE_SEC) {
            viewModel.setRestDuration(totalSeconds)
        }
    }

    private fun currentWorkMultiplier(): Int =
        if (toggleWorkUnit.checkedButtonId == R.id.btnWorkMin) 60 else 1

    private fun currentRestMultiplier(): Int =
        if (toggleRestUnit.checkedButtonId == R.id.btnRestMin) 60 else 1

    private fun renderWork(seconds: Int) {
        isSyncingFromViewModel = true
        if (seconds >= 60 && seconds % 60 == 0) {
            toggleWorkUnit.check(R.id.btnWorkMin)
            inputWorkDuration.setText((seconds / 60).toString())
        } else {
            toggleWorkUnit.check(R.id.btnWorkSec)
            inputWorkDuration.setText(seconds.toString())
        }
        isSyncingFromViewModel = false
    }

    private fun renderRest(seconds: Int) {
        isSyncingFromViewModel = true
        if (seconds >= 60 && seconds % 60 == 0) {
            toggleRestUnit.check(R.id.btnRestMin)
            inputRestDuration.setText((seconds / 60).toString())
        } else {
            toggleRestUnit.check(R.id.btnRestSec)
            inputRestDuration.setText(seconds.toString())
        }
        isSyncingFromViewModel = false
    }

    private fun initViewModel() {
        viewModel = ViewModelProvider(this)[TimerViewModel::class.java]
        viewModel.setTimerMode(TimerMode.ROUTINE)

        // A preset chosen on the mode selector becomes the starting configuration.
        intent.getStringExtra(ModeSelectionActivity.EXTRA_PRESET_ID)?.let { presetId ->
            PresetManager(this).findById(presetId)?.let(viewModel::applyPreset)
        }

        viewModel.workDuration.observe(this) { seconds ->
            if (!inputWorkDuration.hasFocus()) renderWork(seconds)
        }
        viewModel.restDuration.observe(this) { seconds ->
            if (!inputRestDuration.hasFocus()) renderRest(seconds)
        }
        viewModel.totalSets.observe(this) { sets ->
            if (!inputTotalSets.hasFocus()) {
                isSyncingFromViewModel = true
                inputTotalSets.setText(sets.toString())
                isSyncingFromViewModel = false
            }
        }
    }

    private fun observeServiceState() {
        val service = timerService ?: return

        service.timeRemainingMs.observe(this) { timeMs ->
            timerDisplay.text = viewModel.formatTime(timeMs)
            timerDisplay.contentDescription = getString(R.string.timer_remaining, timerDisplay.text)

            val totalMs = service.totalTimeMs.value ?: 0L
            if (totalMs > 0) {
                progressBar.progress = ((timeMs.toFloat() / totalMs.toFloat()) * 100).toInt()
            }
        }

        service.timerState.observe(this) { state ->
            updateUI(state)
        }

        service.currentPhase.observe(this) { phase ->
            updatePhaseIndicator(phase)
        }

        service.currentSet.observe(this) { currentSet ->
            val total = viewModel.totalSets.value ?: 1
            setCounter.text = getString(R.string.set_counter, currentSet, total)
        }
    }

    private fun updatePhaseIndicator(phase: TimerPhase) {
        val colorRes = when (phase) {
            TimerPhase.PREPARE -> R.color.timer_warning
            TimerPhase.WORK -> R.color.accent_primary
            TimerPhase.REST -> R.color.timer_rest
        }
        val labelRes = when (phase) {
            TimerPhase.PREPARE -> R.string.phase_prepare
            TimerPhase.WORK -> R.string.phase_work
            TimerPhase.REST -> R.string.phase_rest
        }
        phaseIndicator.setText(labelRes)
        phaseIndicator.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    private fun updateUI(state: TimerState) {
        val inputsEnabled = state == TimerState.IDLE
        inputWorkDuration.isEnabled = inputsEnabled
        inputRestDuration.isEnabled = inputsEnabled
        inputTotalSets.isEnabled = inputsEnabled

        when (state) {
            TimerState.IDLE -> {
                btnStart.setIconResource(R.drawable.ic_play)
                btnStart.contentDescription = getString(R.string.cd_start_timer)
                btnStart.visibility = View.VISIBLE
                btnStop.visibility = View.GONE
                btnReset.visibility = View.GONE
                phaseIndicator.visibility = View.INVISIBLE
                setCounter.visibility = View.INVISIBLE
                progressBar.progress = 0
            }
            TimerState.RUNNING -> {
                btnStart.setIconResource(R.drawable.ic_pause)
                btnStart.contentDescription = getString(R.string.btn_pause)
                btnStart.visibility = View.VISIBLE
                btnStop.visibility = View.VISIBLE
                btnReset.visibility = View.GONE
                phaseIndicator.visibility = View.VISIBLE
                setCounter.visibility = View.VISIBLE
            }
            TimerState.PAUSED -> {
                btnStart.setIconResource(R.drawable.ic_play)
                btnStart.contentDescription = getString(R.string.btn_resume)
                btnStart.visibility = View.VISIBLE
                btnStop.visibility = View.VISIBLE
                btnReset.visibility = View.VISIBLE
                phaseIndicator.visibility = View.VISIBLE
                setCounter.visibility = View.VISIBLE
            }
        }
    }

    private fun onStartClicked() {
        when (timerService?.timerState?.value ?: TimerState.IDLE) {
            TimerState.IDLE -> {
                val intent = Intent(this, TimerService::class.java).apply {
                    action = TimerService.ACTION_START
                    putExtra(TimerService.EXTRA_MODE, TimerMode.ROUTINE.name)
                    putExtra(
                        TimerService.EXTRA_WORK_DURATION,
                        viewModel.workDuration.value ?: 60
                    )
                    putExtra(
                        TimerService.EXTRA_REST_DURATION,
                        viewModel.restDuration.value ?: 180
                    )
                    putExtra(
                        TimerService.EXTRA_TOTAL_SETS,
                        viewModel.totalSets.value ?: 1
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
    }

    private fun onResetClicked() {
        onStopClicked()
        progressBar.progress = 0
        timerDisplay.setText(R.string.timer_zero)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Guards the input fields against echoing their own programmatic updates. */
    private var isSyncingFromViewModel = false
}
