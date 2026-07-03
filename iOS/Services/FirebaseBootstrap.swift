@preconcurrency import FirebaseAuth
@preconcurrency import FirebaseAppCheck
@preconcurrency import FirebaseDatabase
import FirebaseCore
import Foundation

@MainActor
enum FirebaseBootstrap {
    nonisolated static let databaseURL = "https://oak-healthy-default-rtdb.asia-southeast1.firebasedatabase.app"
    private static var didConfigure = false
    nonisolated(unsafe) private static var isConfigured = false

    static func configureIfNeeded() {
        guard !didConfigure else { return }
        didConfigure = true

#if DEBUG
        AppCheck.setAppCheckProviderFactory(AppCheckDebugProviderFactory())
#else
        AppCheck.setAppCheckProviderFactory(AppAttestProviderFactory())
#endif
        if FirebaseApp.app() != nil {
            isConfigured = true
            postConfigure()
            return
        }

        guard let path = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist"),
              let options = FirebaseOptions(contentsOfFile: path) else {
            print("[FirebaseBootstrap] GoogleService-Info.plist not found — Firebase disabled.")
            return
        }
        FirebaseApp.configure(options: options)
        isConfigured = true
        postConfigure()
    }

    nonisolated static var isFirebaseEnabled: Bool { isConfigured }

    static func ensureSignedIn() async throws {
        configureIfNeeded()
        guard isConfigured else { throw FirebaseOfflineError() }
        if Auth.auth().currentUser != nil { return }
        _ = try await Auth.auth().signInAnonymously()
    }

    private static func postConfigure() {
        let db = Database.database(url: databaseURL)
        db.isPersistenceEnabled = true
    }
}
