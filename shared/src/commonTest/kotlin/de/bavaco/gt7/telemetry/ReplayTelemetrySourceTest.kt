package de.bavaco.gt7.telemetry

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReplayTelemetrySourceTest {
    @Test
    fun replayKeepsRelativeTiming() = runTest {
        val rec = listOf(1000L, 1016L, 1050L).map { RawPacket(it, ByteArray(4)) }
        val source = ReplayTelemetrySource(rec, nowMs = { testScheduler.currentTime })
        val out = source.packets.toList()
        assertEquals(listOf(0L, 16L, 50L), out.map { it.atMs })
        assertEquals(50L, testScheduler.currentTime)
    }
}
