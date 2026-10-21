package de.bavaco.gt7.telemetry

/** Decrypts and parses GT7 telemetry packets of type "A" (296 bytes). */
object PacketDecoder {
    const val PACKET_SIZE = 296
    private const val MAGIC = 0x47375330
    private const val IV_MASK = 0xDEADBEAF.toInt()
    private val KEY = "Simulator Interface Packet GT7 ver 0.0".encodeToByteArray().copyOf(32)

    fun decode(raw: ByteArray): TelemetryFrame? {
        if (raw.size != PACKET_SIZE) return null
        val iv1 = i32(raw, 0x40)
        val iv2 = iv1 xor IV_MASK
        val nonce = ByteArray(8)
        for (i in 0 until 4) {
            nonce[i] = (iv2 ushr (8 * i)).toByte()
            nonce[4 + i] = (iv1 ushr (8 * i)).toByte()
        }
        val p = Salsa20.xor(KEY, nonce, raw)
        if (i32(p, 0) != MAGIC) return null

        val flags = i16(p, 0x8E)
        val gearByte = p[0x90].toInt() and 0xFF
        return TelemetryFrame(
            packetId = i32(p, 0x70),
            x = f32(p, 0x04), y = f32(p, 0x08), z = f32(p, 0x0C),
            speedMps = f32(p, 0x4C),
            rpm = f32(p, 0x3C),
            rpmAlertMin = i16(p, 0x88),
            rpmAlertMax = i16(p, 0x8A),
            gear = gearByte and 0x0F,
            suggestedGear = gearByte ushr 4,
            throttle = u8(p, 0x91) / 255f,
            brake = u8(p, 0x92) / 255f,
            boostBar = f32(p, 0x50) - 1f,
            fuelLevel = f32(p, 0x44),
            fuelCapacity = f32(p, 0x48),
            tireTempC = wheels(p, 0x60),
            wheelRps = wheels(p, 0xA4),
            tireRadiusM = wheels(p, 0xB4),
            currentLap = i16(p, 0x74),
            totalLaps = i16(p, 0x76),
            bestLapMs = i32(p, 0x78),
            lastLapMs = i32(p, 0x7C),
            racePosition = i16(p, 0x84),
            totalPositions = i16(p, 0x86),
            carCode = i32(p, 0x124),
            onTrack = flags and 0x01 != 0,
            paused = flags and 0x02 != 0,
            loading = flags and 0x04 != 0,
            hasTurbo = flags and 0x10 != 0,
            oilPressureBar = f32(p, 0x54),
            waterTempC = f32(p, 0x58),
            oilTempC = f32(p, 0x5C),
            carFlags = CarFlags(
                lights = flags and 0x80 != 0,
                highBeam = flags and 0x100 != 0,
                asm = flags and 0x400 != 0,
                tcs = flags and 0x800 != 0,
                revLimiter = flags and 0x20 != 0,
                handBrake = flags and 0x40 != 0,
            ),
        )
    }

    private fun u8(b: ByteArray, o: Int) = b[o].toInt() and 0xFF
    private fun i16(b: ByteArray, o: Int) = ((u8(b, o + 1) shl 8) or u8(b, o)).toShort().toInt()
    private fun i32(b: ByteArray, o: Int) =
        (u8(b, o + 3) shl 24) or (u8(b, o + 2) shl 16) or (u8(b, o + 1) shl 8) or u8(b, o)
    private fun f32(b: ByteArray, o: Int) = Float.fromBits(i32(b, o))
    private fun wheels(b: ByteArray, o: Int) =
        Wheels(f32(b, o), f32(b, o + 4), f32(b, o + 8), f32(b, o + 12))
}
