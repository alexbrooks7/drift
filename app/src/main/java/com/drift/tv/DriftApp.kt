package com.drift.tv

import android.app.Application
import com.drift.tv.analytics.DriftAnalytics
import com.drift.tv.sharing.BrightManager

class DriftApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Bright SDK runs its foreground service in a separate process
        // (:cskd_srvh, per its AAR manifest). Application.onCreate() runs
        // again in that process too, and per Bright's own integration guide,
        // if this code crashed there it would take the SDK's service down
        // with it — every launch, since the OS restarts a killed process.
        // Nothing below this line is needed by (or safe to run in) the SDK's
        // own process, so exit before touching PostHog or anything else.
        // Always false in the store flavor: there's no SDK process to be.
        if (BrightManager.isSvcProcess()) return
        DriftAnalytics.init(this)
        // Bright SDK itself is configured from MainActivity.onCreate, not
        // here — its init() takes an Activity, not a bare Context, and must
        // be guarded on savedInstanceState rather than Application lifecycle.
        // See MainActivity for why.
    }
}
