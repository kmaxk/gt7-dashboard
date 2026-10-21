package de.bavaco.gt7.session

import kotlin.math.sqrt

/**
 * Recognises a known track from the live position, long before a lap is complete: the recently
 * driven path (one point every [stepM]) is compared with each candidate's line. GT7 positions are
 * absolute per track, so a path on the same track lies on the line.
 */
class TrackLocator(
    private val stepM: Float = 5f,
    private val windowPoints: Int = 80,
    private val minPoints: Int = 60,
    private val matchM: Float = 8f,
    private val lostM: Float = 25f,
) {
    private val path = ArrayDeque<Pair<Float, Float>>()
    private var lines: Map<Long, List<Pair<Float, Float>>> = emptyMap()

    /** Candidate lines by track id; thinned to about one point per metre. */
    fun setCandidates(candidates: Map<Long, List<LapSample>>) {
        lines = candidates.mapValues { (_, samples) -> thin(samples.map { it.x to it.z }, 1f) }.filterValues { it.size >= 2 }
    }

    fun reset() = path.clear()

    fun add(x: Float, z: Float) {
        val last = path.lastOrNull()
        if (last != null && dist(last, x to z) < stepM) return
        path.addLast(x to z)
        while (path.size > windowPoints) path.removeFirst()
    }

    /**
     * The track to show: the unambiguous best match within [matchM], else [current] while the path
     * stays within [lostM] of its line, else null. Before [minPoints] points it keeps [current].
     */
    fun locate(current: Long?): Long? {
        if (path.size < minPoints) return current
        val distances = lines.mapValues { (_, line) -> meanDistance(path, line) }
        val ranked = distances.entries.sortedBy { it.value }
        val best = ranked.firstOrNull()
        val second = ranked.getOrNull(1)
        // two layouts sharing this stretch (e.g. GP and full circuit) stay undecided until they part
        val clear = best != null && best.value <= matchM && (second == null || second.value > matchM)
        if (clear) return best!!.key
        val keep = current?.let { distances[it] }?.let { it <= lostM } == true
        return if (keep) current else null
    }

    private fun meanDistance(path: Collection<Pair<Float, Float>>, line: List<Pair<Float, Float>>): Float =
        path.sumOf { p -> line.minOf { dist2(it, p) }.let { sqrt(it).toDouble() } }.toFloat() / path.size

    private fun thin(points: List<Pair<Float, Float>>, minStep: Float): List<Pair<Float, Float>> {
        val out = ArrayList<Pair<Float, Float>>()
        for (p in points) if (out.isEmpty() || dist(out.last(), p) >= minStep) out += p
        return out
    }

    private fun dist2(a: Pair<Float, Float>, b: Pair<Float, Float>): Float {
        val dx = a.first - b.first
        val dz = a.second - b.second
        return dx * dx + dz * dz
    }

    private fun dist(a: Pair<Float, Float>, b: Pair<Float, Float>) = sqrt(dist2(a, b))
}
