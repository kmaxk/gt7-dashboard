package de.bavaco.gt7.ui

import de.bavaco.gt7.session.Cause
import de.bavaco.gt7.session.Corner
import de.bavaco.gt7.session.CornerDynamics
import de.bavaco.gt7.session.CornerReport
import de.bavaco.gt7.session.LapSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DynamicsLogicTest {
    private fun report(vararg causes: Cause, dyn: CornerDynamics = CornerDynamics.NONE, ref: CornerDynamics = CornerDynamics.NONE) =
        CornerReport(Corner(1, 0f, 50f, 100f), 100, null, null, 80f, 80f, null, null, 3, 3, causes.toList(), dyn, ref)

    private fun dyn(releaseM: Float?, releaseMs: Int?, steerPct: Int?) =
        CornerDynamics(releaseM, releaseMs, null, steerPct, null, null, null, null, null, null)

    @Test
    fun detailLine() {
        assertEquals(
            "Lösen −12 m · Auslaufen 0,1 / 0,4 s · Gas bei 70 / 40 % Lenkung",
            dynamicsDetail(report(dyn = dyn(138f, 100, 70), ref = dyn(150f, 400, 40))),
        )
    }

    @Test
    fun detailLineMissingValues() {
        assertEquals("Lösen – · Auslaufen – / – s · Gas bei – / – % Lenkung", dynamicsDetail(report()))
    }

    @Test
    fun handlingMarks() {
        assertEquals("US", handlingMark(report(Cause.Understeer)))
        assertEquals("ÜS", handlingMark(report(Cause.Oversteer(true))))
        assertEquals("US/ÜS", handlingMark(report(Cause.Understeer, Cause.Oversteer(false))))
        assertNull(handlingMark(report(Cause.Wheelspin)))
    }

    @Test
    fun hasDynamicsFalseForOldLaps() {
        val old = LapSample(0, 0f, 50f, 1f, 0f, 4, 7000f, 0f, 0f, 0f)
        assertFalse(hasDynamics(listOf(old)))
        assertTrue(hasDynamics(listOf(old, old.copy(steeringRad = 0.1f))))
    }
}
