package org.example.autoplay.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import kotlin.math.max

private const val SAMPLE = 16

/** Brightest channel of the tint: low enough that white text stays readable on top of it. */
private const val TARGET_PEAK = 120f

/** Very dark covers get a small lift, not a gray wash. */
private const val MAX_BOOST = 2f

/** Average RGB of the visible pixels (ARGB ints), or null when they are all transparent. */
fun averageRgb(pixels: IntArray): Int? {
    var r = 0L
    var g = 0L
    var b = 0L
    var count = 0
    for (pixel in pixels) {
        if ((pixel ushr 24) < 128) continue
        r += (pixel shr 16) and 0xFF
        g += (pixel shr 8) and 0xFF
        b += pixel and 0xFF
        count++
    }
    if (count == 0) return null
    return ((r / count).toInt() shl 16) or ((g / count).toInt() shl 8) or (b / count).toInt()
}

/** Keeps the hue of [rgb] but caps its brightness so it works as a background behind text. */
fun ambientTone(rgb: Int): Int {
    val r = (rgb shr 16) and 0xFF
    val g = (rgb shr 8) and 0xFF
    val b = rgb and 0xFF
    val peak = max(r, max(g, b)).coerceAtLeast(1)
    val factor = (TARGET_PEAK / peak).coerceAtMost(MAX_BOOST)
    fun scale(channel: Int) = (channel * factor).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (scale(r) shl 16) or (scale(g) shl 8) or scale(b)
}

/** The color a cover lends to the player's background; looks at a 16x16 thumbnail, so it is cheap. */
fun coverTint(art: Bitmap): Color? {
    val small = Bitmap.createScaledBitmap(art, SAMPLE, SAMPLE, true)
    val pixels = IntArray(SAMPLE * SAMPLE)
    small.getPixels(pixels, 0, SAMPLE, 0, 0, SAMPLE, SAMPLE)
    if (small !== art) small.recycle()
    return averageRgb(pixels)?.let { Color(ambientTone(it)) }
}
