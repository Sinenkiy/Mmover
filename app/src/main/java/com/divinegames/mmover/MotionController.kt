package com.divinegames.mmover

internal class MotionController(
    private val scheduler: TaskScheduler,
    private val settings: () -> Settings,
    private val randomPause: (Int) -> Int = { maximum -> (0..maximum).random() },
    onStateChanged: (State) -> Unit
) {
    data class Settings(val activeSeconds: Int, val pauseSteps: Int, val stealth: Boolean)
    sealed interface State {
        data object Stopped : State
        data object Moving : State
        data class Waiting(val secondsRemaining: Int, val stealth: Boolean) : State
    }

    var state: State = State.Stopped
        private set
    private var listener: ((State) -> Unit)? = onStateChanged
    private var cancelPending: (() -> Unit)? = null
    private var generation = 0L
    private var closed = false

    fun start() {
        if (!closed && state == State.Stopped) move()
    }

    fun stop() {
        if (closed) return
        cancelTimer()
        publish(State.Stopped)
    }

    fun close() {
        if (closed) return
        stop()
        closed = true
        listener = null
    }

    private fun move() {
        val duration = settings().activeSeconds.coerceAtLeast(1) * 1000L
        arm(duration, ::pause)
        publish(State.Moving)
    }

    private fun pause() {
        val config = settings()
        // SharedPreferences stores pause_duration in five-second steps (0..180).
        val maximum = config.pauseSteps.coerceIn(0, 180) * 5
        val seconds = if (config.stealth && maximum > 0) {
            randomPause(maximum).coerceIn(0, maximum)
        } else maximum
        if (seconds == 0) {
            move()
        } else {
            tick(scheduler.nowMs + seconds * 1000L, config.stealth)
        }
    }

    private fun tick(deadline: Long, stealth: Boolean) {
        val remaining = deadline - scheduler.nowMs
        if (remaining <= 0) {
            move()
            return
        }
        arm(minOf(1000L, remaining)) { tick(deadline, stealth) }
        publish(State.Waiting(((remaining + 999) / 1000).toInt(), stealth))
    }

    private fun arm(delayMs: Long, action: () -> Unit) {
        cancelTimer()
        val token = generation
        cancelPending = scheduler.schedule(delayMs) {
            if (!closed && token == generation) {
                cancelPending = null
                action()
            }
        }
    }

    private fun cancelTimer() {
        generation++
        cancelPending?.invoke()
        cancelPending = null
    }

    private fun publish(value: State) {
        state = value
        listener?.invoke(value)
    }
}
