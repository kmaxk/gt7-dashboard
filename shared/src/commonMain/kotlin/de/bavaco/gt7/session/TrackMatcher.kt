package de.bavaco.gt7.session

import kotlin.math.sqrt

/** A known track; [outline] holds POINTS x/z pairs (see [TrackMatcher.outlineOf]). Equality is content-based. */
data class Track(val id: Long, val name: String, val outline: FloatArray) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is Track && id == other.id && name == other.name && outline.contentEquals(other.outline))

    override fun hashCode(): Int = 31 * (31 * id.hashCode() + name.hashCode()) + outline.contentHashCode()
}

object TrackMatcher {
    const val POINTS = 200
    const val THRESHOLD_M = 15f

    /** POINTS x/z pairs, equally spaced over distance, relative to the first point. */
    fun outlineOf(samples: List<LapSample>): FloatArray {
        require(samples.size >= 2) { "need at least 2 samples" }
        val start = samples.first().distanceM
        val total = samples.last().distanceM - start
        val out = FloatArray(2 * POINTS)
        var seg = 0
        for (i in 0 until POINTS) {
            val d = start + total * i / (POINTS - 1)
            while (seg < samples.size - 2 && samples[seg + 1].distanceM < d) seg++
            val a = samples[seg]
            val b = samples[seg + 1]
            val span = b.distanceM - a.distanceM
            val t = if (span > 0f) ((d - a.distanceM) / span).coerceIn(0f, 1f) else 0f
            out[2 * i] = a.x + (b.x - a.x) * t
            out[2 * i + 1] = a.z + (b.z - a.z) * t
        }
        val x0 = out[0]
        val z0 = out[1]
        for (i in 0 until POINTS) {
            out[2 * i] -= x0
            out[2 * i + 1] -= z0
        }
        return out
    }

    fun meanDistance(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "outline sizes differ" }
        val n = a.size / 2
        if (n == 0) return 0f
        var sum = 0f
        for (i in 0 until n) {
            val dx = a[2 * i] - b[2 * i]
            val dz = a[2 * i + 1] - b[2 * i + 1]
            sum += sqrt(dx * dx + dz * dz)
        }
        return sum / n
    }

    /** The track with the smallest mean distance below THRESHOLD_M, or null. */
    fun findMatch(outline: FloatArray, tracks: List<Track>): Track? =
        tracks.map { it to meanDistance(outline, it.outline) }
            .filter { it.second < THRESHOLD_M }
            .minByOrNull { it.second }?.first
}
