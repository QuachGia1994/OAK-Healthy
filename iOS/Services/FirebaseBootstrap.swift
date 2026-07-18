@preconcurrency import FirebaseAuth
@preconcurrency import FirebaseAppCheck
@preconcurrency import FirebaseDatabase
import FirebaseCore
import Foundation

enum FirebaseBootstrapError: LocalizedError {
    case missingClientConfiguration

    var errorDescription: String? {
        "sync_center_error_missing_firebase_config".localized
    }
}

@MainActor
enum FirebaseBootstrap {
    nonisolated static let databaseURL = "https://oak-healthy-default-rtdb.asia-southeast1.firebasedatabase.app"
    private static var didConfigure = false

    static func configureIfNeeded() {
        guard !didConfigure else { return }
        if FirebaseApp.app() != nil { markDone("ok"); return }
#if DEBUG
        AppCheck.setAppCheckProviderFactory(AppCheckDebugProviderFactory())
#else
        AppCheck.setAppCheckProviderFactory(AppAttestProviderFactory())
#endif
        if tryPlist(Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist")) { return }
        UserDefaults.standard.set("missing_plist", forKey: "fbDiagState")
    }

    private static func tryPlist(_ path: String?) -> Bool {
        guard let path, let opts = FirebaseOptions(contentsOfFile: path) else { return false }
        FirebaseApp.configure(options: opts)
        guard FirebaseApp.app() != nil else { return false }
        markDone("configured")
        return true
    }

    private static func markDone(_ state: String) {
        didConfigure = true
        UserDefaults.standard.set(state, forKey: "fbDiagState")
        postConfigure()
    }

    static var firebaseDiag: String {
        "state=\(UserDefaults.standard.string(forKey: "fbDiagState") ?? "not_called") app=\(FirebaseApp.app() != nil)"
    }

    static func ensureSignedIn() async throws {
        configureIfNeeded()
        guard FirebaseApp.app() != nil else {
            throw FirebaseBootstrapError.missingClientConfiguration
        }
        if Auth.auth().currentUser != nil { return }
        _ = try await Auth.auth().signInAnonymously()
    }

    private static func postConfigure() {
        Database.database(url: databaseURL).isPersistenceEnabled = true
    }
}
