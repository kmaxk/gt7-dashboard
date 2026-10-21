package de.bavaco.gt7.storage

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import de.bavaco.gt7.db.Gt7Database

private lateinit var context: Context

/** Hands the application context to the platform layer; called once from the Application's onCreate. */
fun initPlatform(context: Context) {
    de.bavaco.gt7.storage.context = context.applicationContext
}

actual fun createDriver(): SqlDriver = AndroidSqliteDriver(Gt7Database.Schema, context, "gt7.db")

actual fun appDataDir(): String = context.filesDir.absolutePath

// an absolute name is used as is by Context.getDatabasePath
actual fun openDatabaseFile(path: String): SqlDriver = AndroidSqliteDriver(Gt7Database.Schema, context, path)
