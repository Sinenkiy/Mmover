package com.divinegames.mmover

/** All calls and scheduled actions must run on the same thread. No Android dependencies. */
internal interface TaskScheduler {
    val nowMs: Long
    // Queue the action, never invoke it inline. Return an idempotent cancellation.
    fun schedule(delayMs: Long, action: () -> Unit): () -> Unit
}

