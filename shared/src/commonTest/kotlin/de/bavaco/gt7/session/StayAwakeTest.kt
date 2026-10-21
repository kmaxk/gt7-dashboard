package de.bavaco.gt7.session

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StayAwakeTest {
    @Test fun neverReceivedLetsTheComputerSleep() {
        val s = StayAwake()
        assertFalse(s.update(ConnectionStatus.Idle, 0))
        assertFalse(s.update(ConnectionStatus.Searching, 1_000))
        assertFalse(s.update(ConnectionStatus.Waiting, 2_000))
    }

    @Test fun receivingKeepsItAwakeTwoMinutesBeyondTheLastPacket() {
        val s = StayAwake()
        assertTrue(s.update(ConnectionStatus.Receiving, 10_000))
        assertTrue(s.update(ConnectionStatus.Waiting, 10_000 + 60_000)) // loading screen
        assertTrue(s.update(ConnectionStatus.Waiting, 10_000 + 120_000))
        assertFalse(s.update(ConnectionStatus.Waiting, 10_000 + 120_001))
        assertTrue(s.update(ConnectionStatus.Receiving, 500_000)) // back on track
    }
}
