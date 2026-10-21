package de.stiller.gt7.telemetry

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Salsa20Test {
    private fun ByteArray.toHex(): String =
        joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    @Test
    fun keystreamMatchesEcryptSet1Vector0() {
        val key = ByteArray(32).also { it[0] = 0x80.toByte() }
        val nonce = ByteArray(8)
        val out = Salsa20.xor(key, nonce, ByteArray(64))
        assertEquals(
            "e3be8fdd8beca2e3ea8ef9475b29a6e7003951e1097a5c38d23b7a5fad9f6844" +
                "b22c97559e2723c7cbbd3fe4fc8d9a0744652a83e72a9c461876af4d7ef1a117",
            out.toHex(),
        )
    }

    @Test
    fun xorIsInvolution() {
        val key = ByteArray(32) { (it * 7 + 1).toByte() }
        val nonce = ByteArray(8) { (it + 3).toByte() }
        val data = Random(42).nextBytes(150)
        val roundTrip = Salsa20.xor(key, nonce, Salsa20.xor(key, nonce, data))
        assertTrue(roundTrip.contentEquals(data))
    }
}
