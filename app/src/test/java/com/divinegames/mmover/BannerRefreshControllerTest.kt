package com.divinegames.mmover

import org.junit.Assert.*
import org.junit.Test

class BannerRefreshControllerTest {
    private val clock = Clock()
    private val timing = BannerTimingState()
    private val requests = mutableListOf<Long>()
    private var allowed = true
    private fun controller(sdk: Boolean = false, state: BannerTimingState = timing) = BannerRefreshController(
        clock, state, sdk, { allowed }, { requests.add(clock.nowMs) }
    )

    @Test fun initialLoadIsImmediateButNeverDuplicatedWhileInFlight() {
        val banner = controller()
        repeat(5) { banner.resume() }
        clock.advance(0)
        repeat(5) { banner.resume() }
        clock.advance(120000)
        assertEquals(listOf(0L), requests)
    }

    @Test fun slowSuccessfulLoadGetsFullMinuteBeforeReplacement() {
        val banner = controller()
        banner.resume(); clock.advance(10000)
        banner.loaded()
        clock.advance(59999)
        assertEquals(listOf(0L), requests)
        clock.advance(1)
        assertEquals(listOf(0L, 70000L), requests)
    }

    @Test fun impressionExtendsDeadlineAndLeavesOnlyOneQueuedRefresh() {
        val banner = controller()
        banner.resume(); clock.advance(1000); banner.loaded()
        clock.advance(5000); banner.impression()
        clock.advance(59999)
        assertEquals(1, requests.size)
        clock.advance(1)
        assertEquals(listOf(0L, 66000L), requests)
    }

    @Test fun failuresBackOffAndResumeCannotBypassRetryDeadline() {
        val banner = controller()
        banner.resume(); clock.advance(0)
        for (delay in listOf(60000L, 120000L, 240000L, 300000L, 300000L)) {
            banner.failed()
            val count = requests.size
            repeat(4) { banner.resume() }
            clock.advance(delay - 1)
            assertEquals(count, requests.size)
            clock.advance(1)
            assertEquals(count + 1, requests.size)
        }
    }

    @Test fun successfulLoadResetsFailureBackoff() {
        val banner = controller()
        banner.resume(); clock.advance(0)
        repeat(3) { banner.failed(); clock.advance(300000) }
        banner.loaded()
        clock.advance(60000)
        banner.failed()
        val count = requests.size
        clock.advance(60000)
        assertEquals(count + 1, requests.size)
    }

    @Test fun backgroundCancelsWorkAndReturnUsesRemainingTime() {
        val banner = controller()
        banner.resume(); clock.advance(0); banner.loaded()
        clock.advance(20000); banner.pause()
        clock.advance(10000); banner.resume()
        clock.advance(29999)
        assertEquals(1, requests.size)
        clock.advance(1)
        assertEquals(listOf(0L, 60000L), requests)
    }

    @Test fun longBackgroundDoesNotLoadAndReturnDoesNotWaitAnotherMinute() {
        val banner = controller()
        banner.resume(); clock.advance(0); banner.loaded(); banner.pause()
        clock.advance(180000)
        assertEquals(1, requests.size)
        banner.resume(); clock.advance(0)
        assertEquals(listOf(0L, 180000L), requests)
    }

    @Test fun recreationSharesDeadlineAndDiscardsOldCallbacks() {
        val old = controller()
        old.resume(); clock.advance(0); old.loaded()
        val stale = clock.tasks.last().action
        clock.advance(10000); old.close()
        val replacement = controller()
        replacement.resume()
        old.loaded(); old.failed(); old.resume(); stale()
        clock.advance(49999)
        assertEquals(1, requests.size)
        clock.advance(1)
        assertEquals(listOf(0L, 60000L), requests)
    }

    @Test fun callbackWhilePausedPersistsDeadlineWithoutStartingTimer() {
        val banner = controller()
        banner.resume(); clock.advance(0); banner.pause()
        clock.advance(10000); banner.loaded()
        clock.advance(70000)
        assertEquals(1, requests.size)
        banner.resume(); clock.advance(0)
        assertEquals(listOf(0L, 80000L), requests)
    }

    @Test fun consentGatePreventsRequests() {
        allowed = false
        val banner = controller()
        banner.resume(); clock.advance(180000)
        assertTrue(requests.isEmpty())
        allowed = true
        banner.resume(); clock.advance(0)
        assertEquals(listOf(180000L), requests)
    }

    @Test fun topAndBottomSlotsHaveIndependentDeadlines() {
        val top = controller()
        val bottom = controller(state = BannerTimingState())
        top.resume(); clock.advance(0); top.failed()
        bottom.resume(); clock.advance(0)
        assertEquals(listOf(0L, 0L), requests)
    }

    @Test fun sdkOwnedRefreshNeverAddsAppRefreshOrRetry() {
        val banner = controller(sdk = true)
        banner.resume(); clock.advance(0); banner.loaded()
        clock.advance(60000); banner.impression(); banner.failed()
        banner.pause(); banner.resume(); clock.advance(600000)
        assertEquals(listOf(0L), requests)
    }

    @Test fun savedDeadlineAndBackoffSurviveNewControllerAndState() {
        var savedNext = 0L
        var savedRetry = 0L
        val state = BannerTimingState(persist = { next, retry -> savedNext = next; savedRetry = retry })
        val banner = controller(state = state)
        banner.resume(); clock.advance(1000); banner.failed(); banner.close()
        assertEquals(61000L, savedNext)
        assertEquals(120000L, savedRetry)
        val restored = controller(state = BannerTimingState(savedNext, savedRetry))
        restored.resume(); clock.advance(59999)
        assertEquals(1, requests.size)
        clock.advance(1)
        assertEquals(listOf(0L, 61000L), requests)
    }

    @Test fun processRestartRetainsDeadlineButRebootUsesConservativeMinute() {
        assertEquals(85000L, BannerTimingState.restoreDeadline(85000L, 4, 4, 45000L))
        assertEquals(62000L, BannerTimingState.restoreDeadline(85000L, 4, 5, 2000L))
        assertEquals(62000L, BannerTimingState.restoreDeadline(85000L, -1, -1, 2000L))
        assertEquals(0L, BannerTimingState.restoreDeadline(null, -2, 5, 2000L))
    }

    private class Clock : TaskScheduler {
        override var nowMs = 0L
        data class Task(val at: Long, val action: () -> Unit, var cancelled: Boolean = false)
        val tasks = mutableListOf<Task>()
        override fun schedule(delayMs: Long, action: () -> Unit): () -> Unit {
            val task = Task(nowMs + delayMs, action)
            tasks.add(task)
            return { task.cancelled = true }
        }
        fun advance(ms: Long) {
            val target = nowMs + ms
            while (true) {
                val task = tasks.filter { !it.cancelled && it.at <= target }.minByOrNull { it.at } ?: break
                tasks.remove(task)
                nowMs = task.at
                task.action()
            }
            nowMs = target
        }
    }
}
