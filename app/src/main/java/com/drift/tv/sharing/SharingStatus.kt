package com.drift.tv.sharing

/**
 * Drift's own view of the internet-sharing ("Web Indexing") state.
 *
 * Deliberately not a Bright SDK type: the store flavor doesn't have the SDK
 * on its classpath at all, so any shared UI that referenced an SDK type would
 * simply fail to compile there. The sideload flavor's BrightManager maps its
 * consent choice onto this; the store flavor never produces anything but
 * [Off].
 *
 * Only two states, unlike Pawns' equivalent: Bright exposes a binary
 * peer/not-peer consent choice with no separate "running but degraded" signal
 * to surface (no low-battery pause, no per-attempt error reason).
 */
sealed interface SharingStatus {
    /** Not sharing, and nothing is wrong — the normal idle state. */
    data object Off : SharingStatus

    /** Consent given; registered as a peer. */
    data object Active : SharingStatus
}
