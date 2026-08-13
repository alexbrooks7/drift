package com.drift.tv

import android.app.Application
import com.drift.tv.analytics.DriftAnalytics
import com.drift.tv.sharing.PawnsManager

class DriftApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DriftAnalytics.init(this)
        // Configures the SDK only — never starts sharing by itself. Actual
        // sharing only begins from PawnsManager.grantConsent(), reached
        // through ConsentScreen's explicit "Share" choice.
        PawnsManager.init(this)
    }
}
