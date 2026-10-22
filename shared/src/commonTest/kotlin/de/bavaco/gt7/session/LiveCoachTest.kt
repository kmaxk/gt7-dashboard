package de.bavaco.gt7.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LiveCoachTest {
    private fun report(number: Int, start: Float, apex: Float, loss: Int, brake: Float? = start + 20f) = CornerReport(
        Corner(number, start, apex, apex + 100f), loss, brake, brake, 80f, 80f, null, null, 3, 3, listOf(Cause.BrakeEarly(10)),
    )

    private fun coach(vararg r: CornerReport) = LiveCoach().apply { set(Debrief(r.toList(), r.toList(), 0)) }

    @Test
    fun announcesTheCornerWithinTheLeadDistance() {
        val c = coach(report(2, 500f, 600f, 200))
        assertNull(c.tipAt(100f, 50f))
        val tip = c.tipAt(300f, 50f)!!
        assertEquals(2, tip.report.corner.number)
        assertEquals(220, tip.inM) // brake point 520 m, 220 m ahead
    }

    @Test
    fun staysUntilTheApexThenEnds() {
        val c = coach(report(2, 500f, 600f, 200))
        assertEquals(0, c.tipAt(550f, 50f)!!.inM)
        assertNull(c.tipAt(601f, 50f))
    }

    @Test
    fun keepsOnlyTheThreeCostliestCorners() {
        val reports = listOf(report(1, 100f, 150f, 300), report(2, 400f, 450f, 200), report(3, 700f, 750f, 100), report(4, 1000f, 1050f, 60))
        val c = LiveCoach().apply { set(Debrief(reports, reports.sortedByDescending { it.lossMs }, 0)) }
        assertNull(c.tipAt(1000f, 50f))
        assertEquals(3, c.tipAt(700f, 50f)!!.report.corner.number)
    }

    @Test
    fun clearedCoachIsSilent() {
        val c = coach(report(2, 500f, 600f, 200))
        c.set(null)
        assertNull(c.tipAt(400f, 50f))
    }
}
