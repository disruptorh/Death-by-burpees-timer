package com.timer.minimal

import kotlin.math.max

/**
 * Pure timer state machine. No Android dependencies, so it is fully unit-testable.
 *
 * Time is supplied by the caller as a monotonic reading (SystemClock.elapsedRealtime()),
 * never as a wall clock. The engine never decrements an internal counter: it compares
 * the current reading against an absolute deadline, so it cannot drift if ticks are
 * late, coalesced, or dropped entirely.
 *
 * The engine is the single source of truth. TimerService only translates the emitted
 * [TimerEvent]s into sound, vibration and notification updates.
 */
class TimerEngine {

    /** Absolute configuration for one session. Validated by [TimerConfig.isValid]. */
    data class Config(
        val mode: TimerMode = TimerMode.ROUTINE,
        val workDurationSec: Int = 60,
        val restDurationSec: Int = 180,
        val totalSets: Int = 1,
        val inputMinutes: Int = 10
    )

    /**
     * Events the UI/service must react to. Returned in order from [onTick] so that
     * sound and vibration stay in sync with the phase transitions.
     */
    sealed interface TimerEvent {
        /** 5-second pre-start countdown. [secondsRemaining] goes 5, 4, 3, 2, 1. */
        data class PrepareTick(val secondsRemaining: Int) : TimerEvent

        /** Countdown finished; the real session is about to start. */
        data object PrepareFinished : TimerEvent

        /** A WORK phase started (also used to open the Death by Burpees session). */
        data object WorkStarted : TimerEvent

        /** A REST phase started. */
        data object RestStarted : TimerEvent

        /** Routine mode: seconds left before the current phase ends. 1..10. */
        data class Warning(val secondsToPhaseEnd: Int) : TimerEvent

        /** Death by Burpees: a new minute boundary. [burpeesForThisMinute] starts at 1. */
        data class MinuteMark(val burpeesForThisMinute: Int) : TimerEvent

        /** The whole session is over. */
        data object Finished : TimerEvent
    }

    var state: TimerState = TimerState.IDLE
        private set

    var phase: TimerPhase = TimerPhase.PREPARE
        private set

    var currentSet: Int = 1
        private set

    /** Remaining time of the current phase, in milliseconds. */
    var timeRemainingMs: Long = 0L
        private set

    /** Total length of the current phase, in milliseconds. */
    var totalTimeMs: Long = 0L
        private set

    /** Elapsed time since the session started, excluding the prepare countdown. */
    var elapsedSessionMs: Long = 0L
        private set

    private var config: Config = Config()
    private var phaseDeadlineMs: Long = 0L
    private var sessionStartMs: Long = 0L
    private var lastSecondEmitted: Int = -1
    private var phaseJustStarted = true

    /**
     * Loads a configuration and resets to IDLE. Does not start the countdown.
     */
    fun configure(newConfig: Config) {
        require(newConfig.isValid()) { "Invalid timer configuration: $newConfig" }
        config = newConfig
        resetToIdle()
    }

    /**
     * Begins the prepare countdown. [nowMs] must come from a monotonic clock.
     */
    fun start(nowMs: Long) {
        config = config.normalized()
        state = TimerState.RUNNING
        phase = TimerPhase.PREPARE
        currentSet = 1
        elapsedSessionMs = 0L
        totalTimeMs = PREPARE_DURATION_MS
        timeRemainingMs = PREPARE_DURATION_MS
        lastSecondEmitted = -1
        phaseJustStarted = true
        phaseDeadlineMs = nowMs + PREPARE_DURATION_MS
    }

    /**
     * Freezes the countdown. The remaining time is preserved so [resume] continues
     * exactly where it stopped. Returns false if the engine is not running.
     */
    fun pause(nowMs: Long): Boolean {
        if (state != TimerState.RUNNING) return false
        timeRemainingMs = max(0L, phaseDeadlineMs - nowMs)
        state = TimerState.PAUSED
        return true
    }

    /**
     * Continues a paused countdown. [nowMs] is the new monotonic reading, so the
     * deadline is recomputed and no time is lost or gained by the pause itself.
     */
    fun resume(nowMs: Long): Boolean {
        if (state != TimerState.PAUSED) return false
        state = TimerState.RUNNING
        phaseDeadlineMs = nowMs + timeRemainingMs
        return true
    }

    /** Stops the session and returns the engine to IDLE. */
    fun stop() {
        resetToIdle()
    }

    /**
     * Advances the state machine to [nowMs] and returns the events produced by
     * that step. Safe to call at any frequency: missed ticks are simply caught up.
     */
    fun onTick(nowMs: Long): List<TimerEvent> {
        if (state != TimerState.RUNNING) return emptyList()

        val events = mutableListOf<TimerEvent>()
        // A single tick can cross more than one boundary when the process was
        // frozen, so keep draining until the engine is no longer running.
        while (state == TimerState.RUNNING) {
            val remaining = phaseDeadlineMs - nowMs
            if (remaining > 0) {
                timeRemainingMs = remaining
                collectSecondEvents(remaining, events)
                break
            }
            // Cross the deadline, anchoring the next phase to the boundary we just
            // passed rather than to nowMs. If the process was frozen, the session
            // still runs on its original schedule and terminates on time.
            val crossed = phaseDeadlineMs
            events += advancePhase(crossed)
        }
        return events
    }

    private fun collectSecondEvents(remainingMs: Long, events: MutableList<TimerEvent>) {
        val second = ceilToSecond(remainingMs)
        if (second == lastSecondEmitted) return
        lastSecondEmitted = second

        when (phase) {
            TimerPhase.PREPARE ->
                if (second in 1..PREPARE_DURATION_SEC) {
                    events += TimerEvent.PrepareTick(second)
                }
            TimerPhase.WORK -> when (config.mode) {
                TimerMode.ROUTINE ->
                    if (second in 1..WARNING_SECONDS) {
                        events += TimerEvent.Warning(second)
                    }
                TimerMode.DEATH_BURPEES -> collectDeathBurpeesEvents(second, events)            }
            TimerPhase.REST ->
                if (second in 1..WARNING_SECONDS) {
                    events += TimerEvent.Warning(second)
                }
        }
    }

    private fun collectDeathBurpeesEvents(remainingSec: Int, events: MutableList<TimerEvent>) {
        val totalSec = totalTimeMs / 1000L
        val elapsedSec = totalSec - remainingSec
        val secondIntoMinute = elapsedSec % 60L

        // Minute boundary. Skipped while elapsedSec <= 0 so the first beep happens
        // one full minute in, not at the instant the session opens.
        if (secondIntoMinute == 0L && elapsedSec > 0L) {
            events += TimerEvent.MinuteMark((elapsedSec / 60L).toInt() + 1)
        }
        // Ten second warning before each minute.
        if (secondIntoMinute in 50L..59L) {
            events += TimerEvent.Warning((60L - secondIntoMinute).toInt())
        }
    }

    /**
     * Moves past the current phase deadline. Returns the events describing the
     * phase that just began.
     */
    private fun advancePhase(boundaryMs: Long): TimerEvent {
        when (phase) {
            TimerPhase.PREPARE -> {
                sessionStartMs = boundaryMs
                elapsedSessionMs = 0L
                return beginFirstPhase(boundaryMs)
            }
            TimerPhase.WORK -> {
                when (config.mode) {
                    TimerMode.DEATH_BURPEES -> {
                        state = TimerState.IDLE
                        timeRemainingMs = 0L
                        return TimerEvent.Finished
                    }
                    TimerMode.ROUTINE -> {
                        return if (currentSet >= config.totalSets) {
                            state = TimerState.IDLE
                            timeRemainingMs = 0L
                            TimerEvent.Finished
                        } else {
                            beginRestOrNextSet(boundaryMs)
                        }
                    }
                }
            }
            TimerPhase.REST -> {
                currentSet += 1
                return beginPhase(TimerPhase.WORK, config.workDurationSec, boundaryMs)
            }
        }
    }

    private fun beginFirstPhase(boundaryMs: Long): TimerEvent {
        phase = TimerPhase.WORK
        return when (config.mode) {
            TimerMode.DEATH_BURPEES -> {
                val durationMs = config.inputMinutes * 60_000L
                startPhaseClock(durationMs, boundaryMs)
                TimerEvent.WorkStarted
            }
            TimerMode.ROUTINE -> beginPhase(TimerPhase.WORK, config.workDurationSec, boundaryMs)
        }
    }

    private fun beginRestOrNextSet(boundaryMs: Long): TimerEvent {
        return if (config.restDurationSec > 0) {
            beginPhase(TimerPhase.REST, config.restDurationSec, boundaryMs)
        } else {
            currentSet += 1
            beginPhase(TimerPhase.WORK, config.workDurationSec, boundaryMs)
        }
    }

    private fun beginPhase(newPhase: TimerPhase, durationSec: Int, boundaryMs: Long): TimerEvent {
        phase = newPhase
        startPhaseClock(durationSec * 1000L, boundaryMs)
        return if (newPhase == TimerPhase.REST) TimerEvent.RestStarted else TimerEvent.WorkStarted
    }

    private fun startPhaseClock(durationMs: Long, boundaryMs: Long) {
        totalTimeMs = durationMs
        timeRemainingMs = durationMs
        lastSecondEmitted = -1
        phaseJustStarted = true
        phaseDeadlineMs = boundaryMs + durationMs
    }

    private fun resetToIdle() {
        state = TimerState.IDLE
        phase = TimerPhase.PREPARE
        currentSet = 1
        timeRemainingMs = 0L
        totalTimeMs = 0L
        elapsedSessionMs = 0L
        lastSecondEmitted = -1
        phaseJustStarted = true
        phaseDeadlineMs = 0L
        sessionStartMs = 0L
    }

    private fun Config.normalized(): Config = copy(
        workDurationSec = workDurationSec.coerceIn(MIN_WORK_SEC, MAX_PHASE_SEC),
        restDurationSec = restDurationSec.coerceIn(0, MAX_PHASE_SEC),
        totalSets = totalSets.coerceIn(MIN_SETS, MAX_SETS),
        inputMinutes = inputMinutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
    )

    companion object {
        const val PREPARE_DURATION_MS = 5_000L
        const val PREPARE_DURATION_SEC = 5
        const val WARNING_SECONDS = 10
        const val MIN_WORK_SEC = 5
        const val MAX_PHASE_SEC = 3600
        const val MIN_SETS = 1
        const val MAX_SETS = 99
        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 999

        /**
         * Rounds a remaining-time reading up to whole seconds. CountDownTimer-style
         * tickers report 0 for the final second and 5000 for a full 5s phase, so
         * "seconds left" must be ceil() of ms, not a plain division.
         */
        fun ceilToSecond(ms: Long): Int = ((ms + 999L) / 1000L).toInt()
    }
}

/** Validation shared by the engine, the ViewModel and the UI inputs. */
fun TimerEngine.Config.isValid(): Boolean = when (mode) {
    TimerMode.ROUTINE ->
        workDurationSec in TimerEngine.MIN_WORK_SEC..TimerEngine.MAX_PHASE_SEC &&
            restDurationSec in 0..TimerEngine.MAX_PHASE_SEC &&
            totalSets in TimerEngine.MIN_SETS..TimerEngine.MAX_SETS
    TimerMode.DEATH_BURPEES ->
        inputMinutes in TimerEngine.MIN_MINUTES..TimerEngine.MAX_MINUTES
}
