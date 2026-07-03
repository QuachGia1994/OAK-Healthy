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

        if configureFromPlist() {
            didConfigure = true
            UserDefaults.standard.set("configured", forKey: "fbDiagState")
            postConfigure()
        } else if configureFromFallback() {
            didConfigure = true
            UserDefaults.standard.set("fallback", forKey: "fbDiagState")
            postConfigure()
        } else {
            UserDefaults.standard.set("plist_not_found", forKey: "fbDiagState")
        }
    }

    private static func configureFromPlist() -> Bool {
        guard let path = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist"),
              let options = FirebaseOptions(contentsOfFile: path) else { return false }
        FirebaseApp.configure(options: options)
        return FirebaseApp.app() != nil
    }

    private static func configureFromFallback() -> Bool {
        let options = FirebaseOptions(
            googleAppID: "1:339994104835:ios:3662c12531ad716cac2a36",
            gcmSenderID: "339994104835"
        )
        options.projectID = "oak-healthy"
        options.databaseURL = "https://oak-healthy-default-rtdb.asia-southeast1.firebasedatabase.app"
        options.storageBucket = "oak-healthy.firebasestorage.app"
        FirebaseApp.configure(options: options)
        return FirebaseApp.app() != nil
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
