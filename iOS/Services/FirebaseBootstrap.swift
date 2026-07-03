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
        print("[FB-DIAG] configureIfNeeded entered")

#if DEBUG
        AppCheck.setAppCheckProviderFactory(AppCheckDebugProviderFactory())
#else
        AppCheck.setAppCheckProviderFactory(AppAttestProviderFactory())
#endif
        if FirebaseApp.app() != nil {
            print("[FB-DIAG] FirebaseApp already exists")
            postConfigure()
            return
        }

        let plistPath = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist")
        print("[FB-DIAG] plist path: \(plistPath ?? "NOT FOUND")")
        guard let path = plistPath,
              let options = FirebaseOptions(contentsOfFile: path) else {
            print("[FB-DIAG] FirebaseOptions parse failed — Firebase disabled.")
            return
        }
        FirebaseApp.configure(options: options)
        let appExists = FirebaseApp.app() != nil
        print("[FB-DIAG] after configure, FirebaseApp.app() = \(appExists)")
        postConfigure()
    }

    static func ensureSignedIn() async throws {
        configureIfNeeded()
        let appOK = FirebaseApp.app() != nil
        print("[FB-DIAG] ensureSignedIn: appOK=\(appOK)")
        guard appOK else { throw FirebaseOfflineError() }
        if Auth.auth().currentUser != nil { return }
        _ = try await Auth.auth().signInAnonymously()
    }

    private static func postConfigure() {
        let db = Database.database(url: databaseURL)
        db.isPersistenceEnabled = true
    }
}
