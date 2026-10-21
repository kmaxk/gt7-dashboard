package de.bavaco.gt7.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class PedalTraceTest {
    @Test fun keepsOnlyWindow() {
        val t = PedalTrace(windowPackets = 300)
        for (id in 0..400) t.add(id, 1f, 0f)
        val ids = t.points().map { it.packetId }
        assertEquals(101, ids.first())
        assertEquals(400, ids.last())
        assertEquals(300, ids.size)
    }

    @Test fun ignoresDuplicateAndOlderPackets() {
        val t = PedalTrace(windowPackets = 300)
        t.add(10, 0.5f, 0f)
        t.add(10, 0.9f, 0f)
        t.add(8, 0.1f, 0f)
        assertEquals(listOf(PedalPoint(10, 0.5f, 0f)), t.points())
    }

    @Test fun restartsOnStreamRestart() {
        // replay restarted or PS5 reconnected: ids jump far back
        val t = PedalTrace(windowPackets = 300)
        t.add(5000, 1f, 0f)
        t.add(3, 0f, 1f)
        assertEquals(listOf(PedalPoint(3, 0f, 1f)), t.points())
    }

    @Test fun extremePacketIdsDoNotOverflowWindow() {
        val t = PedalTrace(windowPackets = 300)
        t.add(Int.MIN_VALUE, 1f, 0f)
        t.add(Int.MAX_VALUE - 1, 0f, 1f)
        t.add(Int.MAX_VALUE, 0f, 1f)
        assertEquals(listOf(Int.MAX_VALUE - 1, Int.MAX_VALUE), t.points().map { it.packetId })
    }
}
