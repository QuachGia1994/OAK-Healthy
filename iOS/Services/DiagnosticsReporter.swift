@preconcurrency import FirebaseAnalytics
@preconcurrency import FirebaseCrashlytics
import Foundation

public enum DiagnosticsPrivacyPolicy {
    private static let allowedEvents: Set<String> = [
        "plan_access_view",
        "billing_purchase_started",
        "billing_restore_started"
    ]
    private static let allowedKeys: Set<String> = ["plan", "billing_period", "source"]

    public static func sanitize(
        event: String,
        fields: [String: String]
    ) -> (String, [String: String])? {
        guard allowedEvents.contains(event) else { return nil }
        let safeFields = fields.reduce(into: [String: String]()) { result, item in
            guard allowedKeys.contains(item.key) else { return }
            result[item.key] = sanitizeValue(item.value)
        }
        return (event, safeFields)
    }

    private static func sanitizeValue(_ value: String) -> String {
        let lowered = value.lowercased()
        let allowed = CharacterSet(charactersIn: "abcdefghijklmnopqrstuvwxyz0123456789_-")
        let normalized = lowered.unicodeScalars.map { allowed.contains($0) ? Character(String($0)) : "_" }
        return String(normalized.prefix(40))
    }
}

public enum DiagnosticsReporter {
    private static let preferenceKey = "shareAnonymousDiagnostics"

    public static var isEnabled: Bool {
        UserDefaults.standard.bool(forKey: preferenceKey)
    }

    public static func applyStoredConsent() {
        setCollection(isEnabled)
    }

    public static func setConsent(_ enabled: Bool) {
        UserDefaults.standard.set(enabled, forKey: preferenceKey)
        setCollection(enabled)
    }

    public static func event(_ name: String, fields: [String: String] = [:]) {
        guard isEnabled else { return }
        guard let sanitized = DiagnosticsPrivacyPolicy.sanitize(event: name, fields: fields) else { return }
        let parameters = sanitized.1.reduce(into: [String: Any]()) { result, item in
            result[item.key] = item.value
        }
        Analytics.logEvent(sanitized.0, parameters: parameters)
    }

    private static func setCollection(_ enabled: Bool) {
        Analytics.setAnalyticsCollectionEnabled(enabled)
        let crashlytics = Crashlytics.crashlytics()
        crashlytics.setCrashlyticsCollectionEnabled(enabled)
        if !enabled { crashlytics.deleteUnsentReports() }
    }
}
