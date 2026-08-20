package com.drift.tv.sharing

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/**
 * STORE FLAVOR ONLY — a no-op stand-in with the same API as the sideload
 * flavor's real implementation.
 *
 * Bright SDK does have a Play Store review/approval path, but that hasn't
 * been pursued for this flavor yet — see the productFlavors comment in
 * app/build.gradle.kts. The dependency is declared `sideloadImplementation`,
 * so for this flavor the SDK isn't on the classpath at all.
 *
 * This file exists so the shared UI compiles unchanged. With [available]
 * false, Home hides the settings entry, the consent prompt never appears, and
 * nothing here ever runs.
 */
object BrightManager {

    /** Always false: there is no sharing SDK in this build. */
    val available: Boolean = false

    fun init(activity: Activity) = Unit

    fun showConsent(activity: Activity) = Unit

    fun hasConsent(context: Context): Boolean = false

    fun optOut(context: Context) = Unit

    fun isSvcProcess(): Boolean = false

    /** Null, matching "not configured" — callers treat it as off. */
    fun sharingStatus(): StateFlow<SharingStatus>? = null
}
