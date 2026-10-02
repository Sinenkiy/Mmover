package com.divinegames.mmover

/** Shared across screen instances; contains no Activity, View or SDK references. */
internal class BannerTimingState(
    var nextAllowedMs: Long = 0,
    var retryDelayMs: Long = MIN_INTERVAL_MS,
    private val persist: (Long, Long) -> Unit = { _, _ -> }
) {
    fun save() = persist(nextAllowedMs, retryDelayMs)

    companion object {
        const val MIN_INTERVAL_MS = 60_000L

        fun restoreDeadline(saved: Long?, savedBoot: Int, currentBoot: Int, nowMs: Long): Long = when {
            saved == null -> 0L
            currentBoot >= 0 && currentBoot == savedBoot -> saved
            else -> nowMs + MIN_INTERVAL_MS
        }
    }
}

/** One queue per slot for initial loads, refreshes and retries. Main-thread only. */
internal class BannerRefreshController(
    private val scheduler: TaskScheduler,
    private val timing: BannerTimingState,
    private val sdkOwnsRefresh: Boolean,
    private val canLoad: () -> Boolean,
    private val load: () -> Unit
) {
    private var active = false
    private var closed = false
    private var inFlight = false
    private var requested = false
    private var generation = 0
    private var cancelPending: (() -> Unit)? = null

    fun resume() {
        if (closed) return
        active = true
        schedule()
    }

    fun pause() {
        active = false
        cancel()
    }

    fun close() {
        pause()
        closed = true
    }

    fun loaded() {
        if (closed) return
        inFlight = false
        timing.retryDelayMs = BannerTimingState.MIN_INTERVAL_MS
        deferFromNow(BannerTimingState.MIN_INTERVAL_MS)
        schedule()
    }

    fun impression() {
        if (closed) return
        deferFromNow(BannerTimingState.MIN_INTERVAL_MS)
        schedule()
    }

    fun failed() {
        if (closed) return
        inFlight = false
        deferFromNow(timing.retryDelayMs)
        timing.retryDelayMs = (timing.retryDelayMs * 2).coerceAtMost(300_000L)
        timing.save()
        schedule()
    }

    private fun deferFromNow(delayMs: Long) {
        timing.nextAllowedMs = maxOf(timing.nextAllowedMs, scheduler.nowMs + delayMs)
        timing.save()
    }

    private fun schedule() {
        cancel()
        if (!active || closed || inFlight || (sdkOwnsRefresh && requested)) return
        val token = generation
        cancelPending = scheduler.schedule((timing.nextAllowedMs - scheduler.nowMs).coerceAtLeast(0)) task@{
            if (closed || !active || token != generation) return@task
            cancelPending = null
            if (scheduler.nowMs < timing.nextAllowedMs) {
                schedule() // Another screen/callback may have extended the shared deadline.
            } else if (canLoad()) {
                requested = true
                inFlight = true
                deferFromNow(BannerTimingState.MIN_INTERVAL_MS)
                load()
            }
        }
    }

    private fun cancel() {
        generation++
        cancelPending?.invoke()
        cancelPending = null
    }
}
