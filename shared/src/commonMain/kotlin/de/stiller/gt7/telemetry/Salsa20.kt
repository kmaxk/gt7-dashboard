package de.stiller.gt7.telemetry

/** Salsa20/20 stream cipher (256-bit key, 64-bit nonce, block counter starting at 0). */
object Salsa20 {
    private val SIGMA = "expand 32-byte k".encodeToByteArray()

    fun xor(key: ByteArray, nonce: ByteArray, data: ByteArray): ByteArray {
        require(key.size == 32) { "key must be 32 bytes" }
        require(nonce.size == 8) { "nonce must be 8 bytes" }
        val state = IntArray(16)
        state[0] = le(SIGMA, 0)
        state[5] = le(SIGMA, 4)
        state[10] = le(SIGMA, 8)
        state[15] = le(SIGMA, 12)
        for (i in 0 until 4) {
            state[1 + i] = le(key, i * 4)
            state[11 + i] = le(key, 16 + i * 4)
        }
        state[6] = le(nonce, 0)
        state[7] = le(nonce, 4)

        val out = ByteArray(data.size)
        val block = ByteArray(64)
        var counter = 0L
        var pos = 0
        while (pos < data.size) {
            state[8] = counter.toInt()
            state[9] = (counter ushr 32).toInt()
            core(state, block)
            val n = minOf(64, data.size - pos)
            for (i in 0 until n) out[pos + i] = (data[pos + i].toInt() xor block[i].toInt()).toByte()
            pos += n
            counter++
        }
        return out
    }

    private fun le(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or
            ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or
            ((b[o + 3].toInt() and 0xFF) shl 24)

    private fun rotl(v: Int, c: Int): Int = (v shl c) or (v ushr (32 - c))

    private fun core(input: IntArray, out: ByteArray) {
        val x = input.copyOf()
        fun qr(a: Int, b: Int, c: Int, d: Int) {
            x[b] = x[b] xor rotl(x[a] + x[d], 7)
            x[c] = x[c] xor rotl(x[b] + x[a], 9)
            x[d] = x[d] xor rotl(x[c] + x[b], 13)
            x[a] = x[a] xor rotl(x[d] + x[c], 18)
        }
        repeat(10) {
            qr(0, 4, 8, 12); qr(5, 9, 13, 1); qr(10, 14, 2, 6); qr(15, 3, 7, 11)
            qr(0, 1, 2, 3); qr(5, 6, 7, 4); qr(10, 11, 8, 9); qr(15, 12, 13, 14)
        }
        for (i in 0 until 16) {
            val v = x[i] + input[i]
            out[i * 4] = v.toByte()
            out[i * 4 + 1] = (v ushr 8).toByte()
            out[i * 4 + 2] = (v ushr 16).toByte()
            out[i * 4 + 3] = (v ushr 24).toByte()
        }
    }
}
