package com.drift.tv.sharing

/**
 * Drift's own view of the internet-sharing state.
 *
 * Deliberately not the SDK's `ServiceState`: the store flavor doesn't have
 * the Pawns SDK on its classpath at all, so any shared UI that referenced an
 * SDK type would simply fail to compile there. The sideload flavor's
 * PawnsManager maps `ServiceState` onto this; the store flavor never produces
 * anything but [Off].
 */
sealed interface SharingStatus {
    /** Not sharing, and nothing is wrong — the normal idle state. */
    data object Off : SharingStatus

    /** Registered and relaying. */
    data object Active : SharingStatus

    /** The SDK paused itself to protect the battery. */
    data object LowBattery : SharingStatus

    /** Not sharing because the SDK reported a problem. [reason] is its own wording. */
    data class Error(val reason: String) : SharingStatus
}
