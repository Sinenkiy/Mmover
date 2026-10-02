package com.divinegames.mmover

import org.junit.Assert.*
import org.junit.Test

class MotionControllerTest {
    private val clock = FakeScheduler()
    private var settings = MotionController.Settings(3, 1, false)
    private val states = mutableListOf<MotionController.State>()
    private fun controller(random: (Int) -> Int = { it }) = MotionController(
        clock, { settings }, random, states::add
    )

    @Test fun movesWaitsFiveSecondsPerStepAndRepeats() {
        val controller = controller()
        controller.start()
        clock.advance(2999)
        assertEquals(MotionController.State.Moving, controller.state)
        clock.advance(1)
        assertEquals(MotionController.State.Waiting(5, false), controller.state)
        clock.advance(1000)
        assertEquals(MotionController.State.Waiting(4, false), controller.state)
        clock.advance(4000)
        assertEquals(MotionController.State.Moving, controller.state)
        assertEquals(1, clock.pending)
    }

    @Test fun stoppingMovementPreventsLaterPause() {
        val controller = controller()
        controller.start()
        controller.stop()
        clock.advance(100000)
        assertEquals(listOf(MotionController.State.Moving, MotionController.State.Stopped), states)
        assertEquals(0, clock.pending)
    }

    @Test fun stoppingWaitingPreventsAutomaticRestart() {
        val controller = controller()
        controller.start()
        clock.advance(3000)
        controller.stop()
        val count = states.size
        clock.advance(100000)
        assertEquals(MotionController.State.Stopped, controller.state)
        assertEquals(count, states.size)
        assertEquals(0, clock.pending)
    }

    @Test fun repeatedStartDoesNotResetTimerOrCreateAnotherCycle() {
        val controller = controller()
        controller.start()
        clock.advance(2000)
        repeat(10) { controller.start() }
        clock.advance(1000)
        assertEquals(MotionController.State.Waiting(5, false), controller.state)
        controller.start()
        assertEquals(1, clock.pending)
        assertEquals(2, states.size)
    }

    @Test fun zeroPauseKeepsMovingWithoutWaitingOrBusyLoop() {
        settings = settings.copy(pauseSteps = 0)
        val controller = controller()
        controller.start()
        clock.advance(9000)
        assertEquals(4, states.size)
        assertTrue(states.all { it == MotionController.State.Moving })
        assertEquals(1, clock.pending)
    }

    @Test fun staleCallbackCannotInterruptNewRun() {
        val controller = controller()
        controller.start()
        val oldCallback = clock.tasks.last().action
        controller.stop()
        controller.start()
        oldCallback() // Simulate an already queued callback despite cancellation.
        assertEquals(MotionController.State.Moving, controller.state)
        assertEquals(1, clock.pending)
        clock.advance(3000)
        assertEquals(MotionController.State.Waiting(5, false), controller.state)
    }

    @Test fun closeCancelsWaitingAndRejectsCallbacksAndRestarts() {
        val controller = controller()
        controller.start()
        clock.advance(3000)
        val oldCallback = clock.tasks.last().action
        controller.close()
        val count = states.size
        oldCallback()
        controller.start()
        controller.close()
        clock.advance(100000)
        assertEquals(MotionController.State.Stopped, controller.state)
        assertEquals(count, states.size)
        assertEquals(0, clock.pending)
    }

    @Test fun randomModeUsesPauseRangeButFixedActiveDuration() {
        settings = settings.copy(pauseSteps = 2, stealth = true)
        val controller = controller { maximum -> assertEquals(10, maximum); 2 }
        controller.start()
        clock.advance(2999)
        assertEquals(MotionController.State.Moving, controller.state)
        clock.advance(1)
        assertEquals(MotionController.State.Waiting(2, true), controller.state)
        clock.advance(2000)
        assertEquals(MotionController.State.Moving, controller.state)
    }

    @Test fun randomPauseMayBeZeroOrMaximum() {
        settings = settings.copy(stealth = true)
        var selected = 0
        val controller = controller { maximum -> if (selected++ == 0) 0 else maximum }
        controller.start()
        clock.advance(3000)
        assertEquals(MotionController.State.Moving, controller.state)
        clock.advance(3000)
        assertEquals(MotionController.State.Waiting(5, true), controller.state)
    }

    @Test fun nextPhaseReadsLatestSettingsIncludingExpiredReward() {
        settings = settings.copy(stealth = true)
        val controller = controller { error("Expired reward must not randomize pause") }
        controller.start()
        settings = MotionController.Settings(1, 2, false)
        clock.advance(3000)
        assertEquals(MotionController.State.Waiting(10, false), controller.state)
        clock.advance(10000)
        clock.advance(1000)
        assertEquals(MotionController.State.Waiting(10, false), controller.state)
    }

    @Test fun lateTickUsesDeadlineInsteadOfExtendingPause() {
        val controller = controller()
        controller.start()
        clock.advance(3000)
        clock.deliverNextLate(7500)
        assertEquals(MotionController.State.Waiting(1, false), controller.state)
        clock.advance(500)
        assertEquals(MotionController.State.Moving, controller.state)
    }

    @Test fun invalidDurationsAreBoundedAndMaximumPauseIsFifteenMinutes() {
        settings = MotionController.Settings(0, Int.MAX_VALUE, false)
        val controller = controller()
        controller.start()
        clock.advance(1000)
        assertEquals(MotionController.State.Waiting(900, false), controller.state)
        controller.stop()
        settings = settings.copy(pauseSteps = -1)
        controller.start()
        clock.advance(1000)
        assertEquals(MotionController.State.Moving, controller.state)
    }

    private class FakeScheduler : TaskScheduler {
        data class Task(val due: Long, val action: () -> Unit, var cancelled: Boolean = false)
        val tasks = mutableListOf<Task>()
        override var nowMs = 0L
        val pending get() = tasks.count { !it.cancelled }
        override fun schedule(delayMs: Long, action: () -> Unit): () -> Unit {
            val task = Task(nowMs + delayMs, action)
            tasks += task
            return { task.cancelled = true }
        }
        fun advance(ms: Long) {
            val target = nowMs + ms
            while (true) {
                val next = tasks.filter { !it.cancelled && it.due <= target }.minByOrNull { it.due } ?: break
                tasks.remove(next)
                nowMs = next.due
                next.action()
            }
            nowMs = target
        }
        fun deliverNextLate(time: Long) {
            val next = tasks.filter { !it.cancelled }.minBy { it.due }
            tasks.remove(next)
            nowMs = time
            next.action()
        }
    }
}
