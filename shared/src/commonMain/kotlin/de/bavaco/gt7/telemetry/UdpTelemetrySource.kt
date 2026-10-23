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
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource
import kotlinx.io.Source
import kotlinx.io.Buffer

/** "A" asks GT7 for 296-byte packets, "B" for 316 bytes with steering and accelerations. */
internal fun heartbeatPacket(type: Char = 'B'): Source = Buffer().apply { writeByte(type.code.toByte()) }

class UdpTelemetrySource(
    private val host: String,
    private val sendPort: Int = 33739,
    private val receivePort: Int = 33740,
    private val heartbeatIntervalMs: Long = 10_000,
    private val nowMs: () -> Long,
    private val timings: HeartbeatTimings = HeartbeatTimings(),
) : TelemetrySource {
    override val packets: Flow<RawPacket> = channelFlow {
        val selector = SelectorManager(Dispatchers.IO)
        try {
            val socket = aSocket(selector).udp().bind(InetSocketAddress("0.0.0.0", receivePort))
            try {
                val target = InetSocketAddress(host, sendPort)
                // shared by the heartbeat loop and the receive loop below
                val clock = TimeSource.Monotonic.markNow()
                fun elapsedMs() = clock.elapsedNow().inWholeMilliseconds
                val chooser = HeartbeatChooser(elapsedMs(), timings)
                val lock = Mutex()
                launch {
                    val tick = minOf(1_000L, timings.switchMs, heartbeatIntervalMs)
                    while (true) {
                        try {
                            socket.send(Datagram(heartbeatPacket(lock.withLock { chooser.type }), target))
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
                            val next = lock.withLock { chooser.tick(elapsedMs()) }
                            if (next != null) {
                                println("[GT7] heartbeat now $next")
                                break
                            }
                        }
                    }
                }
                println("[GT7] listening on ${socket.localAddress}, heartbeat to $host:$sendPort")
                var first = true
                while (true) {
                    val datagram = socket.receive()
                    if (first) println("[GT7] first packet from ${datagram.address}").also { first = false }
                    val bytes = datagram.packet.readByteArray()
                    // only a packet GT7 really sent counts as an answer to the heartbeat
                    if (PacketDecoder.decode(bytes) != null) lock.withLock { chooser.onPacket(bytes.size, elapsedMs()) }
                    send(RawPacket(nowMs(), bytes, datagram.address.senderHost()))
                }
            } finally {
                socket.close()
            }
        } finally {
            selector.close()
        }
    }
}
