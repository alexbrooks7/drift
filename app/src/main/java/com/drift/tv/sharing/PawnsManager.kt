package com.drift.tv.sharing

import android.content.Context
import android.content.Intent
import android.util.Log
import com.drift.tv.BuildConfig
import com.drift.tv.R
import com.pawns.sdk.common.dto.ServiceConfig
import com.pawns.sdk.common.dto.ServiceNotificationPriority
import com.pawns.sdk.common.dto.ServiceState
import com.pawns.sdk.common.dto.ServiceType
import com.pawns.sdk.common.sdk.Pawns
import kotlinx.coroutines.flow.Flow

/**
 * Thin wrapper around the Pawns.app bandwidth-sharing SDK — see README's
 * "Internet Sharing" section for what this feature does.
 *
 * Exposes the same primitives the official demo app calls
 * (github.com/pawns-app/android-pawns-sdk-demo), so the consent flow can be
 * tested against the SDK's own bundled consent Activity
 * ([consentIntent]) rather than a custom reimplementation.
 *
 * Same no-op-if-unconfigured pattern as DriftAnalytics: with no
 * `pawns.apiKey` in local.properties, [available] is false and every call
 * here does nothing — no consent prompt, no SDK service, nothing shown.
 */
object PawnsManager {

    private const val TAG = "PawnsManager"

    val available: Boolean = BuildConfig.PAWNS_API_KEY.isNotBlank()

    fun init(context: Context) {
        if (!available) {
            Log.i(TAG, "No pawns.apiKey in local.properties — bandwidth sharing unavailable.")
            return
        }
        Pawns.Builder(context)
            .apiKey(BuildConfig.PAWNS_API_KEY)
            .serviceConfig(
                ServiceConfig(
                    title = R.string.pawns_service_name,
                    body = R.string.pawns_service_body,
                    smallIcon = R.drawable.ic_sharing,
                    notificationPriority = ServiceNotificationPriority.HIGH,
                )
            )
            .loggerEnabled(true)
            .serviceType(ServiceType.FOREGROUND)
            .build()
    }

    /** Has the person already answered the consent prompt affirmatively? */
    fun hasConsent(): Boolean = available && Pawns.getInstance().isConsentGiven()

    /**
     * The SDK's own consent screen, launched via an ActivityResult contract.
     * RESULT_OK means consent was granted. Null when the feature isn't
     * configured.
     */
    fun consentIntent(): Intent? =
        if (available) Pawns.getInstance().getConsentIntent() else null

    fun setConsentGiven(given: Boolean) {
        if (!available) return
        Pawns.getInstance().setConsentGiven(given)
    }

    /** Null when the feature isn't configured — UI should treat that as "off". */
    fun serviceState(): Flow<ServiceState>? =
        if (available) Pawns.getInstance().getServiceState() else null

    fun startSharing(context: Context) {
        if (!available) return
        Pawns.getInstance().startSharing(context)
    }

    fun stopSharing(context: Context) {
        if (!available) return
        Pawns.getInstance().stopSharing(context)
    }
}
