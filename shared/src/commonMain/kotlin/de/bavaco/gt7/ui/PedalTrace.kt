package de.bavaco.gt7.ui

data class PedalPoint(val packetId: Int, val throttle: Float, val brake: Float)

/** Rolling throttle/brake history over the last [windowPackets] packets (GT7 sends ~60 per second). */
class PedalTrace(val windowPackets: Int = 300) {
    private val points = ArrayDeque<PedalPoint>()

    fun add(packetId: Int, throttle: Float, brake: Float) {
        // ids come straight off the network: compare in Long so extreme values cannot overflow
        val id = packetId.toLong()
        val last = points.lastOrNull()
        if (last != null && id <= last.packetId) {
            if (id >= last.packetId.toLong() - windowPackets) return // duplicate or late packet
            points.clear() // stream restarted
        }
        points.addLast(PedalPoint(packetId, throttle, brake))
        while (id - points.first().packetId >= windowPackets || points.size > windowPackets) points.removeFirst()
    }

    fun points(): List<PedalPoint> = points.toList()
}
