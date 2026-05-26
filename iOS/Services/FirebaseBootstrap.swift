@preconcurrency import FirebaseAuth
import FirebaseCore
import Foundation

@MainActor
enum FirebaseBootstrap {
    nonisolated static let databaseURL = "https://oak-healthy-default-rtdb.asia-southeast1.firebasedatabase.app"
    nonisolated private static let googleAppID = "1:339994104835:ios:3662c12531ad716cac2a36"
    nonisolated private static let gcmSenderID = "339994104835"
    nonisolated private static let apiKey = "AIzaSyBi9c4GbH_XWl4y9qBJf0rgjBYsHBIewFw"
    nonisolated private static let projectID = "oak-healthy"
    nonisolated private static let storageBucket = "oak-healthy.firebasestorage.app"
    private static var didConfigure = false
    
    static func configureIfNeeded() {
        guard !didConfigure else { return }
        didConfigure = true
        if FirebaseApp.app() != nil { return }
        
        if let path = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist"),
           let options = FirebaseOptions(contentsOfFile: path) {
            FirebaseApp.configure(options: options)
            return
        }
        
        let options = FirebaseOptions(googleAppID: googleAppID, gcmSenderID: gcmSenderID)
        options.apiKey = apiKey
        options.projectID = projectID
        options.databaseURL = databaseURL
        options.storageBucket = storageBucket
        FirebaseApp.configure(options: options)
    }
    
    static func ensureSignedIn() async throws {
        configureIfNeeded()
        if Auth.auth().currentUser != nil { return }
        _ = try await Auth.auth().signInAnonymously()
    }
}
