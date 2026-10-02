package com.divinegames.mmover

/** Screen-owned observer; all calls and callbacks run on the same thread. */
internal class RewardAccessController(
    private val access: RewardAccess,
    private val scheduler: TaskScheduler,
    onChanged: (Boolean) -> Unit
) {
    private var listener: ((Boolean) -> Unit)? = onChanged
    private var cancelPending: (() -> Unit)? = null
    private var generation = 0
    private var visible = false
    private var closed = false

    fun isEnabled() = access.isEnabled()

    fun setEnabled(enabled: Boolean): Boolean {
        if (closed) return false
        val result = access.setEnabled(enabled)
        refresh()
        return result
    }

    fun grant() {
        if (closed) return
        access.grant()
        refresh()
    }

    fun onResume() {
        if (closed) return
        visible = true
        refresh()
    }

    fun onPause() {
        visible = false
        cancel()
    }

    fun close() {
        onPause()
        closed = true
        listener = null
    }

    private fun refresh() {
        cancel()
        val enabled = access.isEnabled()
        if (!visible || closed) return
        val token = generation
        cancelPending = scheduler.schedule(access.nextRefreshDelayMs()) {
            if (!closed && visible && token == generation) refresh()
        }
        listener?.invoke(enabled)
    }

    private fun cancel() {
        generation++
        cancelPending?.invoke()
        cancelPending = null
    }
}
