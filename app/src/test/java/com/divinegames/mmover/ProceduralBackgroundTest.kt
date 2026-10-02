package com.divinegames.mmover

import org.junit.Assert.*
import org.junit.Test

class ProceduralBackgroundTest {
    @Test fun migrationOnlyReplacesRemovedStylesAndIsIdempotent() {
        val replacements = mapOf(
            "generated_perlin" to "generated_perlin_small",
            "generated_white_noise" to "generated_contrast_noise",
            "generated_color_noise" to "generated_contrast_noise",
            "generated_gray_noise" to "generated_contrast_noise",
            "generated_broken_grid_large" to "generated_broken_grid",
            "background_blocks_small" to "generated_mosaic",
            "background_blocks_medium" to "generated_mosaic",
            "background_blocks_large" to "generated_mosaic",
            "background_stripes" to "generated_stripes",
            "generated_voronoi" to "generated_mosaic",
            "generated_subpixel" to "generated_stripes"
        )
        for ((old, replacement) in replacements) {
            assertEquals(replacement, ProceduralBackground.migrateKey(old))
            assertEquals(replacement, ProceduralBackground.migrateKey(replacement))
        }
        for (key in ProceduralBackground.keys) {
            assertEquals(key, ProceduralBackground.migrateKey(key))
        }
        assertNull(ProceduralBackground.migrateKey(null))
    }

    @Test fun texturesAreBoundedOpaqueDistinctAndDeterministic() {
        val fingerprints = mutableSetOf<Int>()
        for (key in ProceduralBackground.keys) {
            val pixels = ProceduralBackground.generate(key)
            assertEquals(240 * 240, pixels.size)
            assertTrue(key, pixels.all { it ushr 24 == 255 })
            assertTrue(key, pixels.toSet().size > 1)
            assertArrayEquals(pixels, ProceduralBackground.generate(key))
            assertTrue(key, fingerprints.add(pixels.contentHashCode()))
        }
    }

    @Test fun textureSamplingWrapsInBothDirections() {
        for (key in ProceduralBackground.keys) {
            for (x in listOf(-241, -1, 0, 17, 239, 240)) {
                for (y in listOf(-240, -1, 0, 53, 239, 241)) {
                    assertEquals(key, ProceduralBackground.pixel(key, x, y),
                        ProceduralBackground.pixel(key, x + 240, y - 240))
                }
            }
        }
    }
}
