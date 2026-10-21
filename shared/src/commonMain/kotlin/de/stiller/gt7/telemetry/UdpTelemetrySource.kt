package de.stiller.gt7.telemetry

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
import kotlinx.io.Source
import kotlinx.io.Buffer

internal fun heartbeatPacket(): Source = Buffer().apply { writeByte('A'.code.toByte()) }

class UdpTelemetrySource(
    private val host: String,
    private val sendPort: Int = 33739,
    private val receivePort: Int = 33740,
    private val heartbeatIntervalMs: Long = 10_000,
    private val nowMs: () -> Long,
) : TelemetrySource {
    override val packets: Flow<RawPacket> = channelFlow {
        val selector = SelectorManager(Dispatchers.IO)
        try {
            val socket = aSocket(selector).udp().bind(InetSocketAddress("0.0.0.0", receivePort))
            try {
                val target = InetSocketAddress(host, sendPort)
                launch {
                    while (true) {
                        try {
                            socket.send(Datagram(heartbeatPacket(), target))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // network unreachable etc.: retry on next tick
                            println("[GT7] heartbeat to $host:$sendPort failed: $e")
                        }
                        delay(heartbeatIntervalMs)
                    }
                }
                println("[GT7] listening on ${socket.localAddress}, heartbeat to $host:$sendPort")
                var first = true
                while (true) {
                    val datagram = socket.receive()
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
