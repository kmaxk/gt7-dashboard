package de.bavaco.gt7.ui

import de.bavaco.gt7.storage.CarInfo
import de.bavaco.gt7.storage.DrivenCar
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class CarsLogicTest {
    @Test fun carCodeMustBePositiveNumber() {
        assertEquals(3332, parseCarCode(" 3332 "))
        assertEquals(null, parseCarCode("0"))
        assertEquals(null, parseCarCode("-4"))
        assertEquals(null, parseCarCode("abc"))
        assertEquals(null, parseCarCode(""))
    }

    @Test fun originNamesTheSource() {
        assertEquals("Liste · Toyota", carOrigin(CarInfo(1, "GR Supra", "GR Supra", "Toyota", custom = false)))
        assertEquals("eigener Name · Liste: GR Supra", carOrigin(CarInfo(1, "Supra", "GR Supra", "Toyota", custom = true)))
        assertEquals("eigener Name", carOrigin(CarInfo(7, "Eigenbau", null, null, custom = true)))
        assertEquals("unbekannt – bitte benennen", carOrigin(CarInfo(7, "Auto #7", null, null, custom = false)))
    }

    @Test fun drivenFacts() {
        // 2026-03-05T07:08:09Z
        assertEquals("ID 12 · 3 Runden · zuletzt 05.03.2026 07:08", drivenCarFacts(DrivenCar(12, 3, 1_772_694_489_000L), TimeZone.UTC))
        assertEquals("ID 12 · 1 Runde · zuletzt 05.03.2026 07:08", drivenCarFacts(DrivenCar(12, 1, 1_772_694_489_000L), TimeZone.UTC))
    }
}
