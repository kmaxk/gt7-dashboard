package de.bavaco.gt7.telemetry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class PacketDecoderTest {
    @Test
    fun decodesKnownFields() {
        val plain = plainPacket {
            float(0x4C, 50.0f)
            float(0x3C, 7000f)
            byte(0x90, 0x34)
            byte(0x91, 255)
            byte(0x92, 0)
            float(0x50, 1.8f)
            float(0x44, 40f)
            float(0x48, 100f)
            short(0x74, 3)
            short(0x76, 10)
            int(0x7C, 102381)
            int(0x124, 3332)
            short(0x8E, 0x01 or 0x02 or 0x04 or 0x10)
            float(0x04, 1.5f); float(0x08, -2.5f); float(0x0C, 3.5f)
            int(0x70, 777)
            int(0x78, 99999)
            short(0x84, -1)
            short(0x86, 20)
            short(0x88, 6500)
            short(0x8A, 7500)
            float(0x60, 85.5f); float(0x64, 86.5f); float(0x68, 87.5f); float(0x6C, 88.5f)
            float(0xA4, -12.5f); float(0xA8, 13.5f); float(0xAC, -14.5f); float(0xB0, 15.5f)
            float(0xB4, 0.31f); float(0xB8, 0.32f); float(0xBC, 0.33f); float(0xC0, 0.34f)
        }
        val f = assertNotNull(PacketDecoder.decode(encryptForTest(plain, 0x12345678)))
        assertEquals(50f, f.speedMps)
        assertEquals(7000f, f.rpm)
        assertEquals(4, f.gear)
        assertEquals(3, f.suggestedGear)
        assertEquals(1f, f.throttle)
        assertEquals(0f, f.brake)
        assertEquals(0.8f, f.boostBar, 1e-4f)
        assertEquals(40f, f.fuelLevel)
        assertEquals(100f, f.fuelCapacity)
        assertEquals(3, f.currentLap)
        assertEquals(10, f.totalLaps)
        assertEquals(102381, f.lastLapMs)
        assertEquals(3332, f.carCode)
        assertEquals(Wheels(85.5f, 86.5f, 87.5f, 88.5f), f.tireTempC)
        assertEquals(Wheels(-12.5f, 13.5f, -14.5f, 15.5f), f.wheelRps)
        assertEquals(Wheels(0.31f, 0.32f, 0.33f, 0.34f), f.tireRadiusM)
        assertEquals(1.5f, f.x)
        assertEquals(-2.5f, f.y)
        assertEquals(3.5f, f.z)
        assertEquals(777, f.packetId)
        assertEquals(99999, f.bestLapMs)
        assertEquals(-1, f.racePosition)
        assertEquals(20, f.totalPositions)
        assertEquals(6500, f.rpmAlertMin)
        assertEquals(7500, f.rpmAlertMax)
        assertTrue(f.onTrack && f.hasTurbo && f.paused && f.loading)
    }

    @Test
    fun rejectsWrongLength() {
        assertNull(PacketDecoder.decode(ByteArray(295)))
    }

    @Test
    fun rejectsWrongMagic() {
        val plain = plainPacket { int(0, 0) }
        assertNull(PacketDecoder.decode(encryptForTest(plain, 42)))
    }

    @Test
    fun decodesIndependentKnownAnswerPacket() {
        val hex =
            "d732167a5ee1049c1f1ef6aebd49f50218ea7403795ab10b46e16357f013a4e0fa87a81822addccf1b60daf1e777d31d" +
            "ed16c8cef2da66bbaed7b79378806a8778563412e03058379e6a490cc9d408f2cae9c2208d458b38f422d95796ad2ba5" +
            "1496b79fb7d56f41a12e4b6b2a8248946d79e0af9ba1d465bdcdcc4b366571e979684bb3227b4c8ba1b4e150af9c7b35" +
            "83776f5b3b68c517c4d5c51dfe0e221c0ed2d88e290170b04cf7e50436c82d4cc4640a2d3b8ebedb01a70e1dce8eb1e3" +
            "09212490ea6f18c6a0a9eb175fc1bbeca0a0f8db419e910acfb7ec38660eb7377cf8766a371b60b9990d5dadf66b4053" +
            "c409a9be8a31b809507f4c621882aa1c5a31f1266a9e838e013e0d5d64fea8cd52d4d49769114345e00266b51d5f3fd4" +
            "22a08894d1c7bd67"
        val raw = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        val f = assertNotNull(PacketDecoder.decode(raw))
        assertEquals(-123.5f, f.x)
        assertEquals(4.25f, f.y)
        assertEquals(987.0f, f.z)
        assertEquals(50.0f, f.speedMps)
        assertEquals(424242, f.packetId)
        assertEquals(3, f.currentLap)
        assertEquals(102381, f.lastLapMs)
        assertEquals(3332, f.carCode)
        assertEquals(Wheels(10f, 20f, 30f, 40f), f.wheelRps)
        assertEquals(0, f.gear)
        assertFalse(f.onTrack)
    }

    @Test
    fun decodesEngineTemperaturesAndCarFlags() {
        val plain = plainPacket {
            float(0x54, 4.5f); float(0x58, 85f); float(0x5C, 105f)
            short(0x8E, 0x01 or 0x20 or 0x80 or 0x100 or 0x400 or 0x800)
        }
        val f = assertNotNull(PacketDecoder.decode(encryptForTest(plain, 7)))
        assertEquals(4.5f, f.oilPressureBar)
        assertEquals(85f, f.waterTempC)
        assertEquals(105f, f.oilTempC)
        assertEquals(CarFlags(lights = true, highBeam = true, asm = true, tcs = true, revLimiter = true, handBrake = false), f.carFlags)
    }

    private fun packetB(block: PacketBuilder.() -> Unit) =
        encryptForTest(plainPacket(PacketDecoder.PACKET_SIZE_B, block), 0x12345678, 0xDEADBEEF.toInt())

    @Test
    fun decodesPacketB() {
        val f = assertNotNull(PacketDecoder.decode(packetB {
            float(0x4C, 50f)
            float(0x30, 0.7f)
            float(0x128, 0.5f)
            float(0x130, 9.81f)
            float(0x138, -4f)
        }))
        assertEquals(50f, f.speedMps)
        assertEquals(0.5f, f.steeringRad)
        assertEquals(-9.81f, f.latAccel) // GT7: right positive, the frame: left positive
        assertEquals(-4f, f.longAccel)
        assertEquals(0.7f, f.yawRate)
    }

    @Test
    fun packetAHasYawButNoSteering() {
        val f = assertNotNull(PacketDecoder.decode(encryptForTest(plainPacket { float(0x30, 0.7f) }, 0x12345678)))
        assertEquals(0.7f, f.yawRate)
        assertNull(f.steeringRad)
        assertNull(f.latAccel)
        assertNull(f.longAccel)
    }

    @Test
    fun nonFiniteDynamicsBecomeNull() {
        val f = assertNotNull(PacketDecoder.decode(packetB {
            float(0x128, Float.NaN)
            float(0x130, Float.POSITIVE_INFINITY)
            float(0x30, Float.NaN)
        }))
        assertNull(f.steeringRad)
        assertNull(f.latAccel)
        assertNull(f.yawRate)
    }

    @Test
    fun rejectsOtherLengths() {
        assertNull(PacketDecoder.decode(encryptForTest(plainPacket(344), 0x12345678, 0x55FABB4F)))
        assertNull(PacketDecoder.decode(encryptForTest(plainPacket(300), 0x12345678)))
    }
}
