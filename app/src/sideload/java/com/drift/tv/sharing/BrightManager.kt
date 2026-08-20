package com.drift.tv.sharing

import android.app.Activity
import android.content.Context
import com.android.eapx.BrightApi
import com.android.eapx.CustomConsentSettings
import com.android.eapx.Settings
import com.drift.tv.BuildConfig
import com.drift.tv.ui.theme.AccentViolet
import com.drift.tv.ui.theme.MoonDim
import com.drift.tv.ui.theme.MoonWhite
import com.drift.tv.ui.theme.Panel
import com.drift.tv.ui.theme.Void
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * SIDELOAD FLAVOR ONLY — the real Bright SDK ("Web Indexing") integration.
 *
 * The store flavor has its own no-op copy of this object with an identical
 * API (`src/store/.../BrightManager.kt`), and the dependency is declared
 * `sideloadImplementation`, so the SDK is never compiled into store builds at
 * all — see app/build.gradle.kts.
 *
 * Within this flavor, it's still off unless `bright.appId` is set in
 * local.properties: [available] is false and every call no-ops, so a fresh
 * clone doesn't ship a half-configured sharing feature.
 *
 * ### Why this doesn't look like PawnsManager
 *
 * Bright's API has no `setConsentGiven(boolean)` — per their own docs, "There
 * is no way to directly opt in. Only the user can decide. You must show the
 * consent screen." Opting in only ever happens inside Bright's own consent
 * screen (`showConsent`), which is restyled here via [CustomConsentSettings]
 * to match Drift's theme rather than replaced with a custom Compose dialog
 * the way Pawns' was — that path doesn't exist for this SDK. Opting *out*
 * remains a direct call ([optOut]).
 *
 * There's also no boot receiver or watchdog worker here, unlike Pawns: the
 * SDK's own AAR manifest already declares its own boot/update receiver, its
 * own foreground service, and its own JobScheduler-bound watchdog service —
 * see app/src/sideload/AndroidManifest.xml.
 */
object BrightManager {

    val available: Boolean = BuildConfig.BRIGHT_APP_ID.isNotBlank()

    private val _status = MutableStateFlow<SharingStatus>(SharingStatus.Off)

    // init() must run exactly once per process — see [MainActivity]'s guard
    // on savedInstanceState — but that guard alone doesn't protect against
    // the Compose recomposition / multiple-entry-point calls this object is
    // also reachable from, hence a second latch here.
    private var initialized = false

    /**
     * Configures the SDK. Call once, from `MainActivity.onCreate` guarded on
     * `savedInstanceState == null` per Bright's own guidance — see that call
     * site for why.
     *
     * `setSkipConsent(true)`: the consent screen is never shown automatically
     * here. Drift decides when to show it (first-run prompt, or "Review what
     * this shares" in Settings) via explicit [showConsent] calls, matching
     * the same on-open-prompt-once product decision Pawns used.
     */
    fun init(activity: Activity) {
        if (!available || initialized) return
        initialized = true

        val settings = Settings(activity)
        settings.setSkipConsent(true)
        settings.setBenefit("To play Drift with fewer interruptions")
        settings.setOnStatusChange { choice: Int ->
            _status.value = choice.toSharingStatus()
        }
        applyDriftTheme(settings)

        BrightApi.init(activity, settings)
    }

    /** Shows Bright's own consent screen, styled via [applyDriftTheme]. */
    fun showConsent(activity: Activity) {
        if (!available) return
        BrightApi.showConsent(activity)
    }

    fun hasConsent(context: Context): Boolean =
        available && BrightApi.getConsentChoice(context) == true

    /** Revokes consent. There is no programmatic opposite — see the class doc. */
    fun optOut(context: Context) {
        if (!available) return
        BrightApi.optOut(context)
        _status.value = SharingStatus.Off
    }

    /** True while the current process is Bright's own SDK service process. */
    fun isSvcProcess(): Boolean = available && BrightApi.isSvcProcess()

    /**
     * Live status. Off until the user has actually agreed inside the consent
     * screen — Bright exposes only a binary peer/not-peer choice, so unlike
     * Pawns there's no separate LowBattery/Error state to surface here.
     */
    fun sharingStatus(): StateFlow<SharingStatus>? = if (available) _status else null

    // Choice is a plain Java class of `public static final int` constants,
    // not an enum — hence Int here rather than a Kotlin `when` over a type.
    private fun Int.toSharingStatus(): SharingStatus = when (this) {
        BrightApi.Choice.PEER -> SharingStatus.Active
        else -> SharingStatus.Off // NOT_PEER or NONE
    }

    /** Restyles Bright's built-in consent screen to match Drift's dark theme. */
    private fun applyDriftTheme(settings: Settings) {
        val ccs = CustomConsentSettings()
        ccs.setConsentTitle("Help Keep Drift Free")
        ccs.setScreenBackgroundColor(colorHex(Void))
        ccs.setBodyBackgroundColor(colorHex(Panel))
        ccs.setConsentTextColor(colorHex(MoonWhite))
        ccs.setPrivacyMessageTextColor(colorHex(MoonDim))
        ccs.setAgreeButtonBackgroundColor(colorHex(AccentViolet))
        ccs.setAgreeButtonTextColor(colorHex(MoonWhite))
        ccs.setDisagreeButtonBackgroundColor("#00000000")
        ccs.setDisagreeButtonTextColor(colorHex(MoonDim))
        settings.setCustomConsentSettings(ccs)
    }

    private fun colorHex(color: Color): String =
        "#%06X".format(color.toArgb() and 0xFFFFFF)
}
