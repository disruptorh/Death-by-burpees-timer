package com.timer.minimal

import com.timer.minimal.TimerEngine.Companion.PREPARE_DURATION_MS
import com.timer.minimal.TimerEngine.Config
import com.timer.minimal.TimerEngine.TimerEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerEngineTest {

    private fun engine(config: Config) = TimerEngine().apply { configure(config) }

    private val routine = Config(
        mode = TimerMode.ROUTINE,
        workDurationSec = 60,
        restDurationSec = 180,
        totalSets = 8
    )

    // --- Prepare phase ---

    @Test
    fun `prepare emits descending ticks then work starts`() {
        val e = engine(routine)
        e.start(nowMs = 0L)

        val ticks = mutableListOf<TimerEvent>()
        // 50ms cadence like the real service
        var now = 0L
        while (e.state == TimerState.RUNNING && e.phase == TimerPhase.PREPARE) {
            ticks += e.onTick(now)
            now += 50L
        }

        val prepareTicks = ticks.filterIsInstance<TimerEvent.PrepareTick>()
        assertEquals(listOf(5, 4, 3, 2, 1), prepareTicks.map { it.secondsRemaining })
        assertEquals(1, ticks.count { it is TimerEvent.WorkStarted })
        assertEquals(TimerPhase.WORK, e.phase)
    }

    @Test
    fun `prepare lasts exactly five seconds`() {
        val e = engine(routine)
        e.start(0L)
        e.onTick(PREPARE_DURATION_MS - 1)
        assertEquals(TimerPhase.PREPARE, e.phase)
        e.onTick(PREPARE_DURATION_MS)
        assertEquals(TimerPhase.WORK, e.phase)
    }

    // --- Routine transitions ---

    @Test
    fun `work warns on the final ten seconds`() {
        val e = engine(routine.copy(workDurationSec = 60))
        e.start(0L)
        e.onTick(PREPARE_DURATION_MS)

        val warnings = mutableListOf<Int>()
        var now = PREPARE_DURATION_MS
        // Drive through the whole 60s work phase; warnings fire on seconds 50..60.
        while (now <= PREPARE_DURATION_MS + 60_000) {
            e.onTick(now).filterIsInstance<TimerEvent.Warning>()
                .forEach { warnings += it.secondsToPhaseEnd }
            now += 50L
        }
        // Counted down, so the urgency ramps from 10 down to 1.
        assertEquals((10 downTo 1).toList(), warnings)
    }

    @Test
    fun `work is followed by rest and set increments after rest`() {
        val e = engine(routine.copy(workDurationSec = 10, restDurationSec = 20, totalSets = 3))
        e.start(0L)
        e.onTick(PREPARE_DURATION_MS)
        assertEquals(1, e.currentSet)
        assertEquals(TimerPhase.WORK, e.phase)

        e.onTick(PREPARE_DURATION_MS + 10_000)
        assertEquals(TimerPhase.REST, e.phase)
        assertEquals(1, e.currentSet)

        e.onTick(PREPARE_DURATION_MS + 30_000)
        assertEquals(TimerPhase.WORK, e.phase)
        assertEquals(2, e.currentSet)
    }

    @Test
    fun `zero rest skips straight to the next set`() {
        val e = engine(routine.copy(workDurationSec = 10, restDurationSec = 0, totalSets = 3))
        e.start(0L)
        e.onTick(PREPARE_DURATION_MS)
        e.onTick(PREPARE_DURATION_MS + 10_000)

        assertEquals(TimerPhase.WORK, e.phase)
        assertEquals(2, e.currentSet)
    }

    @Test
    fun `routine finishes after the configured number of sets`() {
        val e = engine(routine.copy(workDurationSec = 10, restDurationSec = 0, totalSets = 2))
        e.start(0L)

        var now = PREPARE_DURATION_MS
        val events = mutableListOf<TimerEvent>()
        // Drive well past the expected end; the engine must stop exactly once.
        while (now <= PREPARE_DURATION_MS + 60_000) {
            events += e.onTick(now)
            now += 50L
            if (e.state == TimerState.IDLE) break
        }

        assertEquals(TimerState.IDLE, e.state)
        assertEquals(1, events.count { it is TimerEvent.Finished })
    }

    // --- Pause / resume ---

    @Test
    fun `pause freezes the remaining time and resume continues from it`() {
        val e = engine(routine)
        e.start(0L)
        e.onTick(PREPARE_DURATION_MS)

        val pauseAt = PREPARE_DURATION_MS + 25_000L
        e.onTick(pauseAt)
        assertTrue(e.pause(pauseAt))
        val remainingAtPause = e.timeRemainingMs
        assertEquals(35_000L, remainingAtPause)

        // Time passes while paused; the engine must not move.
        e.onTick(pauseAt + 120_000L)
        assertEquals(remainingAtPause, e.timeRemainingMs)
        assertEquals(TimerState.PAUSED, e.state)

        assertTrue(e.resume(pauseAt + 120_000L))
        assertEquals(TimerState.RUNNING, e.state)
        e.onTick(pauseAt + 125_000L)
        assertEquals(30_000L, e.timeRemainingMs)
    }

    @Test
    fun `pause and resume are ignored when the engine is not in that state`() {
        val e = engine(routine)
        e.start(0L)
        e.onTick(PREPARE_DURATION_MS + 1_000L)

        // Not paused yet: resume must be a no-op.
        assertFalse(e.resume(PREPARE_DURATION_MS + 1_000L))
        assertEquals(TimerState.RUNNING, e.state)

        assertTrue(e.pause(PREPARE_DURATION_MS + 1_000L))
        assertEquals(TimerState.PAUSED, e.state)

        // Already paused: a second pause must be a no-op.
        assertFalse(e.pause(PREPARE_DURATION_MS + 1_000L))
        assertEquals(TimerState.PAUSED, e.state)

        // Idling after stop: neither transition is allowed.
        e.stop()
        assertFalse(e.pause(0L))
        assertFalse(e.resume(0L))
    }

    // --- Death by Burpees ---

    @Test
    fun `death by burpees beeps on minute boundaries announcing the new count`() {
        val e = engine(Config(mode = TimerMode.DEATH_BURPEES, inputMinutes = 5))
        e.start(0L)
        e.onTick(PREPARE_DURATION_MS)

        val marks = mutableListOf<Int>()
        var now = PREPARE_DURATION_MS
        while (e.state == TimerState.RUNNING) {
            e.onTick(now).filterIsInstance<TimerEvent.MinuteMark>()
                .forEach { marks += it.burpeesForThisMinute }
            now += 50L
        }
        // The first burpee is announced by WorkStarted, so the minute marks carry
        // the count for the minute that is beginning: 2, 3, 4, 5.
        assertEquals(listOf(2, 3, 4, 5), marks)
    }

    @Test
    fun `death by burpees does not beep on minute zero`() {
        val e = engine(Config(mode = TimerMode.DEATH_BURPEES, inputMinutes = 3))
        e.start(0L)
        // First ten seconds must produce no minute mark.
        var now = PREPARE_DURATION_MS
        val marks = mutableListOf<Int>()
        while (now < PREPARE_DURATION_MS + 10_000) {
            e.onTick(now).filterIsInstance<TimerEvent.MinuteMark>().forEach { marks += it.burpeesForThisMinute }
            now += 50L
        }
        assertTrue(marks.isEmpty())
    }

    @Test
    fun `death by burpees warns for ten seconds before each minute`() {
        val e = engine(Config(mode = TimerMode.DEATH_BURPEES, inputMinutes = 2))
        e.start(0L)
        e.onTick(PREPARE_DURATION_MS)

        val warnings = mutableListOf<Int>()
        var now = PREPARE_DURATION_MS
        while (now <= PREPARE_DURATION_MS + 60_000) {
            e.onTick(now).filterIsInstance<TimerEvent.Warning>()
                .forEach { warnings += it.secondsToPhaseEnd }
            now += 50L
        }
        // Counted down, so the urgency ramps from 10 down to 1.
        assertEquals((10 downTo 1).toList(), warnings)
    }

    @Test
    fun `death by burpees minute mark matches the displayed burpee count`() {
        val e = engine(Config(mode = TimerMode.DEATH_BURPEES, inputMinutes = 3))
        e.start(0L)
        e.onTick(PREPARE_DURATION_MS)

        // At the two-minute boundary (elapsed 120s), burpee count = 3.
        val atTwoMinutes = PREPARE_DURATION_MS + 120_000L
        val marks = e.onTick(atTwoMinutes).filterIsInstance<TimerEvent.MinuteMark>()
        assertEquals(listOf(3), marks.map { it.burpeesForThisMinute })
    }

    @Test
    fun `death by burpees finishes once for the whole session`() {
        val e = engine(Config(mode = TimerMode.DEATH_BURPEES, inputMinutes = 1))
        e.start(0L)
        var now = PREPARE_DURATION_MS
        var finished = 0
        while (now <= PREPARE_DURATION_MS + 120_000) {
            e.onTick(now).filterIsInstance<TimerEvent.Finished>().let { finished += it.size }
            now += 50L
            if (e.state == TimerState.IDLE) break
        }
        assertEquals(TimerState.IDLE, e.state)
        assertEquals(1, finished)
    }

    // --- Drift resistance ---

    @Test
    fun `a single huge tick catches up across every missed boundary`() {
        val e = engine(routine.copy(workDurationSec = 10, restDurationSec = 10, totalSets = 2))
        e.start(0L)

        // Timeline: prepare ends at 5s, work at 15s, rest at 25s, final work at 35s.
        // One tick straight to 40s must cross all four boundaries.
        val events = e.onTick(PREPARE_DURATION_MS + 35_000L)
        assertTrue(events.any { it is TimerEvent.WorkStarted })
        assertTrue(events.any { it is TimerEvent.RestStarted })
        assertTrue(events.any { it is TimerEvent.Finished })
        assertEquals(TimerState.IDLE, e.state)
    }

    @Test
    fun `no events are produced while paused or idle`() {
        val e = engine(routine)
        e.start(0L)
        e.onTick(1_000L)
        assertTrue(e.pause(1_000L))
        assertTrue(e.onTick(999_999L).isEmpty())
        e.stop()
        assertTrue(e.onTick(999_999L).isEmpty())
    }

    @Test
    fun `stop returns the engine to a clean idle state`() {
        val e = engine(routine)
        e.start(0L)
        e.onTick(PREPARE_DURATION_MS + 5_000L)
        e.stop()

        assertEquals(TimerState.IDLE, e.state)
        assertEquals(0L, e.timeRemainingMs)
        assertEquals(1, e.currentSet)
    }

    // --- Config validation ---

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a work duration below the minimum`() {
        engine(routine.copy(workDurationSec = 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects more sets than the maximum`() {
        engine(routine.copy(totalSets = 100))
    }

    @Test
    fun `accepts the documented boundary values`() {
        engine(
            routine.copy(
                workDurationSec = TimerEngine.MIN_WORK_SEC,
                restDurationSec = 0,
                totalSets = TimerEngine.MAX_SETS
            )
        )
        engine(Config(mode = TimerMode.DEATH_BURPEES, inputMinutes = TimerEngine.MAX_MINUTES))
    }

    @Test
    fun `minute boundaries longer than an hour are supported`() {
        val e = engine(Config(mode = TimerMode.DEATH_BURPEES, inputMinutes = 999))
        e.start(0L)
        e.onTick(PREPARE_DURATION_MS)
        val twoHoursIn = PREPARE_DURATION_MS + 120 * 60_000L
        val marks = e.onTick(twoHoursIn).filterIsInstance<TimerEvent.MinuteMark>()
        // Elapsed 120 min -> 120/60 + 1 == 121 burpees.
        assertEquals(listOf(121), marks.map { it.burpeesForThisMinute })
    }

    // --- Helper ---

    @Test
    fun `ceilToSecond rounds partial seconds up`() {
        assertEquals(0, TimerEngine.ceilToSecond(0L))
        assertEquals(1, TimerEngine.ceilToSecond(1L))
        assertEquals(1, TimerEngine.ceilToSecond(1000L))
        assertEquals(2, TimerEngine.ceilToSecond(1001L))
        assertEquals(5, TimerEngine.ceilToSecond(5000L))
    }
}
