@preconcurrency import FirebaseAuth
@preconcurrency import FirebaseAppCheck
@preconcurrency import FirebaseDatabase
import FirebaseCore
import Foundation

@MainActor
enum FirebaseBootstrap {
    nonisolated static let databaseURL = "https://oak-healthy-default-rtdb.asia-southeast1.firebasedatabase.app"
    private static var didConfigure = false

    private static let embeddedPlist = """
    <?xml version="1.0" encoding="UTF-8"?><!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd"><plist version="1.0"><dict><key>API_KEY</key><string>AIzaSyBi9c4GbH_XWl4y9qBJf0rgjBYsHBIewFw</string><key>GCM_SENDER_ID</key><string>339994104835</string><key>PLIST_VERSION</key><string>1</string><key>BUNDLE_ID</key><string>com.phongqk.oakhealthy</string><key>PROJECT_ID</key><string>oak-healthy</string><key>STORAGE_BUCKET</key><string>oak-healthy.firebasestorage.app</string><key>IS_ADS_ENABLED</key><false/><key>IS_ANALYTICS_ENABLED</key><false/><key>IS_APPINVITE_ENABLED</key><true/><key>IS_GCM_ENABLED</key><true/><key>IS_SIGNIN_ENABLED</key><true/><key>GOOGLE_APP_ID</key><string>1:339994104835:ios:3662c12531ad716cac2a36</string></dict></plist>
    """

    static func configureIfNeeded() {
        guard !didConfigure else { return }
        if FirebaseApp.app() != nil { markDone("ok"); return }
        AppCheck.setAppCheckProviderFactory(
#if DEBUG
            AppCheckDebugProviderFactory()
#else
            AppAttestProviderFactory()
#endif
        )
        if tryPlist(Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist")) { return }
        let tmp = (NSTemporaryDirectory() as NSString).appendingPathComponent("GoogleService-Info.plist")
        if (try? embeddedPlist.write(toFile: tmp, atomically: true, encoding: .utf8)) != nil { _ = tryPlist(tmp) }
        UserDefaults.standard.set(FirebaseApp.app() != nil ? "embedded" : "all_failed", forKey: "fbDiagState")
        if FirebaseApp.app() != nil { didConfigure = true; postConfigure() }
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
        guard FirebaseApp.app() != nil else { throw FirebaseOfflineError() }
        if Auth.auth().currentUser != nil { return }
        _ = try await Auth.auth().signInAnonymously()
    }

    private static func postConfigure() {
        Database.database(url: databaseURL).isPersistenceEnabled = true
    }
}
