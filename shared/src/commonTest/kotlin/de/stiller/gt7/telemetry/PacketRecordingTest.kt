package de.stiller.gt7.telemetry

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class PacketRecordingTest {
    private fun sample() = listOf(
        RawPacket(1000L, ByteArray(296) { it.toByte() }),
        RawPacket(1016L, ByteArray(296) { (it * 3).toByte() }),
        RawPacket(1050L, ByteArray(10) { (it + 7).toByte() }),
    )

    private fun record(packets: List<RawPacket>): Buffer {
        val buffer = Buffer()
        PacketRecorder(buffer).use { r -> packets.forEach { r.write(it) } }
        return buffer
    }

    @Test
    fun recordingRoundTrips() {
        val input = sample()
        val out = readRecording(record(input))
        assertEquals(3, out.size)
        input.zip(out).forEach { (a, b) ->
            assertEquals(a.atMs, b.atMs)
            assertContentEquals(a.bytes, b.bytes)
        }
    }

    @Test
    fun truncatedTailIsIgnored() {
        val bytes = record(sample()).readByteArray()
        val truncated = Buffer().apply { write(bytes.copyOfRange(0, bytes.size - 5)) }
        assertEquals(2, readRecording(truncated).size)
    }
}
