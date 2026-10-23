package de.bavaco.gt7.telemetry

/** How long [HeartbeatChooser] waits before it changes the heartbeat type, in ms. */
data class HeartbeatTimings(
    /** Back from "A" to "B" after this much silence; back to "A" when "B" brings only type-A packets this long. */
    val switchMs: Long = 5_000,
    /** Try "A" after this much silence on "B" (a GT7 without packet type B stays silent). */
    val tryAMs: Long = 15_000,
    /** While "A" is answered, try "B" again this often (the console may have been off when "B" was tried). */
    val probeBMs: Long = 60_000,
)

/**
 * Picks the heartbeat letter: "B" (packets with steering and accelerations) whenever GT7 answers it, "A" only
 * when "B" goes unanswered. Not thread-safe; times in ms from any fixed origin, [startMs] = now.
 */
class HeartbeatChooser(startMs: Long, private val timings: HeartbeatTimings = HeartbeatTimings()) {
    var type = 'B'
        private set
    private var typeSince = startMs
    private var lastPacketAt = Long.MIN_VALUE / 2
    private var lastBPacketAt = Long.MIN_VALUE / 2

    /** A valid packet of [size] bytes arrived. */
    fun onPacket(size: Int, atMs: Long) {
        lastPacketAt = atMs
        if (size == PacketDecoder.PACKET_SIZE_B) lastBPacketAt = atMs
    }

    /** The new type if it changes now (send a heartbeat of it at once), else null. */
    fun tick(nowMs: Long): Char? {
        val quiet = nowMs - maxOf(lastPacketAt, typeSince)
        val receiving = nowMs - lastPacketAt < timings.switchMs
        val next = when (type) {
            'B' -> when {
                quiet >= timings.tryAMs -> 'A'
                receiving && nowMs - maxOf(typeSince, lastBPacketAt) >= timings.switchMs -> 'A'
                else -> null
            }
            else -> when {
                quiet >= timings.switchMs -> 'B'
                nowMs - typeSince >= timings.probeBMs -> 'B'
                else -> null
            }
        }
        if (next != null) {
            type = next
            typeSince = nowMs
        }
        return next
    }
}
