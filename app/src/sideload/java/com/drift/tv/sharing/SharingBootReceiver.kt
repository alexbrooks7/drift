package com.drift.tv.sharing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.drift.tv.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Resumes internet sharing after a reboot or an app update.
 *
 * Without this the feature simply stopped at every reboot and stayed stopped
 * until someone next opened the app — a TV box that reboots overnight and
 * sits on the home screen for a day would share nothing that whole day, while
 * Settings honestly reported "Off" to anyone who went looking.
 *
 * Lives in `src/sideload` and is declared only in that flavor's manifest, so
 * the store build — which has no Pawns SDK at all — never registers a boot
 * receiver for a service it doesn't contain.
 *
 * ### Why a receiver rather than leaving it to the watchdog
 *
 * `SharingWatchdogWorker` can't do this job. Android 12 forbids starting a
 * foreground service from the background, and a WorkManager job is the
 * background, so its restart attempt is refused in exactly this situation.
 * `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` are two of the explicit
 * exemptions to that rule — the app is permitted to start a foreground
 * service while handling them, which makes this the one path that reliably
 * works with no user-granted permission behind it.
 *
 * `RECEIVE_BOOT_COMPLETED` costs nothing extra here: WorkManager already
 * declares it for its own scheduling to survive a reboot.
 */
class SharingBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            // Receivers are addressable by other apps; ignoring anything not
            // asked for means a stray broadcast can't start sharing.
            else -> return
        }
        if (!PawnsManager.available) return

        // Android delivers no component until Application.onCreate has run,
        // so PawnsManager.init has already configured the SDK by the time we
        // get here.
        val app = context.applicationContext
        // goAsync keeps the broadcast — and with it the permission to start a
        // foreground service — alive across the DataStore read, which is disk
        // I/O and can't be done on the main thread.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val enabled = withTimeoutOrNull(SETTINGS_READ_TIMEOUT_MS) {
                    Prefs.sharingEnabled(app)
                }
                // Gated on the stored preference, never on hasConsent() alone
                // — see Prefs.sharingEnabled for why.
                if (enabled == true && PawnsManager.hasConsent()) {
                    Log.i(TAG, "Resuming sharing after ${intent.action}")
                    PawnsManager.startSharing(app)
                }
            } catch (e: Exception) {
                // A receiver that throws takes the whole process down with
                // it, at boot, on every boot. Whatever went wrong here,
                // failing to resume sharing isn't worth that.
                Log.e(TAG, "Failed to resume sharing at boot", e)
            } finally {
                // Must run on every path or the system holds the broadcast
                // open until it times out and reports the app as slow.
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SharingBootReceiver"
        const val SETTINGS_READ_TIMEOUT_MS = 5_000L
    }
}
