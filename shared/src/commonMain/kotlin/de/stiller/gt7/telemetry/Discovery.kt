package de.stiller.gt7.telemetry

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.SocketAddress
import io.ktor.network.sockets.aSocket
import kotlinx.io.readByteArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withTimeoutOrNull

/** Sends a heartbeat to [broadcastHost] and returns the host of the first sender of a valid GT7 packet, or null on timeout. */
suspend fun discoverPs5(
    timeoutMs: Long = 3_000,
    broadcastHost: String = "255.255.255.255",
    sendPort: Int = 33739,
    receivePort: Int = 33740,
): String? {
    val selector = SelectorManager(Dispatchers.IO)
    try {
        val socket = aSocket(selector).udp().bind(InetSocketAddress("0.0.0.0", receivePort)) { broadcast = true }
        try {
            try {
                socket.send(Datagram(heartbeatPacket(), InetSocketAddress(broadcastHost, sendPort)))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return null
            }
            return withTimeoutOrNull(timeoutMs) {
                var found: String? = null
                while (found == null) {
                    val datagram = socket.receive()
                    if (PacketDecoder.decode(datagram.packet.readByteArray()) != null) {
                        found = datagram.address.senderHost()
                    }
                }
                found
            }
        } finally {
            socket.close()
        }
    } finally {
        selector.close()
    }
}

/** Numeric sender address (hostname would trigger a reverse lookup and may yield e.g. "localhost"). */
internal fun SocketAddress.senderHost(): String? {
    val inet = this as? InetSocketAddress ?: return null
    val bytes = inet.resolveAddress()
    return if (bytes != null && bytes.size == 4) bytes.joinToString(".") { (it.toInt() and 0xFF).toString() } else inet.hostname
}
