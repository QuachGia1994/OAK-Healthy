import FirebaseAuth
import FirebaseCore
import Foundation

enum FirebaseBootstrap {
    static let databaseURL = "https://oak-healthy-default-rtdb.asia-southeast1.firebasedatabase.app"
    private static var didConfigure = false
    
    static func configureIfNeeded() {
        guard !didConfigure else { return }
        didConfigure = true
        FirebaseApp.configure()
    }
    
    static func ensureSignedIn() async throws {
        configureIfNeeded()
        if Auth.auth().currentUser != nil { return }
        _ = try await Auth.auth().signInAnonymously()
    }
}

