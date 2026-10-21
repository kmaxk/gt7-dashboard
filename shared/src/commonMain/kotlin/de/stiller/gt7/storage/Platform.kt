package de.stiller.gt7.storage

import app.cash.sqldelight.db.SqlDriver

expect fun createDriver(): SqlDriver

expect fun appDataDir(): String

/** Opens the SQLite file at [path] with the app schema: an empty file gets it created, an older one is migrated. */
expect fun openDatabaseFile(path: String): SqlDriver
