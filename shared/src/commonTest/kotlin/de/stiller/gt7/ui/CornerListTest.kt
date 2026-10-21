package de.stiller.gt7.ui

import de.stiller.gt7.session.Corner
import de.stiller.gt7.session.CornerReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CornerListTest {
    private val cs = (1..4).map { Corner(it, it * 100f, it * 100f + 50f, it * 100f + 100f) }

    private fun reports(vararg losses: Int) = losses.mapIndexed { i, loss ->
        CornerReport(Corner(i + 1, 0f, 0f, 0f), loss, null, null, 80f, 80f, null, null, 3, 3, emptyList())
    }

    @Test
    fun stepCornerMovesAndStopsAtTheEnds() {
        assertEquals(cs[0], stepCorner(cs, null, +1))
        assertEquals(cs[3], stepCorner(cs, null, -1))
        assertEquals(cs[2], stepCorner(cs, cs[1], +1))
        assertEquals(cs[0], stepCorner(cs, cs[1], -1))
        assertEquals(cs[3], stepCorner(cs, cs[3], +1))
        assertEquals(cs[0], stepCorner(cs, cs[0], -1))
        assertNull(stepCorner(emptyList(), null, +1))
    }

    @Test
    fun costliestCornersOnlyCountLosses() {
        assertEquals(setOf(4, 5, 1), costliestCorners(reports(560, 140, -20, 840, 820)))
        assertEquals(setOf(1), costliestCorners(reports(30, -10)))
        assertEquals(emptySet(), costliestCorners(reports(-30, -10, 0)))
    }

    @Test
    fun lossBarsAreRelativeToTheLargestAmount() {
        val r = reports(400, -200, 800)
        assertEquals(listOf(0.5f, 0.25f, 1f), r.map { lossBarFraction(it, r) })
        val none = reports(0, 0)
        assertEquals(listOf(0f, 0f), none.map { lossBarFraction(it, none) })
    }

    @Test
    fun scrollOnlyAsFarAsTheSelectedRowNeeds() {
        // viewport 0..400 px
        assertNull(scrollToShow(rowTop = 100, rowBottom = 160, current = 0, viewport = 400))
        assertEquals(260, scrollToShow(rowTop = 600, rowBottom = 660, current = 0, viewport = 400))
        assertEquals(50, scrollToShow(rowTop = 50, rowBottom = 110, current = 200, viewport = 400))
        // a row taller than the viewport shows its top
        assertEquals(600, scrollToShow(rowTop = 600, rowBottom = 1100, current = 0, viewport = 400))
    }
}
