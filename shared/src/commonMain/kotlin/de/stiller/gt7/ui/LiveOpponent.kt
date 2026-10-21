package de.stiller.gt7.ui

import de.stiller.gt7.session.Opponent
import de.stiller.gt7.session.ReferenceInfo

/** Texts that name the opponent in the time band and the side panel. */
data class OpponentLabels(val bestLabel: String, val deltaLabel: String, val gapTitle: String, val opponentName: String)

fun opponentLabels(opponent: Opponent, activeName: String): OpponentLabels = when (opponent) {
    Opponent.Self -> OpponentLabels("BESTE", "DELTA", "ABSTAND ZU DEINER BESTZEIT", activeName)
    is Opponent.Of -> {
        val name = opponent.driver.name
        OpponentLabels(name.uppercase(), "VS. ${name.uppercase()}", "ABSTAND ZU ${name.uppercase()}", name)
    }
}

/** Time in the band's best cell: the reference lap, else GT7's own best — but only when racing oneself. */
fun bestCellTimeMs(reference: ReferenceInfo?, opponent: Opponent, gt7BestLapMs: Int?): Int? =
    reference?.timeMs ?: gt7BestLapMs?.takeIf { opponent == Opponent.Self && it > 0 }

/** Path (x, z) of the current lap, restarted when the lap time drops; keeps the last [maxPoints]. */
class LapPath(private val maxPoints: Int = 2000) {
    private val points = ArrayDeque<Pair<Float, Float>>()
    private var lastLapTimeMs = -1

    fun add(lapTimeMs: Int, x: Float, z: Float) {
        if (lapTimeMs < lastLapTimeMs) points.clear()
        lastLapTimeMs = lapTimeMs
        points.addLast(x to z)
        while (points.size > maxPoints) points.removeFirst()
    }

    fun points(): List<Pair<Float, Float>> = points.toList()
}

/** Live deltas per packet, to compare the gap with the one [lookBackPackets] packets ago (~1 s at 60 Hz). */
class DeltaHistory(private val lookBackPackets: Int = 60) {
    private val deltas = ArrayDeque<Pair<Int, Int>>()
    private var lastLapTimeMs = -1

    fun add(packetId: Int, lapTimeMs: Int, deltaMs: Int) {
        // a new lap starts from a fresh gap; comparing with the previous lap's end would fake a trend
        if (lapTimeMs < lastLapTimeMs) deltas.clear()
        lastLapTimeMs = lapTimeMs
        val last = deltas.lastOrNull()
        if (last != null && packetId <= last.first) {
            if (packetId.toLong() >= last.first.toLong() - lookBackPackets) return // duplicate or late packet
            deltas.clear() // stream restarted
        }
        deltas.addLast(packetId to deltaMs)
        while (deltas.first().first.toLong() < packetId.toLong() - 2L * lookBackPackets) deltas.removeFirst()
    }

    /** Delta about [lookBackPackets] packets before [packetId] (nearest older one if packets were lost), or null. */
    fun before(packetId: Int): Int? {
        val target = packetId.toLong() - lookBackPackets
        return deltas.lastOrNull { it.first.toLong() <= target }?.second
    }
}
