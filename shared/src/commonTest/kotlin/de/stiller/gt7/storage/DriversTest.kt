package de.stiller.gt7.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DriversTest {
    @Test fun trimsAndRejectsDuplicates() {
        assertEquals("Anna", validateDriverName("  Anna ", emptyList()))
        assertNull(validateDriverName("anna", listOf(Driver(1, "Anna"))))
        assertNull(validateDriverName("   ", emptyList()))
    }
}
