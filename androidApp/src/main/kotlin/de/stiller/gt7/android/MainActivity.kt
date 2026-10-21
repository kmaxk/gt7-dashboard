package de.stiller.gt7.android

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import de.stiller.gt7.storage.FilePicker
import de.stiller.gt7.ui.App

class MainActivity : ComponentActivity() {
    private val vm: DashboardViewModel by viewModels()
    private lateinit var files: FilePicker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        files = AndroidFilePicker(this)
        setContent {
            val cars by vm.cars.collectAsState()
            App(vm.controller, vm.repo, cars, vm.transfer, files, compactAllowed = true, onLiveVisible = ::liveMode)
        }
    }

    /** While driving: screen stays on, system bars hide (a swipe shows them briefly). */
    private fun liveMode(on: Boolean) {
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insets.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
