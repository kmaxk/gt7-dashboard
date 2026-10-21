package de.bavaco.gt7.telemetry

import kotlinx.coroutines.flow.Flow

class RawPacket(val atMs: Long, val bytes: ByteArray, val senderHost: String? = null)

interface TelemetrySource {
    val packets: Flow<RawPacket>
}
