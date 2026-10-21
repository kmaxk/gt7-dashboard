package de.stiller.gt7.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import de.stiller.gt7.db.Gt7Database
import java.io.File
import java.util.Properties

private const val APP_DIR = "GT7Dashboard"

/** Per-user data directory: %APPDATA% on Windows, Application Support on macOS. */
internal fun appDataPath(osName: String, userHome: String, appData: String?): File =
    if (osName.startsWith("Windows")) {
        if (appData != null) File(appData, APP_DIR) else File(userHome, "AppData/Roaming/$APP_DIR")
    } else {
        File(userHome, "Library/Application Support/$APP_DIR")
    }

actual fun appDataDir(): String {
    val dir = appDataPath(System.getProperty("os.name"), System.getProperty("user.home"), System.getenv("APPDATA"))
    dir.mkdirs()
    return dir.absolutePath
}

actual fun createDriver(): SqlDriver =
    JdbcSqliteDriver("jdbc:sqlite:${File(appDataDir(), "gt7.db").path}", Properties(), Gt7Database.Schema)

actual fun openDatabaseFile(path: String): SqlDriver = JdbcSqliteDriver("jdbc:sqlite:$path", Properties(), Gt7Database.Schema)
