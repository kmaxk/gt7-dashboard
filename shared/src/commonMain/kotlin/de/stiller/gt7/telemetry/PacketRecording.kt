package de.stiller.gt7.telemetry

import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.EOFException
import kotlinx.io.readByteArray

/** Writes packets as `[i64 BE atMs][u16 BE len][bytes]`. */
class PacketRecorder(private val sink: Sink) : AutoCloseable {
    fun write(p: RawPacket) {
        require(p.bytes.size <= 0xFFFF) { "packet too large" }
        sink.writeLong(p.atMs)
        sink.writeShort(p.bytes.size.toShort())
        sink.write(p.bytes)
        sink.flush()
    }

    override fun close() = sink.close()
}

/** Reads all complete packets; a truncated tail is ignored. */
fun readRecording(source: Source): List<RawPacket> {
    val out = ArrayList<RawPacket>()
    try {
        while (!source.exhausted()) {
            val at = source.readLong()
            val len = source.readShort().toInt() and 0xFFFF
            out += RawPacket(at, source.readByteArray(len))
        }
    } catch (_: EOFException) {
    }
    return out
}
