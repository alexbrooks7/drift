package com.drift.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drift.tv.ui.DriftViewModel
import com.drift.tv.ui.HomeScreen
import com.drift.tv.ui.PlayerScreen
import com.drift.tv.ui.theme.DriftTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Immersive: hide system bars so "lights out" is edge-to-edge black.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent { DriftTheme { DriftApp() } }
    }

    /** Called from Compose when lights-out mode toggles. Keeping the screen on
     *  prevents the system screensaver/ambient mode from replacing our black view. */
    fun setKeepScreenOn(keep: Boolean) {
        if (keep) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
private fun DriftApp(vm: DriftViewModel = viewModel()) {
    val layers by vm.layers.collectAsState()
    val timer by vm.timer.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    var onPlayerScreen by remember { mutableStateOf(false) }
    val activity = androidx.compose.ui.platform.LocalContext.current as MainActivity

    if (onPlayerScreen && layers.isNotEmpty()) {
        PlayerScreen(
            layers = layers,
            catalog = vm.catalog,
            timer = timer,
            isPlaying = isPlaying,
            onTogglePlayPause = vm::togglePlayPause,
            onSetTimer = vm::setSleepTimer,
            onAddLayer = vm::addLayer,
            onRemoveLayer = vm::removeLayer,
            onLayerVolume = vm::setLayerVolume,
            onDimChanged = { dimmed -> activity.setKeepScreenOn(dimmed) },
            onBack = { onPlayerScreen = false },
        )
    } else {
        HomeScreen(sounds = vm.catalog, onSelect = { sound ->
            vm.play(sound)
            onPlayerScreen = true
        })
    }
}
