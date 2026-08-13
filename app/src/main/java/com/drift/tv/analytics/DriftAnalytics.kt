package com.drift.tv.analytics

import android.content.Context
import android.util.Log
import com.drift.tv.BuildConfig
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig

/**
 * Thin wrapper around PostHog so the rest of the app never touches the SDK
 * directly — call sites just say what happened, not which vendor is
 * listening. If no API key is configured (see local.properties), every call
 * here is a silent no-op instead of a crash, so a fresh clone still builds
 * and runs with analytics simply off.
 *
 * What this buys you, concretely:
 * - DAU/WAU/MAU and retention: free from `captureApplicationLifecycleEvents`
 *   below — PostHog derives these from "Application Opened" events without
 *   any extra code.
 * - Everything else (which sounds get played, how mixes are built, whether
 *   people use the sleep timer or lights-out) comes from the explicit
 *   `event()` calls this class exposes, fired from PlaybackService — the one
 *   place all playback state already flows through.
 *
 * Screens are tracked manually (`screen()`) rather than via PostHog's
 * built-in Activity-based auto capture: Drift is a single Activity with two
 * Compose-level screens, so Activity-based tracking would only ever emit one
 * "screen" for the whole app.
 */
object DriftAnalytics {

    private const val TAG = "DriftAnalytics"
    private var enabled = false

    fun init(context: Context) {
        val apiKey = BuildConfig.POSTHOG_API_KEY
        if (apiKey.isBlank()) {
            Log.i(TAG, "No posthog.apiKey in local.properties — analytics disabled.")
            return
        }
        val config = PostHogAndroidConfig(
            apiKey = apiKey,
            host = BuildConfig.POSTHOG_HOST,
        ).apply {
            // Gives DAU/session data automatically (see class doc).
            captureApplicationLifecycleEvents = true
            // Single-Activity app — see class doc. Manual screen() calls
            // stand in for this instead.
            captureScreenViews = false
            // This is a sleep app; a recording of a screen that's dimmed to
            // black for hours (by design) is both useless and needlessly
            // invasive to capture even opt-in, so it stays off.
            sessionReplay = false
        }
        PostHogAndroid.setup(context, config)
        enabled = true
    }

    /** Manual stand-in for screen tracking — see class doc for why. */
    fun screen(name: String, properties: Map<String, Any?> = emptyMap()) {
        if (!enabled) return
        PostHog.screen(name, properties.filterValuesNotNull())
    }

    fun event(name: String, properties: Map<String, Any?> = emptyMap()) {
        if (!enabled) return
        PostHog.capture(name, properties = properties.filterValuesNotNull())
    }

    private fun Map<String, Any?>.filterValuesNotNull(): Map<String, Any> =
        mapNotNull { (k, v) -> v?.let { k to it } }.toMap()
}
