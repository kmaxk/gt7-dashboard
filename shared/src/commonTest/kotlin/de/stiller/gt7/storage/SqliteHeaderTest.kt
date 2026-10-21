package de.stiller.gt7.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SqliteHeaderTest {
    @Test fun readsUserVersionAtOffset60() {
        val h = "SQLite format 3\u0000".encodeToByteArray() + ByteArray(84)
        h[60] = 0; h[61] = 0; h[62] = 1; h[63] = 4 // big-endian 260
        assertEquals(260, sqliteUserVersion(h))
    }

    @Test fun rejectsOtherFiles() {
        assertNull(sqliteUserVersion(ByteArray(0)))
        assertNull(sqliteUserVersion("SQLite format 3\u0000".encodeToByteArray())) // shorter than 64 bytes
        assertNull(sqliteUserVersion("das ist keine Datenbank".repeat(10).encodeToByteArray()))
    }
}
