package de.bavaco.gt7.telemetry

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ReplayTelemetrySource(
    private val recording: List<RawPacket>,
    private val nowMs: () -> Long,
    private val realtime: Boolean = true,
) : TelemetrySource {
    override val packets: Flow<RawPacket> = flow {
        if (recording.isEmpty()) return@flow
        val start = nowMs()
        val first = recording.first().atMs
        for (p in recording) {
            val offset = p.atMs - first
            if (realtime) {
                val wait = start + offset - nowMs()
                if (wait > 0) delay(wait)
            }
            emit(RawPacket(start + offset, p.bytes))
        }
    }
}
