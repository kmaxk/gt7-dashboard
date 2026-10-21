package de.bavaco.gt7.storage

import com.sun.jna.Library
import com.sun.jna.Native
import java.util.concurrent.Executors

/** Keeps the computer and its display awake while [set] is true: macOS via caffeinate, Windows via SetThreadExecutionState. */
class SleepBlocker(osName: String = System.getProperty("os.name")) {
    private val platform: Platform = when {
        osName.startsWith("Mac") -> Caffeinate()
        osName.startsWith("Windows") -> ExecutionState()
        else -> object : Platform { override fun apply(awake: Boolean) = Unit }
    }
    private var awake = false

    fun set(awake: Boolean) {
        if (awake == this.awake) return
        this.awake = awake
        try {
            platform.apply(awake)
        } catch (e: Exception) {
            println("[GT7] keeping the computer awake failed: $e")
        }
    }

    private interface Platform {
        fun apply(awake: Boolean)
    }

    /** `caffeinate -d -i` bound to this process (-w), so the assertion ends with the app even after a crash. */
    private class Caffeinate : Platform {
        private var process: Process? = null

        override fun apply(awake: Boolean) {
            process?.destroy()
            process = if (awake) {
                ProcessBuilder("/usr/bin/caffeinate", "-d", "-i", "-w", ProcessHandle.current().pid().toString()).start()
            } else {
                null
            }
        }
    }

    /** The execution state belongs to the calling thread, so one dedicated thread sets and clears it. */
    private class ExecutionState : Platform {
        private val thread = Executors.newSingleThreadExecutor { r -> Thread(r, "gt7-stay-awake").apply { isDaemon = true } }
        private val kernel32 by lazy { Native.load("kernel32", Kernel32::class.java) }

        override fun apply(awake: Boolean) {
            val flags = if (awake) ES_CONTINUOUS or ES_SYSTEM_REQUIRED or ES_DISPLAY_REQUIRED else ES_CONTINUOUS
            thread.submit { kernel32.SetThreadExecutionState(flags) }
        }

        @Suppress("FunctionName")
        private interface Kernel32 : Library {
            fun SetThreadExecutionState(flags: Int): Int
        }

        private companion object {
            const val ES_CONTINUOUS = 0x80000000.toInt()
            const val ES_SYSTEM_REQUIRED = 0x00000001
            const val ES_DISPLAY_REQUIRED = 0x00000002
        }
    }
}
