package de.bavaco.gt7.telemetry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HeartbeatChooserTest {
    private val b = PacketDecoder.PACKET_SIZE_B
    private val a = PacketDecoder.PACKET_SIZE

    @Test
    fun startsWithB() {
        assertEquals('B', HeartbeatChooser(0L).type)
    }

    @Test
    fun staysOnBWhileReceivingB() {
        val c = HeartbeatChooser(0L)
        for (t in 0L..120_000L step 100) {
            c.onPacket(b, t)
            assertNull(c.tick(t))
        }
    }

    @Test
    fun silentBTriesAAfterFifteenSeconds() {
        val c = HeartbeatChooser(0L)
        assertNull(c.tick(14_999))
        assertEquals('A', c.tick(15_000))
        assertEquals('A', c.type)
    }

    @Test
    fun silentAGoesBackToBAfterFiveSeconds() {
        val c = HeartbeatChooser(0L)
        c.tick(15_000)
        assertNull(c.tick(19_999))
        assertEquals('B', c.tick(20_000))
    }

    @Test
    fun answeredAProbesBEveryMinuteAndKeepsIt() {
        val c = HeartbeatChooser(0L)
        c.tick(15_000)
        for (t in 15_100L until 75_000L step 100) {
            c.onPacket(a, t)
            assertNull(c.tick(t))
        }
        assertEquals('B', c.tick(75_000))
        for (t in 75_100L..90_000L step 100) {
            c.onPacket(b, t)
            assertNull(c.tick(t))
        }
    }

    @Test
    fun bAnsweredWithAPacketsGoesBackToA() {
        val c = HeartbeatChooser(0L)
        for (t in 0L until 5_000L step 100) {
            c.onPacket(a, t)
            assertNull(c.tick(t))
        }
        assertEquals('A', c.tick(5_000))
    }
}
