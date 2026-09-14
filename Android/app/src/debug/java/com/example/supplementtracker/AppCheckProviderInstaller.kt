package com.example.supplementtracker

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

internal fun installAppCheckProvider(appCheck: FirebaseAppCheck) {
    appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
}
