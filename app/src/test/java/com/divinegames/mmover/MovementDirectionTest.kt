package com.divinegames.mmover

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class MovementDirectionTest {
    @Test fun stripesReverseAfterFourSecondsAt500PixelsPerSecond() {
        val direction = MovementDirection(Random(1)).apply { reset(true) }
        val initial = direction.y
        repeat(39) { direction.travel(500f * 0.1f) }
        assertEquals(initial, direction.y)
        direction.travel(50f)
        assertEquals(-initial, direction.y)
        assertEquals(0f, direction.x)
        assertEquals(2000f, direction.remaining)
    }

    @Test fun turnsKeepUnitSpeedAndChangeByAtLeast45Degrees() {
        val direction = MovementDirection(Random(5)).apply { reset(false) }
        repeat(100) {
            val x = direction.x
            val y = direction.y
            direction.travel(2000f)
            assertEquals(1f, direction.x * direction.x + direction.y * direction.y, 0.00001f)
            assertTrue(x * direction.x + y * direction.y <= 0.70711f)
        }
    }

    @Test fun partialFrameAndRestartKeepTheDistanceContract() {
        val direction = MovementDirection(Random(2)).apply { reset(true) }
        direction.travel(1990f)
        var distance = 30f
        var dy = 0f
        val initial = direction.y
        while (distance > 0f) {
            val step = minOf(distance, direction.remaining)
            dy += direction.y * step
            direction.travel(step)
            distance -= step
        }
        assertEquals(-10f * initial, dy)
        assertEquals(1980f, direction.remaining)
        direction.reset(true)
        assertEquals(2000f, direction.remaining)
    }
}
