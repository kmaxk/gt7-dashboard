package de.bavaco.gt7.telemetry

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class UdpTelemetrySourceTest {
    private fun freePort(): Int = DatagramSocket(0).use { it.localPort }

    /** Fake PS5: counts heartbeats ("A") and optionally replies with a valid packet. */
    private class FakePs5(val reply: Boolean) : AutoCloseable {
        val socket = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        val port get() = socket.localPort
        val heartbeats = AtomicInteger()
        private val worker = thread(isDaemon = true) {
            val buf = ByteArray(16)
            try {
                while (true) {
                    val p = DatagramPacket(buf, buf.size)
                    socket.receive(p)
                    if (p.length == 1 && buf[0] == 'A'.code.toByte()) {
                        heartbeats.incrementAndGet()
                        if (reply) {
                            val data = encryptForTest(plainPacket {}, 1)
                            socket.send(DatagramPacket(data, data.size, p.address, p.port))
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        override fun close() = socket.close()
    }

    @Test
    fun sendsHeartbeatAndEmitsReply() = runBlocking {
        FakePs5(reply = true).use { ps5 ->
            val source = UdpTelemetrySource("127.0.0.1", ps5.port, freePort(), nowMs = { 42L })
            val packet = withTimeout(2000) { source.packets.first() }
            assertEquals(296, packet.bytes.size)
            assertEquals(42L, packet.atMs)
            assertEquals("127.0.0.1", packet.senderHost)
        }
    }

    @Test
    fun repeatsHeartbeat() = runBlocking {
        FakePs5(reply = false).use { ps5 ->
            val source = UdpTelemetrySource("127.0.0.1", ps5.port, freePort(), 200, nowMs = { 0L })
            val job = launch(Dispatchers.Default) { source.packets.collect { } }
            delay(700)
            job.cancel()
            assertTrue(ps5.heartbeats.get() >= 3, "heartbeats: ${ps5.heartbeats.get()}")
        }
    }

    @Test
    fun discoveryReturnsNullOnTimeout() = runBlocking {
        val start = System.currentTimeMillis()
        val result = withTimeout(1000) {
            discoverPs5(timeoutMs = 300, broadcastHost = "127.0.0.1", sendPort = freePort(), receivePort = freePort())
        }
        assertNull(result)
        assertTrue(System.currentTimeMillis() - start < 1000)
    }

    @Test
    fun discoveryFindsSender() = runBlocking {
        FakePs5(reply = true).use { ps5 ->
            val result = withTimeout(2000) {
                discoverPs5(timeoutMs = 1500, broadcastHost = "127.0.0.1", sendPort = ps5.port, receivePort = freePort())
            }
            assertEquals("127.0.0.1", result)
        }
    }

    @Test
    fun bindFailureClosesSelectorAndThrows() = runBlocking {
        val port = freePort()
        DatagramSocket(port).use {
            val source = UdpTelemetrySource("127.0.0.1", freePort(), port, nowMs = { 0L })
            val failed = withTimeout(2000) { runCatching { source.packets.first() }.isFailure }
            assertTrue(failed)
        }
    }

    @Test
    fun socketReleasedAfterCancel() = runBlocking {
        FakePs5(reply = true).use { ps5 ->
            val port = freePort()
            val source = UdpTelemetrySource("127.0.0.1", ps5.port, port, nowMs = { 0L })
            withTimeout(2000) { source.packets.first() }
            // first() cancels the collection; the port must be bindable again
            withTimeout(2000) {
                while (runCatching { DatagramSocket(port).close() }.isFailure) delay(50)
            }
        }
    }
}
