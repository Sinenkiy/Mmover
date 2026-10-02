package com.divinegames.mmover

import android.app.Activity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Observe a continuously animating screen on its main thread without waiting for
 * the message queue to become idle. ActivityScenario's off-thread onActivity
 * waits for idle, which a 60fps Choreographer loop need not provide on an emulator.
 * Each test still explicitly awaits the state/layout it actually needs.
 */
internal fun <A : Activity> ActivityScenario<A>.onFrameActivity(action: (A) -> Unit) {
    InstrumentationRegistry.getInstrumentation().runOnMainSync { onActivity { action(it) } }
}
