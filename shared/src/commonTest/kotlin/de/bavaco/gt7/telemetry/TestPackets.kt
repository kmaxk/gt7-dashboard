package de.bavaco.gt7.telemetry

class PacketBuilder(val bytes: ByteArray) {
    fun byte(offset: Int, v: Int) {
        bytes[offset] = v.toByte()
    }

    fun short(offset: Int, v: Int) {
        for (i in 0 until 2) bytes[offset + i] = (v ushr (8 * i)).toByte()
    }

    fun int(offset: Int, v: Int) {
        for (i in 0 until 4) bytes[offset + i] = (v ushr (8 * i)).toByte()
    }

    fun float(offset: Int, v: Float) = int(offset, v.toRawBits())
}

/** 296-byte plaintext packet with the GT7 magic set; [block] sets further fields. */
fun plainPacket(block: PacketBuilder.() -> Unit = {}): ByteArray {
    val b = ByteArray(PacketDecoder.PACKET_SIZE)
    val builder = PacketBuilder(b)
    builder.int(0, 0x47375330)
    builder.block()
    return b
}

/** Encrypts [plain] like GT7 does, using a nonce derived from [iv1], and stores [iv1] at 0x40. */
fun encryptForTest(plain: ByteArray, iv1: Int): ByteArray {
    val iv2 = iv1 xor 0xDEADBEAF.toInt()
    val nonce = ByteArray(8)
    for (i in 0 until 4) {
        nonce[i] = (iv2 ushr (8 * i)).toByte()
        nonce[4 + i] = (iv1 ushr (8 * i)).toByte()
    }
    val key = "Simulator Interface Packet GT7 ver 0.0".encodeToByteArray().copyOf(32)
    val enc = Salsa20.xor(key, nonce, plain)
    PacketBuilder(enc).int(0x40, iv1)
    return enc
}
