package com.snaprelay

import android.app.Application

class SnapRelayApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Singletons and app-level initialization will be wired here
    }
}
