package de.bavaco.gt7.telemetry

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.buffered
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TelemetryPipelineTest {
    private class ListSource(private val list: List<RawPacket>) : TelemetrySource {
        override val packets: Flow<RawPacket> = flowOf(*list.toTypedArray())
    }

    private fun valid(at: Long, iv: Int) = RawPacket(at, encryptForTest(plainPacket(), iv), "10.0.0.5")
    private fun broken(at: Long) = RawPacket(at, ByteArray(296))

    @Test
    fun dropsInvalidAndCounts() = runTest {
        val pipeline = TelemetryPipeline(ListSource(listOf(valid(0, 1), broken(100), valid(200, 2))), { null })
        val frames = pipeline.frames.toList()
        assertEquals(2, frames.size)
        assertEquals("10.0.0.5", frames[0].senderHost)
        assertEquals(200L, frames[1].atMs)
        assertEquals(1L, pipeline.stats.value.dropped)
        assertEquals(200L, pipeline.stats.value.lastPacketAtMs)
        assertEquals(3, pipeline.stats.value.packetsPerSecond)
    }

    @Test
    fun recorderGetsRawPackets() = runTest {
        val packets = listOf(valid(0, 1), broken(100), valid(200, 2))
        val buffer = Buffer()
        val recorder = PacketRecorder(buffer)
        TelemetryPipeline(ListSource(packets)) { recorder }.frames.toList()
        val read = readRecording(buffer)
        assertEquals(3, read.size)
        assertEquals(listOf(0L, 100L, 200L), read.map { it.atMs })
        assertEquals(296, read[1].bytes.size)
    }

    @Test
    fun packetsPerSecondCountsLastSecondOnly() = runTest {
        val pipeline = TelemetryPipeline(ListSource(listOf(valid(0, 1), valid(500, 2), valid(1400, 3))), { null })
        pipeline.frames.toList()
        assertEquals(2, pipeline.stats.value.packetsPerSecond)
    }

    @Test
    fun initialStatsAreEmpty() {
        val s = TelemetryPipeline(ListSource(emptyList()), { null }).stats.value
        assertEquals(0, s.packetsPerSecond)
        assertEquals(0L, s.dropped)
        assertNull(s.lastPacketAtMs)
    }

    @Test
    fun recorderFailureDoesNotStopFrames() = runTest {
        val failing = PacketRecorder(object : RawSink {
            override fun write(source: Buffer, byteCount: Long) = throw IllegalStateException("disk full")
            override fun flush() {}
            override fun close() {}
        }.buffered())
        val pipeline = TelemetryPipeline(ListSource(listOf(valid(0, 1), valid(100, 2))), { failing })
        assertEquals(2, pipeline.frames.toList().size)
        assertTrue(pipeline.stats.value.recordErrors > 0)
    }

    @Test
    fun statsResetOnRecollection() = runTest {
        val pipeline = TelemetryPipeline(ListSource(listOf(valid(0, 1), broken(100))), { null })
        pipeline.frames.toList()
        assertEquals(1L, pipeline.stats.value.dropped)
        pipeline.frames.toList()
        assertEquals(1L, pipeline.stats.value.dropped)
        assertEquals(2, pipeline.stats.value.packetsPerSecond)
    }
}
