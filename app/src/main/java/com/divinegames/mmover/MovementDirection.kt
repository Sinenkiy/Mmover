package com.divinegames.mmover

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Distance is measured before wrapping the texture, not from its visible offset. */
internal class MovementDirection(private val random: Random = Random.Default) {
    var x = 0f
        private set
    var y = 1f
        private set
    var remaining = TURN_DISTANCE
        private set
    private var verticalOnly = false

    fun reset(verticalOnly: Boolean) {
        this.verticalOnly = verticalOnly
        remaining = TURN_DISTANCE
        if (verticalOnly) {
            x = 0f
            y = if (random.nextBoolean()) 1f else -1f
        } else setAngle(random.nextDouble() * 2 * Math.PI)
    }

    /** The caller splits a frame at remaining so the turn occurs exactly at 2000 px. */
    fun travel(distance: Float) {
        require(distance.isFinite() && distance >= 0f && distance <= remaining)
        remaining -= distance
        if (remaining <= 0f) {
            if (verticalOnly) y = -y
            else setAngle(atan2(y.toDouble(), x.toDouble()) + Math.PI / 4 + random.nextDouble() * 1.5 * Math.PI)
            remaining = TURN_DISTANCE
        }
    }

    fun reflectX() { x = -x }
    fun reflectY() { y = -y }

    private fun setAngle(angle: Double) {
        x = cos(angle).toFloat()
        y = sin(angle).toFloat()
    }

    companion object { const val TURN_DISTANCE = 2000f }
}
