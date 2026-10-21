package de.bavaco.gt7.storage

import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class FilePickerTest {
    @Test fun appendsMissingExtension() {
        assertEquals("a.gt7export", withExtension("a", "gt7export"))
        assertEquals("a.gt7export", withExtension("a.gt7export", "gt7export"))
    }

    @Test fun saveViaDeletesTempEvenOnFailure() = runTest {
        val tmp = Path(SystemTemporaryDirectory, "save-via-test.tmp")
        assertFailsWith<IllegalStateException> {
            saveVia(tmp.toString(), { SystemFileSystem.sink(Path(it)).close() }, { error("Drive offline") })
        }
        assertFalse(SystemFileSystem.exists(tmp))
    }

    @Test fun copyFileCopiesBytes() {
        val from = Path(SystemTemporaryDirectory, "copy-from.tmp")
        val to = Path(SystemTemporaryDirectory, "copy-to.tmp")
        SystemFileSystem.sink(from).buffered().use { it.write(byteArrayOf(1, 2, 3)) }
        copyFile(from.toString(), to.toString())
        assertContentEquals(byteArrayOf(1, 2, 3), SystemFileSystem.source(to).buffered().use { it.readByteArray() })
        SystemFileSystem.delete(from)
        SystemFileSystem.delete(to)
    }
}
