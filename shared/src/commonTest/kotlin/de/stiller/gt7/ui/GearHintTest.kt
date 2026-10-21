package de.stiller.gt7.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GearHintTest {
    @Test fun hintWhenSuggestionDiffers() {
        assertEquals(3, gearHint(4, 3))
    }

    @Test fun noHintInSuggestedGear() {
        assertNull(gearHint(3, 3))
    }

    @Test fun noHintWithoutSuggestion() {
        assertNull(gearHint(4, 15))
        assertNull(gearHint(4, 0))
        assertNull(gearHint(null, 3))
    }
}
