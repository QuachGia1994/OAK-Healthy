package com.example.supplementtracker

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

internal fun installAppCheckProvider(appCheck: FirebaseAppCheck) {
    appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
}
