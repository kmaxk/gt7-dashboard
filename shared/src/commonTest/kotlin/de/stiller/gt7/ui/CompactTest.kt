package de.stiller.gt7.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompactTest {
    @Test fun narrowScreensAreCompact() {
        assertTrue(isCompact(800f, 600f))
        assertTrue(isCompact(839.9f, 600f))
        assertFalse(isCompact(840f, 600f))
        assertFalse(isCompact(1280f, 800f)) // tablet in landscape
    }

    @Test fun largePhonesInLandscapeAreCompactByHeight() {
        assertTrue(isCompact(997f, 448f)) // Pixel 8 Pro
        assertTrue(isCompact(915f, 411f))
        assertFalse(isCompact(997f, 480f))
    }

    @Test fun tabRowHidesOnlyInCompactLive() {
        assertFalse(tabRowVisible(compact = true, liveTab = true, expanded = false))
        assertTrue(tabRowVisible(compact = true, liveTab = true, expanded = true))
        assertTrue(tabRowVisible(compact = true, liveTab = false, expanded = false))
        assertTrue(tabRowVisible(compact = false, liveTab = true, expanded = false))
    }
}
