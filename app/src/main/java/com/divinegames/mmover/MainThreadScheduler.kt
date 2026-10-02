package com.divinegames.mmover

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

internal class MainThreadScheduler : TaskScheduler {
    private val handler = Handler(Looper.getMainLooper())
    override val nowMs: Long get() = SystemClock.elapsedRealtime()

    override fun schedule(delayMs: Long, action: () -> Unit): () -> Unit {
        val task = Runnable { action() }
        handler.postDelayed(task, delayMs)
        return { handler.removeCallbacks(task) }
    }
}
