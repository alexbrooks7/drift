package com.drift.tv.sharing

import android.content.Context
import android.util.Log
import com.drift.tv.BuildConfig
import com.drift.tv.R
import com.pawns.sdk.common.dto.ServiceConfig
import com.pawns.sdk.common.dto.ServiceNotificationPriority
import com.pawns.sdk.common.dto.ServiceState
import com.pawns.sdk.common.dto.ServiceType
import com.pawns.sdk.common.sdk.Pawns
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * SIDELOAD FLAVOR ONLY — the real Pawns.app bandwidth-sharing integration.
 *
 * The store flavor has its own no-op copy of this object with an identical
 * API (`src/store/.../PawnsManager.kt`), and the Pawns dependency is declared
 * `sideloadImplementation`, so the SDK and its ~65MB of native relay
 * libraries are never compiled into store builds at all. That's deliberate:
 * Google Play and the Amazon Appstore both prohibit this class of SDK, and a
 * blank API key would only disable it at runtime — the native libraries would
 * still be sitting in the APK for a reviewer to find.
 *
 * Within this flavor, it's still off unless `pawns.apiKey` is set in
 * local.properties: [available] is false and every call no-ops, so a fresh
 * clone doesn't ship a half-configured sharing feature.
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
            .loggerEnabled(BuildConfig.DEBUG)
            // Foreground, so a persistent notification stays visible the whole
            // time sharing is active — see the manifest comment on the service.
            .serviceType(ServiceType.FOREGROUND)
            .build()
    }

    fun hasConsent(): Boolean = available && Pawns.getInstance().isConsentGiven()

    fun setConsentGiven(given: Boolean) {
        if (!available) return
        Pawns.getInstance().setConsentGiven(given)
    }

    /**
     * Live status, mapped off the SDK's own service-state flow. Null when the
     * feature isn't configured — callers treat that as "off".
     *
     * Reading real service state rather than the consent flag is load-bearing:
     * consent being granted doesn't prove the relay is running. On real
     * hardware this is what surfaced `This IP is already in use` when another
     * sharing app had claimed the address.
     */
    fun sharingStatus(): Flow<SharingStatus>? =
        if (available) Pawns.getInstance().getServiceState().map { it.toSharingStatus() } else null

    fun startSharing(context: Context) {
        if (!available) return
        Pawns.getInstance().startSharing(context)
    }

    fun stopSharing(context: Context) {
        if (!available) return
        Pawns.getInstance().stopSharing(context)
    }

    private fun ServiceState.toSharingStatus(): SharingStatus = when (this) {
        is ServiceState.Off -> SharingStatus.Off
        is ServiceState.On -> SharingStatus.Active
        is ServiceState.Launched.Running -> SharingStatus.Active
        is ServiceState.Launched.LowBattery -> SharingStatus.LowBattery
        is ServiceState.Launched.Error -> SharingStatus.Error(error.toString())
    }
}
