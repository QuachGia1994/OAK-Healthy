package com.example.supplementtracker

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.example.supplementtracker.service.DiagnosticsReporter

class OAKHealthyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        val appCheck = FirebaseAppCheck.getInstance()
        installAppCheckProvider(appCheck)
        appCheck.setTokenAutoRefreshEnabled(true)
        DiagnosticsReporter.applyStoredConsent(this)
    }
}
