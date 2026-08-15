package com.drift.tv

import android.app.Application
import com.drift.tv.analytics.DriftAnalytics
import com.drift.tv.sharing.PawnsManager

class DriftApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DriftAnalytics.init(this)
        // Configures the SDK only — never starts sharing by itself. Sharing
        // begins only from an explicit "Okay" in ConsentDialog, or Turn on in
        // Settings. In the store flavor this is a no-op: there's no SDK there.
        PawnsManager.init(this)
    }
}
