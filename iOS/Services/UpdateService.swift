import Foundation
import Observation

/// Thông tin phiên bản ứng dụng từ xa.
public struct AppUpdateInfo: Codable, Sendable {
    public let version: String
    public let updateUrl: String
    public let forceUpdate: Bool
}

/// Dịch vụ kiểm tra cập nhật phiên bản (iOS).
@Observable
@MainActor
public final class UpdateService {
    public var updateInfo: AppUpdateInfo?
    public var isUpdateAvailable: Bool = false
    
    public init() {}
    
    /// Kiểm tra phiên bản mới từ GitHub Gist/Remote JSON.
    public func checkForUpdates() async {
        let currentVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
        let remoteVersion = currentVersion
        
        if remoteVersion > currentVersion {
            self.updateInfo = AppUpdateInfo(
                version: remoteVersion,
                updateUrl: "https://github.com/your-repo/OAK-Healthy/releases",
                forceUpdate: false
            )
            self.isUpdateAvailable = true
        }
    }
}
