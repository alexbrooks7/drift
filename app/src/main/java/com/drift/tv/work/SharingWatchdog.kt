package com.drift.tv.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.drift.tv.data.Prefs
import com.drift.tv.sharing.PawnsManager
import com.drift.tv.sharing.SharingStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * Last-resort recovery for internet sharing. **Read the coverage notes below
 * before relying on this — it is a backstop, not the primary mechanism.**
 *
 * The two things that actually keep sharing alive are neither of them this
 * class:
 *
 * 1. **Android restarts the peer service itself when the process is killed.**
 *    It's `START_STICKY` (SDK-internal, not something this app configures),
 *    so a low-memory or vendor reclamation kill is repaired by the platform
 *    within about a second, with no app involvement and no permission needed.
 * 2. **`SharingBootReceiver`** (sideload source set) handles reboots and app
 *    updates, because `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` are explicit
 *    exemptions to Android 12's background-foreground-service-start rule.
 *
 * What's left for this worker is the residue: the service isn't running, the
 * platform has stopped trying to bring it back (a repeatedly failing service
 * is eventually given up on), and no reboot has happened to trigger the
 * receiver. Narrow, but it's the case where sharing would otherwise stay off
 * indefinitely while Settings honestly reports "Off" to nobody looking.
 *
 * WorkManager rather than an alarm because it needs no extra permission and
 * survives Doze; the 15-minute floor below is its own, not a choice made here.
 *
 * ### What this can't do
 *
 * - **Can't beat a Force Stop.** Force-stopping the app cancels its
 *   WorkManager jobs too, by design. Recovery there happens when the app is
 *   next opened, via the resume in MainActivity.
 * - **Usually can't restart the service while the app is backgrounded on
 *   API 31+.** Starting a foreground service from the background is forbidden
 *   there, and a worker woken by WorkManager *is* the background — the call
 *   throws, caught below. It succeeds when the app is on screen, or once the
 *   user has exempted it from battery optimization (which lifts the
 *   restriction as a side effect).
 * - **Not immediate.** 15 minutes is WorkManager's own periodic floor, so this
 *   is "recovers within about a quarter hour," never "doesn't drop".
 */
class SharingWatchdogWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Store flavor has no SDK behind PawnsManager at all, and an
        // unconfigured build has no key — either way there's nothing to
        // watch. Cheap enough to re-check here rather than trusting the job
        // was never scheduled.
        if (!PawnsManager.available) return Result.success()

        val enabled = withTimeoutOrNull(SETTINGS_READ_TIMEOUT_MS) {
            Prefs.sharingEnabled(applicationContext)
        }
        // Same rule the launch-time resume follows: act on the stored
        // preference, never on hasConsent() alone, which stays true after
        // someone switches sharing off. A null read means DataStore didn't
        // answer in time — treat that as "don't touch anything", since the
        // failure mode of guessing wrong here is starting a service the user
        // turned off.
        if (enabled != true) return Result.success()
        if (!PawnsManager.hasConsent()) return Result.success()

        val status = withTimeoutOrNull(STATUS_READ_TIMEOUT_MS) {
            PawnsManager.sharingStatus()?.first()
        }
        if (status is SharingStatus.Active) return Result.success()

        // Deliberately also covers the unreadable-status case (status ==
        // null). Starting a service that's already running is a no-op the SDK
        // absorbs; not starting one that has died is the failure this class
        // exists to prevent, so ambiguity resolves toward acting.
        return try {
            Log.w(TAG, "Sharing was enabled but not running — restarting")
            PawnsManager.startSharing(applicationContext)
            Result.success()
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (API 31+) extends
            // IllegalStateException.
            //
            // Success rather than retry: this isn't a transient error, it's
            // the platform saying "not from the background". Retrying with
            // backoff would burn wakeups only to be refused every time. The
            // next periodic run tries again anyway, and by then the app may
            // be in the foreground, where it's allowed.
            Log.w(TAG, "Could not restart sharing from the background: ${e.javaClass.simpleName}")
            Result.success()
        }
    }

    private companion object {
        const val TAG = "SharingWatchdog"
        const val SETTINGS_READ_TIMEOUT_MS = 5_000L
        const val STATUS_READ_TIMEOUT_MS = 2_000L
    }
}

/**
 * Schedules and cancels [SharingWatchdogWorker].
 *
 * Every mutation of the sharing preference — the consent dialog's two
 * buttons, the Settings toggle, and the launch-time reconciliation in
 * MainActivity — should call [sync] so the schedule never drifts from what
 * Settings actually shows.
 */
class SharingWatchdogScheduler(private val context: Context) {

    /**
     * Brings the schedule in line with [enabled].
     *
     * `KEEP` rather than `UPDATE`: this runs on every app start to
     * reconcile, and `UPDATE` would reset the interval each time, pushing the
     * next run further away on a device opened regularly — the run would
     * never actually happen.
     */
    fun sync(enabled: Boolean) {
        // Never schedule anything in a build that can't share, so the store
        // flavor carries no periodic job at all rather than one that wakes up
        // only to return immediately.
        if (!PawnsManager.available || !enabled) {
            cancel()
            return
        }
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request(),
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private fun request() =
        PeriodicWorkRequestBuilder<SharingWatchdogWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    // Sharing routes traffic, so without a network there's
                    // nothing for the service to do even if it started.
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    // No battery/idle constraints: this is mains-powered TV
                    // hardware, and requiring idle would mean the check never
                    // runs while someone's actually using the device — which
                    // is precisely when the app might be in the foreground
                    // and a restart is actually permitted.
                    .build()
            )
            .build()

    companion object {
        const val WORK_NAME = "sharing-watchdog"

        /** WorkManager's own floor. Asking for less is silently clamped to it. */
        private const val INTERVAL_MINUTES = 15L
    }
}
