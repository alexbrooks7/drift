package com.drift.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drift.tv.analytics.DriftAnalytics
import com.drift.tv.data.Prefs
import com.drift.tv.sharing.BrightManager
import com.drift.tv.ui.ConsentDialog
import com.drift.tv.ui.DriftViewModel
import com.drift.tv.ui.HomeScreen
import com.drift.tv.ui.PlayerScreen
import com.drift.tv.ui.SettingsScreen
import com.drift.tv.ui.theme.DriftTheme
import kotlinx.coroutines.launch

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

        // Per Bright's own integration guide: init() must run exactly once,
        // guarded on savedInstanceState rather than called from Application
        // (unlike Pawns, this SDK's init takes an Activity, not a bare
        // Context — it needs one to eventually host its consent screen).
        // No-op in the store flavor and whenever bright.appId is unset.
        if (savedInstanceState == null) BrightManager.init(this)

        setContent { DriftTheme { DriftRoot() } }
    }

    /** Called from Compose when lights-out mode toggles. Keeping the screen on
     *  prevents the system screensaver/ambient mode from replacing our black view. */
    fun setKeepScreenOn(keep: Boolean) {
        if (keep) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

private enum class Screen { Home, Player, Settings }

// NB: must not be named DriftApp — that collides with the DriftApp Application
// class in this same package, and Kotlin resolves the no-arg call to the class
// constructor (it needs no default args), silently emitting an empty UI.
@Composable
private fun DriftRoot(vm: DriftViewModel = viewModel()) {
    val layers by vm.layers.collectAsState()
    val timer by vm.timer.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    val lastMix by vm.lastMix.collectAsState()
    var screen by remember { mutableStateOf(Screen.Home) }
    val activity = LocalContext.current as MainActivity
    val context = LocalContext.current

    // Consent is asked on app open, as an overlay on Home rather than a
    // separate Activity — see ConsentDialog for why. Unlike Pawns, this isn't
    // the actual consent capture: it's a priming screen that leads into
    // Bright's own consent screen, which is the only place a "yes" can
    // actually be recorded — see BrightManager's class doc for why.
    var showConsent by remember { mutableStateOf(false) }
    // Same dialog serves two entry points, and BACK has to mean different
    // things in each: dismissing the first-run prompt is an answer (the safe
    // one — don't share), while dismissing a review from Settings must leave
    // an existing choice untouched.
    var consentIsReview by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!BrightManager.available || BrightManager.hasConsent(context)) return@LaunchedEffect
        // Ask once, not every launch — see Prefs.brightConsentAsked for why
        // checking the SDK's own choice alone isn't enough. Opting in later
        // is still available from "Review what this shares" in Settings.
        if (Prefs.brightConsentAsked(context)) return@LaunchedEffect
        showConsent = true
    }

    // Nothing to resume here, unlike Pawns: Bright's SDK persists its own
    // peer state and resumes it itself after a reboot, an app update, or a
    // process kill — own boot receiver, own foreground service, own
    // JobScheduler watchdog, all declared in its AAR's own manifest. See
    // app/src/sideload/AndroidManifest.xml.

    // Coming back to the app while it's still playing should land on the player,
    // not make the user re-pick a sound that's already running.
    LaunchedEffect(layers.isNotEmpty()) {
        if (layers.isNotEmpty() && screen == Screen.Home) screen = Screen.Player
    }

    // Single-Activity app, so there's no natural per-screen hook to attach
    // automatic screen tracking to — this stands in for it. See DriftAnalytics.
    LaunchedEffect(screen) {
        DriftAnalytics.screen(screen.name.lowercase())
    }

    val scope = rememberCoroutineScope()

    when {
        screen == Screen.Player && layers.isNotEmpty() -> PlayerScreen(
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
            onBack = {
                // Back to the sound picker means the person is done with
                // this mix, not just glancing away — stop playback rather
                // than leaving it running silently behind the menu. The
                // "Resume last mix" card on Home still works afterward:
                // stop() clears the live layers but doesn't touch the
                // persisted mix Prefs remembers.
                vm.stop()
                screen = Screen.Home
            },
        )

        screen == Screen.Settings -> SettingsScreen(
            onOpenConsent = { consentIsReview = true; showConsent = true },
            onBack = { screen = Screen.Home },
        )

        else -> HomeScreen(
            sounds = vm.catalog,
            lastMix = lastMix,
            onSelect = { sound ->
                vm.play(sound)
                screen = Screen.Player
            },
            onResumeMix = {
                vm.resumeLastMix()
                screen = Screen.Player
            },
            onOpenSettings = if (BrightManager.available) {
                { screen = Screen.Settings }
            } else null,
        )
    }

    // Drawn last so it sits above Home, matching the reference's dialog-over-
    // content layout. "Okay" doesn't grant consent itself — it hands off to
    // Bright's own (restyled) consent screen, where the actual choice is
    // made; "No thanks" and a first-run BACK both record a decline directly,
    // since opting out is a plain API call unlike opting in.
    if (showConsent) {
        ConsentDialog(
            onAccept = {
                showConsent = false
                consentIsReview = false
                scope.launch { Prefs.setBrightConsentAsked(context) }
                BrightManager.showConsent(activity)
            },
            onDecline = {
                showConsent = false
                consentIsReview = false
                BrightManager.optOut(context)
                scope.launch { Prefs.setBrightConsentAsked(context) }
            },
            onDismiss = {
                showConsent = false
                if (!consentIsReview) {
                    // BACK out of the first-run prompt without choosing: record
                    // the privacy-safe answer rather than leaving it unanswered
                    // and re-asking every launch. Opting in later is one press
                    // away in Settings.
                    BrightManager.optOut(context)
                    scope.launch { Prefs.setBrightConsentAsked(context) }
                }
                consentIsReview = false
            },
        )
    }
}
