package com.divinegames.mmover

import kotlin.math.floor


/** Fixed-size periodic textures. No Android objects or screen-sized allocations. */
internal object ProceduralBackground {
    const val SIZE = 240
    val keys = listOf(
        "generated_contrast_noise", "generated_perlin_small",
        "generated_mosaic", "generated_stripes", "generated_broken_grid"
    )

    /** Preserve retained selections; replace only backgrounds removed from the picker. */
    fun migrateKey(key: String?): String? = when (key) {
        "generated_perlin" -> "generated_perlin_small"
        "generated_white_noise" -> "generated_contrast_noise"
        "generated_color_noise", "generated_gray_noise" -> "generated_contrast_noise"
        "generated_voronoi" -> "generated_mosaic"
        "generated_subpixel", "background_stripes" -> "generated_stripes"
        "generated_broken_grid_large" -> "generated_broken_grid"
        "background_blocks_small", "background_blocks_medium", "background_blocks_large" -> "generated_mosaic"
        else -> key
    }

    fun generate(key: String): IntArray {
        require(key in keys)
        return IntArray(SIZE * SIZE) { index -> pixel(key, index % SIZE, index / SIZE) }
    }

    private fun hash(x: Int, y: Int): Int {
        var n = x * 374761393 + y * 668265263 + 1447
        n = (n xor (n ushr 13)) * 1274126177
        return n xor (n ushr 16)
    }

    private fun binary(value: Boolean) = if (value) -1 else -0x1000000

    // Periodic coordinates allow a seamless REPEAT shader without an oversized bitmap.
    internal fun pixel(key: String, x: Int, y: Int): Int {
        val px = Math.floorMod(x, SIZE)
        val py = Math.floorMod(y, SIZE)
        return when (key) {
            "generated_mosaic" -> binary(hash(px / 16, py / 16) and 1 == 0)
            "generated_stripes" -> binary(py < 12 || py in 36..59 || py in 72..107 || py in 132..179 || py in 204..215)
            "generated_broken_grid" -> {
                val cell = 24
                val line = cell / 6
                val direction = hash(px / cell, py / cell) and 1
                binary(if (direction == 0) px % cell < line else py % cell < line)
            }
            "generated_contrast_noise" -> binary(hash(px / 4, py / 4) and 1 == 0)
            "generated_perlin_small" -> {
                val step = 12.0
                val period = (SIZE / step).toInt()
                binary(perlin(px / step, py / step, period) +
                    0.35 * perlin(px / (step / 2), py / (step / 2), period * 2) > 0)
            }
            else -> error("Unknown procedural background: $key")
        }
    }

    private fun perlin(x: Double, y: Double, period: Int = 10): Double {
        val ix = floor(x).toInt()
        val iy = floor(y).toInt()
        val fx = x - ix
        val fy = y - iy
        fun gradient(cx: Int, cy: Int, dx: Double, dy: Double): Double =
            when (hash(Math.floorMod(cx, period), Math.floorMod(cy, period)) and 7) {
                0 -> dx; 1 -> -dx; 2 -> dy; 3 -> -dy
                4 -> (dx + dy) * 0.70710678; 5 -> (dx - dy) * 0.70710678
                6 -> (-dx + dy) * 0.70710678; else -> (-dx - dy) * 0.70710678
            }
        fun fade(t: Double) = t * t * t * (t * (t * 6 - 15) + 10)
        fun mix(a: Double, b: Double, t: Double) = a + (b - a) * t
        return mix(
            mix(gradient(ix, iy, fx, fy), gradient(ix + 1, iy, fx - 1, fy), fade(fx)),
            mix(gradient(ix, iy + 1, fx, fy - 1), gradient(ix + 1, iy + 1, fx - 1, fy - 1), fade(fx)),
            fade(fy)
        )
    }
}
