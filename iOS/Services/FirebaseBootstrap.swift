@preconcurrency import FirebaseAuth
@preconcurrency import FirebaseAppCheck
@preconcurrency import FirebaseDatabase
import FirebaseCore
import Foundation

@MainActor
enum FirebaseBootstrap {
    nonisolated static let databaseURL = "https://oak-healthy-default-rtdb.asia-southeast1.firebasedatabase.app"
    private static var didConfigure = false

    static func configureIfNeeded() {
        guard !didConfigure else { return }

#if DEBUG
        AppCheck.setAppCheckProviderFactory(AppCheckDebugProviderFactory())
#else
        AppCheck.setAppCheckProviderFactory(AppAttestProviderFactory())
#endif
        if FirebaseApp.app() != nil {
            didConfigure = true
            UserDefaults.standard.set("ok", forKey: "fbDiagState")
            postConfigure()
            return
        }

        let plistPath = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist")
        guard let path = plistPath,
              let options = FirebaseOptions(contentsOfFile: path) else {
            UserDefaults.standard.set("plist_not_found", forKey: "fbDiagState")
            return
        }
        FirebaseApp.configure(options: options)
        let appOK = FirebaseApp.app() != nil
        UserDefaults.standard.set(appOK ? "configured" : "configure_failed", forKey: "fbDiagState")
        if appOK {
            didConfigure = true
            postConfigure()
        }
    }

    static var firebaseDiag: String {
        let state = UserDefaults.standard.string(forKey: "fbDiagState") ?? "not_called"
        let appOK = FirebaseApp.app() != nil
        return "state=\(state) app=\(appOK)"
    }

    static func ensureSignedIn() async throws {
        configureIfNeeded()
        guard FirebaseApp.app() != nil else { throw FirebaseOfflineError() }
        if Auth.auth().currentUser != nil { return }
        _ = try await Auth.auth().signInAnonymously()
    }

    private static func postConfigure() {
        let db = Database.database(url: databaseURL)
        db.isPersistenceEnabled = true
    }
}
