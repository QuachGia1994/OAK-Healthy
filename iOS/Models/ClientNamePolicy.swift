import Foundation

/// Canonical profile-name presentation/duplicate policy; UUID remains the real identity.
public enum ClientNamePolicy: Sendable {
    public static func cleaned(_ raw: String) -> String {
        raw.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    public static func canonical(_ raw: String) -> String {
        cleaned(raw).folding(options: [.caseInsensitive, .diacriticInsensitive], locale: Locale(identifier: "en_US_POSIX"))
    }

    public static func isValid(_ raw: String) -> Bool {
        !cleaned(raw).isEmpty
    }
}
