package com.divinegames.mmover

internal class DiagnosticThrottle(private val intervalMs: Long = 60_000L) {
    private var lastRunMs: Long? = null

    @Synchronized
    fun tryAcquire(nowMs: Long): Boolean {
        val previous = lastRunMs
        if (previous != null && nowMs >= previous && nowMs - previous < intervalMs) return false
        lastRunMs = nowMs
        return true
    }
}
