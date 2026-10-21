package de.stiller.gt7.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class DashboardScaleTest {
    @Test fun referenceSizeIsOne() {
        assertEquals(1f, dashboardScale(1000f, 640f))
    }

    @Test fun smallWindowNeverShrinks() {
        assertEquals(1f, dashboardScale(800f, 500f))
    }

    @Test fun macBookFullscreenUsesLimitingSide() {
        // 1512×982 minus 48dp tab row → height limits: 934 / 640
        assertEquals(934f / 640f, dashboardScale(1512f, 934f), 0.001f)
    }

    @Test fun hugeScreenIsCapped() {
        assertEquals(2.5f, dashboardScale(5000f, 3000f))
    }

    @Test fun phoneShrinksToFit() = assertEquals(360f / 640f, dashboardScale(800f, 360f, compact = true), 0.001f)

    @Test fun phoneShrinkIsLimited() = assertEquals(0.45f, dashboardScale(400f, 200f, compact = true))

    @Test fun desktopStillNeverShrinks() = assertEquals(1f, dashboardScale(800f, 360f))
}
