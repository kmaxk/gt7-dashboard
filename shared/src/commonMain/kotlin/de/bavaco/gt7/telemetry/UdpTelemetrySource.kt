package de.bavaco.gt7.telemetry

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import kotlinx.io.readByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlin.time.TimeSource
import kotlinx.io.Source
import kotlinx.io.Buffer

/** "A" asks GT7 for 296-byte packets, "B" for 316 bytes with steering and accelerations. */
internal fun heartbeatPacket(type: Char = 'B'): Source = Buffer().apply { writeByte(type.code.toByte()) }

private const val SILENCE_SWITCH_MS = 5_000L

/** GT7 versions without packet type B stay silent on "B": after [SILENCE_SWITCH_MS] of silence try the other type. */
internal fun nextHeartbeat(current: Char, silentMs: Long, switchMs: Long = SILENCE_SWITCH_MS): Char =
    if (silentMs < switchMs) current else if (current == 'B') 'A' else 'B'

class UdpTelemetrySource(
    private val host: String,
    private val sendPort: Int = 33739,
    private val receivePort: Int = 33740,
    private val heartbeatIntervalMs: Long = 10_000,
    private val nowMs: () -> Long,
    private val silenceSwitchMs: Long = SILENCE_SWITCH_MS,
) : TelemetrySource {
    override val packets: Flow<RawPacket> = channelFlow {
        val selector = SelectorManager(Dispatchers.IO)
        try {
            val socket = aSocket(selector).udp().bind(InetSocketAddress("0.0.0.0", receivePort))
            try {
                val target = InetSocketAddress(host, sendPort)
                // last packet, or last switch of the heartbeat type; shared with the receive loop below
                val quietSince = MutableStateFlow(TimeSource.Monotonic.markNow())
                launch {
                    var type = 'B'
                    val tick = minOf(1_000L, silenceSwitchMs, heartbeatIntervalMs)
                    while (true) {
                        try {
                            socket.send(Datagram(heartbeatPacket(type), target))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // network unreachable etc.: retry on next tick
                            println("[GT7] heartbeat to $host:$sendPort failed: $e")
                        }
                        var waited = 0L
                        while (waited < heartbeatIntervalMs) {
                            delay(tick)
                            waited += tick
                            val next = nextHeartbeat(type, quietSince.value.elapsedNow().inWholeMilliseconds, silenceSwitchMs)
                            if (next != type) {
                                type = next
                                quietSince.value = TimeSource.Monotonic.markNow()
                                println("[GT7] no packets, heartbeat now $type")
                                break
                            }
                        }
                    }
                }
                println("[GT7] listening on ${socket.localAddress}, heartbeat to $host:$sendPort")
                var first = true
                while (true) {
                    val datagram = socket.receive()
                    quietSince.value = TimeSource.Monotonic.markNow()
                    if (first) println("[GT7] first packet from ${datagram.address}").also { first = false }
                    send(RawPacket(nowMs(), datagram.packet.readByteArray(), datagram.address.senderHost()))
                }
            } finally {
                socket.close()
            }
        } finally {
            selector.close()
        }
    }
}
