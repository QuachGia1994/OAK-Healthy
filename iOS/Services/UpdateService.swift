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
        // Giả lập gọi API kiểm tra version
        // Trong thực tế sẽ dùng URLSession.shared.data(from: url)
        try? await Task.sleep(for: .seconds(1))
        
        let currentVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
        let remoteVersion = "1.1.0" // Giả sử version mới là 1.1.0
        
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
