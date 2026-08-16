package com.example.supplementtracker.service

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

object DiagnosticsPrivacyPolicy {
    private val allowedEvents = setOf(
        "plan_access_view",
        "billing_purchase_started",
        "billing_restore_started"
    )
    private val allowedKeys = setOf("plan", "billing_period", "source")

    fun sanitize(event: String, fields: Map<String, String>): Pair<String, Map<String, String>>? {
        if (event !in allowedEvents) return null
        val safeFields = fields.filterKeys { it in allowedKeys }
            .mapValues { (_, value) -> sanitizeValue(value) }
        return event to safeFields
    }

    private fun sanitizeValue(value: String): String {
        return value.lowercase().replace(Regex("[^a-z0-9_-]"), "_").take(40)
    }
}

object DiagnosticsReporter {
    private const val preferenceKey = "shareAnonymousDiagnostics"

    fun applyStoredConsent(context: Context) {
        setCollection(context, OakPrefs.get(context).getBoolean(preferenceKey, false))
    }

    fun setConsent(context: Context, enabled: Boolean) {
        OakPrefs.get(context).edit().putBoolean(preferenceKey, enabled).apply()
        setCollection(context, enabled)
    }

    fun isEnabled(context: Context): Boolean {
        return OakPrefs.get(context).getBoolean(preferenceKey, false)
    }

    fun event(context: Context, name: String, fields: Map<String, String> = emptyMap()) {
        if (!isEnabled(context)) return
        val sanitized = DiagnosticsPrivacyPolicy.sanitize(name, fields) ?: return
        FirebaseAnalytics.getInstance(context).logEvent(sanitized.first, sanitized.second.toBundle())
    }

    private fun setCollection(context: Context, enabled: Boolean) {
        FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(enabled)
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCrashlyticsCollectionEnabled(enabled)
        if (!enabled) crashlytics.deleteUnsentReports()
    }

    private fun Map<String, String>.toBundle(): Bundle = Bundle().also { bundle ->
        forEach { (key, value) -> bundle.putString(key, value) }
    }
}
