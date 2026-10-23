package de.bavaco.gt7.ui

import de.bavaco.gt7.session.TrackNotice
import de.bavaco.gt7.storage.ImportResult
import de.bavaco.gt7.telemetry.PacketStats
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsLogicTest {
    // 2026-03-05T07:08:09Z
    private val t = 1_772_694_489_000L

    @Test fun fileName() {
        assertEquals("2026-03-05_07-08-09.gt7rec", recordingFileName(t, TimeZone.UTC))
        assertEquals("2026-03-05_08-08-09.gt7rec", recordingFileName(t, TimeZone.of("Europe/Berlin")))
    }

    @Test fun trackNotice() {
        assertEquals("Strecke erkannt: Monza", trackNoticeText(TrackNotice(1, "Monza", isNew = false)))
        assertEquals("Neue Strecke angelegt: Neue Strecke 2 – umbenennen unter Strecken", trackNoticeText(TrackNotice(2, "Neue Strecke 2", isNew = true)))
    }

    @Test fun diagnostics() {
        assertEquals("58 Pakete/s · 2 verworfen", diagnosticsText(PacketStats(58, 2, null)))
        assertEquals("58 Pakete/s · 2 verworfen · 3 Schreibfehler", diagnosticsText(PacketStats(58, 2, null, 3)))
    }

    @Test fun exportName() {
        assertEquals("gt7-dashboard_2026-03-05.gt7export", exportFileName(t, TimeZone.UTC))
    }

    @Test fun importSummary() {
        assertEquals(
            "Importiert: 3 Fahrer, 2 Strecken, 41 Sitzungen (12 schon vorhanden), 5 Autonamen",
            importResultText(ImportResult(3, 2, 41, 12, 5)),
        )
        assertEquals("Importiert: 0 Fahrer, 1 Strecke, 1 Sitzung, 1 Autoname", importResultText(ImportResult(0, 1, 1, 0, 1)))
    }
}
