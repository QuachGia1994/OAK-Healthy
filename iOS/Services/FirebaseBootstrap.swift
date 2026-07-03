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
        didConfigure = true

#if DEBUG
        AppCheck.setAppCheckProviderFactory(AppCheckDebugProviderFactory())
#else
        AppCheck.setAppCheckProviderFactory(AppAttestProviderFactory())
#endif
        if FirebaseApp.app() != nil {
            postConfigure()
            return
        }

        guard let path = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist"),
              let options = FirebaseOptions(contentsOfFile: path) else {
            fatalError("[FirebaseBootstrap] GoogleService-Info.plist not found. Configure Firebase via plist.")
        }
        FirebaseApp.configure(options: options)
        postConfigure()
    }
    
    static func ensureSignedIn() async throws {
        configureIfNeeded()
        if Auth.auth().currentUser != nil { return }
        _ = try await Auth.auth().signInAnonymously()
    }
    
    private static func postConfigure() {
        let db = Database.database(url: databaseURL)
        db.isPersistenceEnabled = true
    }
}
