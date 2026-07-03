@preconcurrency import FirebaseAuth
@preconcurrency import FirebaseAppCheck
@preconcurrency import FirebaseDatabase
import FirebaseCore
import Foundation

@MainActor
enum FirebaseBootstrap {
    nonisolated static let databaseURL = "https://oak-healthy-default-rtdb.asia-southeast1.firebasedatabase.app"
    private static var didConfigure = false

    private static let embeddedPlistXML = """
    <?xml version="1.0" encoding="UTF-8"?>
    <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
    <plist version="1.0">
    <dict>
        <key>API_KEY</key>
        <string>AIzaSyBi9c4GbH_XWl4y9qBJf0rgjBYsHBIewFw</string>
        <key>GCM_SENDER_ID</key>
        <string>339994104835</string>
        <key>PLIST_VERSION</key>
        <string>1</string>
        <key>BUNDLE_ID</key>
        <string>com.phongqk.oakhealthy</string>
        <key>PROJECT_ID</key>
        <string>oak-healthy</string>
        <key>STORAGE_BUCKET</key>
        <string>oak-healthy.firebasestorage.app</string>
        <key>IS_ADS_ENABLED</key>
        <false/>
        <key>IS_ANALYTICS_ENABLED</key>
        <false/>
        <key>IS_APPINVITE_ENABLED</key>
        <true/>
        <key>IS_GCM_ENABLED</key>
        <true/>
        <key>IS_SIGNIN_ENABLED</key>
        <true/>
        <key>GOOGLE_APP_ID</key>
        <string>1:339994104835:ios:3662c12531ad716cac2a36</string>
    </dict>
    </plist>
    """

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

        // 1) Try bundle plist first
        if configureFromPlist() {
            didConfigure = true
            UserDefaults.standard.set("configured", forKey: "fbDiagState")
            postConfigure()
            return
        }

        // 2) Write embedded plist to temp, then load from there
        if configureFromEmbeddedPlist() {
            didConfigure = true
            UserDefaults.standard.set("embedded", forKey: "fbDiagState")
            postConfigure()
            return
        }

        UserDefaults.standard.set("all_failed", forKey: "fbDiagState")
    }

    private static func configureFromPlist() -> Bool {
        guard let path = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist"),
              let options = FirebaseOptions(contentsOfFile: path) else { return false }
        FirebaseApp.configure(options: options)
        return FirebaseApp.app() != nil
    }

    private static func configureFromEmbeddedPlist() -> Bool {
        let tempDir = NSTemporaryDirectory()
        let tempPlist = (tempDir as NSString).appendingPathComponent("GoogleService-Info.plist")
        guard (try? embeddedPlistXML.write(toFile: tempPlist, atomically: true, encoding: .utf8)) != nil,
              let options = FirebaseOptions(contentsOfFile: tempPlist) else { return false }
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
