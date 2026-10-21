package de.stiller.gt7.storage

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class AppDataPathTest {
    @Test fun macOsUsesApplicationSupport() {
        assertEquals(
            File("/Users/max", "Library/Application Support/GT7Dashboard"),
            appDataPath("Mac OS X", "/Users/max", null),
        )
    }

    @Test fun windowsUsesAppData() {
        assertEquals(
            File("C:\\Users\\max\\AppData\\Roaming", "GT7Dashboard"),
            appDataPath("Windows 11", "C:\\Users\\max", "C:\\Users\\max\\AppData\\Roaming"),
        )
    }

    @Test fun windowsWithoutAppDataFallsBackToHome() {
        assertEquals(
            File("C:\\Users\\max", "AppData/Roaming/GT7Dashboard"),
            appDataPath("Windows 11", "C:\\Users\\max", null),
        )
    }
}
