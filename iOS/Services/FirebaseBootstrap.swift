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
            NSLog("[Firebase] GoogleService-Info.plist missing — sync features disabled")
            return
        }
        FirebaseApp.configure(options: options)
        postConfigure()
    }
    
    private static let lastSignInAttemptKey = "oakLastFirebaseSignInAttempt"
    private static let minSignInInterval: TimeInterval = 30

    static func ensureSignedIn() async throws {
        configureIfNeeded()
        if Auth.auth().currentUser != nil { return }
        let now = Date().timeIntervalSince1970
        let last = UserDefaults.standard.double(forKey: lastSignInAttemptKey)
        guard now - last > minSignInInterval else { return }
        UserDefaults.standard.set(now, forKey: lastSignInAttemptKey)
        _ = try await Auth.auth().signInAnonymously()
    }
    
    private static func postConfigure() {
        let db = Database.database(url: databaseURL)
        db.isPersistenceEnabled = true
    }
}
