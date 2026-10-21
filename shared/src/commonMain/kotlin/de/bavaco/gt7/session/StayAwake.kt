package de.bavaco.gt7.session

private const val GRACE_MS = 120_000L

/**
 * Whether the computer should stay awake: while telemetry arrives and for two minutes after the last packet,
 * so a loading screen does not send it to sleep.
 */
class StayAwake {
    private var lastReceivingAtMs: Long? = null

    fun update(status: ConnectionStatus, nowMs: Long): Boolean {
        if (status == ConnectionStatus.Receiving) lastReceivingAtMs = nowMs
        val last = lastReceivingAtMs ?: return false
        return nowMs - last <= GRACE_MS
    }
}
