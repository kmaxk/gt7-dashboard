package de.stiller.gt7.storage

private val MAGIC = "SQLite format 3\u0000".encodeToByteArray()

/** `user_version` from the first bytes of a SQLite file (big-endian at offset 60); null if [header] is no SQLite header. */
fun sqliteUserVersion(header: ByteArray): Int? {
    if (header.size < 64 || !header.copyOf(MAGIC.size).contentEquals(MAGIC)) return null
    return (60 until 64).fold(0) { v, i -> (v shl 8) or (header[i].toInt() and 0xFF) }
}
