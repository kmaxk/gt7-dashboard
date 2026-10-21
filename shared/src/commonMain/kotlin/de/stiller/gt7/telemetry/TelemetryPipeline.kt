package de.stiller.gt7.telemetry

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlin.coroutines.cancellation.CancellationException

data class TimedFrame(val atMs: Long, val frame: TelemetryFrame, val senderHost: String?)

data class PacketStats(
    val packetsPerSecond: Int,
    val dropped: Long,
    val lastPacketAtMs: Long?,
    val recordErrors: Long = 0,
)

/**
 * Decodes packets from [source]. [recorder] is queried per packet and receives the raw packet
 * before decoding; recording failures are counted in [PacketStats.recordErrors] and never stop the flow.
 * [stats] is updated while [frames] is being collected and reset at the start of each collection.
 * The pipeline is single-collector by design.
 */
class TelemetryPipeline(
    private val source: TelemetrySource,
    private val recorder: () -> PacketRecorder?,
) {
    private val _stats = MutableStateFlow(PacketStats(0, 0L, null))
    val stats: StateFlow<PacketStats> = _stats

    val frames: Flow<TimedFrame> = flow {
        _stats.value = PacketStats(0, 0L, null)
        val window = ArrayDeque<Long>()
        source.packets.collect { packet ->
            val recordFailed = try {
                recorder()?.write(packet)
                false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                true
            }
            window.addLast(packet.atMs)
            while (window.isNotEmpty() && window.first() <= packet.atMs - 1000) window.removeFirst()
            val frame = PacketDecoder.decode(packet.bytes)
            _stats.update {
                it.copy(
                    packetsPerSecond = window.size,
                    dropped = it.dropped + if (frame == null) 1 else 0,
                    lastPacketAtMs = packet.atMs,
                    recordErrors = it.recordErrors + if (recordFailed) 1 else 0,
                )
            }
            if (frame != null) emit(TimedFrame(packet.atMs, frame, packet.senderHost))
        }
    }
}
