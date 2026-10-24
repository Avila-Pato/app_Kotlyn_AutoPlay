package org.example.autoplay.mycar

/** Longest side of a picture kept on the phone: enough to read a paper, small enough to keep many. */
const val MAX_IMAGE_SIDE = 1600

/** [width] x [height] scaled down so its longer side is at most [max]; never scaled up. */
fun fitSize(width: Int, height: Int, max: Int): Pair<Int, Int> {
    val longest = maxOf(width, height)
    if (longest <= max || longest <= 0) return width to height
    val scale = max.toFloat() / longest
    return maxOf(1, Math.round(width * scale)) to maxOf(1, Math.round(height * scale))
}

/** Largest power of two to read the picture smaller by, keeping both sides at least [target]. */
fun sampleSizeFor(width: Int, height: Int, target: Int): Int {
    var sample = 1
    while (width / (sample * 2) >= target && height / (sample * 2) >= target) sample *= 2
    return sample
}

/**
 * Stored pictures nobody points to anymore. A picture just imported is not referenced until its paper is
 * saved, so those younger than [minAgeMillis] are left alone.
 */
fun orphanImages(files: List<Pair<String, Long>>, referenced: Set<String>, minAgeMillis: Long): List<String> =
    files.filter { (name, ageMillis) -> name !in referenced && ageMillis >= minAgeMillis }.map { it.first }
