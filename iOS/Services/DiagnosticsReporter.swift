@preconcurrency import FirebaseAnalytics
@preconcurrency import FirebaseCrashlytics
import Foundation

public enum DiagnosticsPrivacyPolicy {
    private static let allowedEvents: Set<String> = [
        "plan_access_view",
        "billing_products_loaded",
        "billing_purchase_started",
        "billing_purchase_result",
        "billing_restore_started",
        "billing_restore_result",
        "activation_milestone"
    ]
    private static let commercialKeys: Set<String> = ["plan", "billing_period", "source", "product_id", "result"]
    private static let allowedKeysByEvent: [String: Set<String>] = Dictionary(
        uniqueKeysWithValues: allowedEvents.map { event in
            (event, event == "activation_milestone" ? Set(["milestone", "state"]) : commercialKeys)
        }
    )

    public static func sanitize(
        event: String,
        fields: [String: String]
    ) -> (String, [String: String])? {
        guard let allowedKeys = allowedKeysByEvent[event] else { return nil }
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

public enum CommercialTelemetryFields {
    public static func product(_ productId: String, source: String) -> [String: String] {
        var fields = ["product_id": productId, "source": source]
        guard let product = CommercialProductCatalog.products.first(where: { $0.productId == productId }) else {
            return fields
        }
        fields["plan"] = product.plan.rawValue
        fields["billing_period"] = billingPeriod(product.billingPeriod)
        return fields
    }

    public static func result(_ result: String, source: String, productId: String? = nil) -> [String: String] {
        var fields = productId.map { product($0, source: source) } ?? ["source": source]
        fields["result"] = result
        return fields
    }

    private static func billingPeriod(_ period: BillingPeriod) -> String {
        switch period {
        case .monthly: return "monthly"
        case .annual: return "annual"
        }
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
