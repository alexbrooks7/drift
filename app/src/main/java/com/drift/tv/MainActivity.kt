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
import com.drift.tv.sharing.PawnsManager
import com.drift.tv.ui.ConsentDialog
import com.drift.tv.ui.DriftViewModel
import com.drift.tv.ui.HomeScreen
import com.drift.tv.ui.PlayerScreen
import com.drift.tv.ui.SettingsScreen
import com.drift.tv.ui.theme.DriftTheme
import com.drift.tv.work.SharingWatchdogScheduler
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
    val watchdog = remember { SharingWatchdogScheduler(context.applicationContext) }

    // Consent is asked on app open, as an overlay on Home rather than a
    // separate Activity — see ConsentDialog for why the SDK's bundled screen
    // isn't used here.
    var showConsent by remember { mutableStateOf(false) }
    // Same dialog serves two entry points, and BACK has to mean different
    // things in each: dismissing the first-run prompt is an answer (the safe
    // one — don't share), while dismissing a review from Settings must leave
    // an existing choice untouched.
    var consentIsReview by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!PawnsManager.available || PawnsManager.hasConsent()) return@LaunchedEffect
        // Ask once, not every launch. The SDK stores only a binary "consent
        // given", so `isConsentGiven()` reads the same false whether the
        // prompt was declined or never shown — auto-prompting off it alone
        // re-asks on every open forever after a decline. Opting in later is
        // still available from the sharing screen's START button.
        if (Prefs.pawnsConsentAsked(context)) return@LaunchedEffect
        showConsent = true
    }

    // Resume sharing that was already switched on in an earlier session.
    //
    // The SDK's service doesn't survive the process, so without this the
    // feature only ever ran in the session where it was turned on: accept the
    // dialog, close the app, and every launch afterward silently shares
    // nothing while Settings honestly reports "Off". Consent is still
    // granted, so nothing re-asks and nothing looks wrong.
    //
    // Gated on Prefs.sharingEnabled, not hasConsent() alone — see that
    // property's doc for why resuming off consent would overturn a
    // deliberate opt-out on every launch.
    LaunchedEffect(Unit) {
        if (!PawnsManager.available) return@LaunchedEffect
        val enabled = Prefs.sharingEnabled(context)
        if (enabled && PawnsManager.hasConsent()) PawnsManager.startSharing(context)
        // Reconcile the recovery watchdog against the stored preference on
        // every start, not only when the setting changes. A periodic job can
        // be dropped — by a Force Stop, "clear data", a vendor task manager —
        // and this is the cheapest place to notice: enqueueing is idempotent
        // under KEEP, so a schedule that already exists is left alone rather
        // than pushed further out.
        watchdog.sync(enabled)
    }

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
            onOpenSettings = if (PawnsManager.available) {
                { screen = Screen.Settings }
            } else null,
        )
    }

    // Drawn last so it sits above Home, matching the reference's dialog-over-
    // content layout. Either answer is recorded so the prompt isn't shown again.
    if (showConsent) {
        ConsentDialog(
            onAccept = {
                showConsent = false
                consentIsReview = false
                PawnsManager.setConsentGiven(true)
                PawnsManager.startSharing(context)
                watchdog.sync(true)
                scope.launch {
                    Prefs.setPawnsConsentAsked(context)
                    Prefs.setSharingEnabled(context, true)
                }
            },
            onDecline = {
                showConsent = false
                consentIsReview = false
                PawnsManager.setConsentGiven(false)
                // Also reached via Settings → "Review what this shares", where
                // sharing may be running right now — withdrawing consent has
                // to actually stop it, not just clear the flag. No-op when
                // it isn't running.
                PawnsManager.stopSharing(context)
                watchdog.sync(false)
                scope.launch {
                    Prefs.setPawnsConsentAsked(context)
                    Prefs.setSharingEnabled(context, false)
                }
            },
            onDismiss = {
                showConsent = false
                if (!consentIsReview) {
                    // BACK out of the first-run prompt without choosing: record
                    // the privacy-safe answer rather than leaving it unanswered
                    // and re-asking every launch. Opting in later is one press
                    // away in Settings.
                    PawnsManager.setConsentGiven(false)
                    watchdog.sync(false)
                    scope.launch {
                        Prefs.setPawnsConsentAsked(context)
                        Prefs.setSharingEnabled(context, false)
                    }
                }
                consentIsReview = false
            },
        )
    }
}
