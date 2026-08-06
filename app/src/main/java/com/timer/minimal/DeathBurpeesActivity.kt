package com.timer.minimal

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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

    // Colores para gradiente
    private val colorStart = Color.parseColor("#00BCD4") // Cyan/Azul
    private val colorEnd = Color.parseColor("#FF5252")   // Rojo

    private var hasLoadedInitialValue = false

    private var timerService: TimerService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TimerService.TimerBinder
            timerService = binder.getService()
            serviceBound = true
            viewModel.bindService(binder.getService())
            observeServiceState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            timerService = null
            serviceBound = false
            viewModel.unbindService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_death_burpees)

        initViews()
        initViewModel()
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onStart() {
        super.onStart()
        // Bind to existing service if running
        val intent = Intent(this, TimerService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
            viewModel.unbindService()
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

        // Guardar minutos cuando cambian - permitir vacío
        inputMinutes.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: ""
                if (text.isNotEmpty()) {
                    val minutes = text.toIntOrNull()
                    if (minutes != null && minutes in 1..999) {
                        viewModel.setInputMinutes(minutes)
                    }
                }
            }
        })
    }

    private fun initViewModel() {
        viewModel = ViewModelProvider(this)[TimerViewModel::class.java]
        viewModel.setTimerMode(TimerMode.DEATH_BURPEES)

        // Cargar último valor guardado solo si el input está vacío
        viewModel.inputMinutes.observe(this) { minutes ->
            if (inputMinutes.text.isNullOrEmpty() && !hasLoadedInitialValue) {
                inputMinutes.setText(minutes.toString())
                hasLoadedInitialValue = true
            }
        }
    }

    /**
     * Observe timer state from the bound service.
     */
    private fun observeServiceState() {
        val service = timerService ?: return

        service.timeRemainingMs.observe(this) { timeMs ->
            // Skip calculation when timer is stopped — avoids showing wrong burpee count
            val state = service.timerState.value
            if (state == TimerState.IDLE) return@observe

            timerDisplay.text = viewModel.formatTime(timeMs)

            val phase = service.currentPhase.value ?: TimerPhase.WORK

            if (phase == TimerPhase.PREPARE) {
                // During prepare, just show the countdown, no burpee color logic
                burpeeCounter.text = "—"
                val prepareColor = android.graphics.Color.parseColor("#FFB300") // Amber
                burpeeCounter.setTextColor(prepareColor)
                applyColorToProgress(prepareColor)

                val totalMs = service.totalTimeMs.value ?: 1L
                if (totalMs > 0) {
                    val progress = ((timeMs.toFloat() / totalMs) * 100).toInt()
                    progressBar.progress = progress
                }
                return@observe
            }

            val totalMs = service.totalTimeMs.value ?: 1L
            val elapsedMs = totalMs - timeMs

            // Calcular burpees a hacer = minuto actual + 1
            val currentMinute = (elapsedMs / 60000).toInt()
            val burpeesToDo = currentMinute + 1
            burpeeCounter.text = burpeesToDo.toString()

            // Calcular progreso TOTAL del ciclo para color (0.0 a 1.0)
            val totalProgress = if (totalMs > 0) elapsedMs.toFloat() / totalMs.toFloat() else 0f

            // Aplicar color gradiente basado en progreso total
            val color = ColorUtils.blendARGB(colorStart, colorEnd, totalProgress)
            applyColorToProgress(color)
            burpeeCounter.setTextColor(color)

            // Actualizar progreso circular
            if (totalMs > 0) {
                val progress = ((timeMs.toFloat() / totalMs) * 100).toInt()
                progressBar.progress = progress
            }
        }

        service.timerState.observe(this) { state ->
            when (state) {
                TimerState.IDLE -> {
                    btnStart.setIconResource(R.drawable.ic_play)
                    inputMinutes.isEnabled = true
                    applyColorToProgress(colorStart)
                    burpeeCounter.setTextColor(colorStart)
                    burpeeCounter.text = "0"
                }
                TimerState.RUNNING -> {
                    btnStart.setIconResource(R.drawable.ic_pause)
                    inputMinutes.isEnabled = false
                }
                TimerState.PAUSED -> {
                    btnStart.setIconResource(R.drawable.ic_play)
                    inputMinutes.isEnabled = false
                }
                null -> { /* no-op */ }
            }
        }
    }

    private fun applyColorToProgress(color: Int) {
        try {
            val drawable = progressBar.progressDrawable
            if (drawable is LayerDrawable) {
                drawable.getDrawable(1)?.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            } else {
                drawable?.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun onStartClicked() {
        val currentState = timerService?.timerState?.value ?: TimerState.IDLE

        when (currentState) {
            TimerState.IDLE -> {
                // Start fresh timer via foreground service
                val minutes = viewModel.inputMinutes.value ?: 10
                val intent = Intent(this, TimerService::class.java).apply {
                    action = TimerService.ACTION_START
                    putExtra(TimerService.EXTRA_MODE, TimerMode.DEATH_BURPEES.name)
                    putExtra(TimerService.EXTRA_INPUT_MINUTES, minutes)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                // Bind to observe state
                bindService(Intent(this, TimerService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
            }
            TimerState.PAUSED -> {
                val intent = Intent(this, TimerService::class.java).apply {
                    action = TimerService.ACTION_RESUME
                }
                startService(intent)
            }
            TimerState.RUNNING -> {
                val intent = Intent(this, TimerService::class.java).apply {
                    action = TimerService.ACTION_PAUSE
                }
                startService(intent)
            }
        }
    }

    private fun onStopClicked() {
        val intent = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_STOP
        }
        startService(intent)
        burpeeCounter.text = "0"
        applyColorToProgress(colorStart)
        burpeeCounter.setTextColor(colorStart)
    }
}
