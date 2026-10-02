package com.divinegames.mmover

import org.junit.Assert.*
import org.junit.Test

class DiagnosticThrottleTest {
    @Test fun rapidScreenChangesCannotRepeatExpensiveSnapshot() {
        val throttle = DiagnosticThrottle()
        assertTrue(throttle.tryAcquire(0))
        for (time in listOf(0L, 1000L, 59000L, 59999L)) assertFalse(throttle.tryAcquire(time))
        assertTrue(throttle.tryAcquire(60000))
        assertFalse(throttle.tryAcquire(60001))
    }

    @Test fun longGapAndClockResetPermitFreshSnapshot() {
        val throttle = DiagnosticThrottle()
        assertTrue(throttle.tryAcquire(100000))
        assertTrue(throttle.tryAcquire(300000))
        assertTrue(throttle.tryAcquire(0))
    }
}
