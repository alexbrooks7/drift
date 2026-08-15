package com.drift.tv.sharing

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * STORE FLAVOR ONLY — a no-op stand-in with the same API as the sideload
 * flavor's real implementation.
 *
 * Google Play's Device and Network Abuse policy and the Amazon Appstore's
 * equivalent both prohibit SDKs that route third-party traffic through a
 * user's connection, so store builds must not contain one. The Pawns
 * dependency is declared `sideloadImplementation`, which means for this
 * flavor the SDK isn't on the classpath and its native relay libraries are
 * never packaged — not merely disabled, absent.
 *
 * This file exists so the shared UI compiles unchanged. With [available]
 * false, Home hides the settings entry, the consent prompt never appears, and
 * nothing here ever runs.
 */
object PawnsManager {

    /** Always false: there is no sharing SDK in this build. */
    val available: Boolean = false

    fun init(context: Context) = Unit

    fun hasConsent(): Boolean = false

    fun setConsentGiven(given: Boolean) = Unit

    /** Null, matching "not configured" — callers treat it as off. */
    fun sharingStatus(): Flow<SharingStatus>? = null

    fun startSharing(context: Context) = Unit

    fun stopSharing(context: Context) = Unit
}
