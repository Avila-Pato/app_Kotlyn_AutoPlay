package org.example.autoplay

import org.example.autoplay.ui.ambientTone
import org.example.autoplay.ui.averageRgb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoverColorsTest {
    private fun red(rgb: Int) = (rgb shr 16) and 0xFF
    private fun green(rgb: Int) = (rgb shr 8) and 0xFF
    private fun blue(rgb: Int) = rgb and 0xFF

    @Test fun averageMixesTheVisiblePixels() {
        val rgb = averageRgb(intArrayOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt()))!!
        assertEquals(127, red(rgb))
        assertEquals(0, green(rgb))
        assertEquals(127, blue(rgb))
    }

    @Test fun averageIgnoresTransparentPixels() {
        val rgb = averageRgb(intArrayOf(0x00FFFFFF, 0xFF102030.toInt()))!!
        assertEquals(0x10, red(rgb))
        assertEquals(0x20, green(rgb))
        assertEquals(0x30, blue(rgb))
    }

    @Test fun averageOfNothingVisibleIsNull() {
        assertNull(averageRgb(intArrayOf(0x00000000, 0x10FFFFFF)))
        assertNull(averageRgb(intArrayOf()))
    }

    @Test fun brightCoversAreCappedSoTextStaysReadable() {
        val tone = ambientTone(0xFFFFFF)
        assertEquals(120, red(tone))
        assertEquals(120, green(tone))
        assertEquals(120, blue(tone))
    }

    @Test fun darkCoversGetASmallLiftNotAWash() {
        val tone = ambientTone(0x1E1E1E)
        assertEquals(60, red(tone))
        assertTrue(red(tone) <= 2 * 0x1E)
    }

    @Test fun theHueSurvives() {
        // Orange stays orange: red still leads, blue still trails
        val tone = ambientTone(0xBD549E)
        assertTrue(red(tone) > blue(tone) && blue(tone) > green(tone))
        assertEquals(0xFF, tone ushr 24)
    }
}
