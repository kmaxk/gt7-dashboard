package de.stiller.gt7.telemetry

import java.io.DataInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Packets captured from a real PS5 running GT7 (file format: 4-byte big-endian length + packet). */
class RealPacketTest {
    private fun realPackets(): List<ByteArray> {
        val input = DataInputStream(javaClass.getResourceAsStream("/real_packets.bin")!!)
        return List(3) { ByteArray(input.readInt()).also { input.readFully(it) } }
    }

    @Test fun decodesRealPackets() {
        val frames = realPackets().map { assertNotNull(PacketDecoder.decode(it), "packet not decoded") }
        assertEquals(listOf(1, 1), frames.zipWithNext { a, b -> b.packetId - a.packetId })
    }
}
