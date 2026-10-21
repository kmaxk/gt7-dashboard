package de.stiller.gt7.ui

import de.stiller.gt7.session.Corner
import de.stiller.gt7.session.CornerReport
import kotlin.math.abs

/** The next ([direction] > 0) or previous corner; without a selection the first or last; stops at both ends. */
fun stepCorner(corners: List<Corner>, selected: Corner?, direction: Int): Corner? {
    if (corners.isEmpty()) return null
    val i = corners.indexOf(selected)
    if (i < 0) return if (direction > 0) corners.first() else corners.last()
    return corners[(i + direction.coerceIn(-1, 1)).coerceIn(0, corners.lastIndex)]
}

/** Numbers of the [count] corners that lose the most time; corners without a loss never count. */
fun costliestCorners(reports: List<CornerReport>, count: Int = 3): Set<Int> =
    reports.filter { it.lossMs > 0 }.sortedByDescending { it.lossMs }.take(count).map { it.corner.number }.toSet()

/** Length of the loss bar, 0–1, relative to the largest loss or gain of all corners. */
fun lossBarFraction(report: CornerReport, reports: List<CornerReport>): Float {
    val max = reports.maxOfOrNull { abs(it.lossMs) } ?: 0
    return if (max == 0) 0f else abs(report.lossMs).toFloat() / max
}
