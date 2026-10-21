package de.stiller.gt7.android

import android.app.Application
import de.stiller.gt7.storage.initPlatform

/** Runs before any activity or view model, also after Android restarted the process. */
class Gt7Application : Application() {
    override fun onCreate() {
        super.onCreate()
        initPlatform(this)
    }
}
