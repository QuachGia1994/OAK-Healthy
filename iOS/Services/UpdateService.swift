import Foundation
import Observation

/// Thông tin phiên bản ứng dụng từ xa.
public struct AppUpdateInfo: Codable, Sendable {
    public let version: String
    public let updateUrl: String
    public let forceUpdate: Bool
    public let releaseNotes: String
}

public struct UpdateConfig: Codable, Sendable {
    public let latestVersion: String
    public let isForceUpdate: Bool
    public let releaseNotes: String
    public let updateUrl: String
    
    enum CodingKeys: String, CodingKey {
        case latestVersion = "latest_version"
        case isForceUpdate = "is_force_update"
        case releaseNotes = "release_notes"
        case updateUrl = "update_url"
    }
}

/// Dịch vụ kiểm tra cập nhật phiên bản (iOS).
@Observable
@MainActor
public final class UpdateService {
    public var updateInfo: AppUpdateInfo?
    public var isUpdateAvailable: Bool = false
    
    public init() {}
    
    private static let allowedUpdateHosts = ["apps.apple.com", "testflight.apple.com"]

    /// Kiểm tra phiên bản mới từ GitHub Gist/Remote JSON.
    public func checkForUpdates() async {
        guard let url = URL(string: "https://gist.githubusercontent.com/QuachGia1994/901e36f6bab91729d5dd0e2ccce7202f/raw/oak_update.json") else { return }
        let currentVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
        
        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            let config = try JSONDecoder().decode(UpdateConfig.self, from: data)
            guard isVersion(config.latestVersion, newerThan: currentVersion) else { return }
            if config.isForceUpdate == false, skippedUpdateVersion == config.latestVersion { return }
            guard isTrustedUpdateURL(config.updateUrl) else { return }
            
            updateInfo = AppUpdateInfo(
                version: config.latestVersion,
                updateUrl: config.updateUrl,
                forceUpdate: config.isForceUpdate,
                releaseNotes: config.releaseNotes
            )
            isUpdateAvailable = true
        } catch {
            return
        }
    }

    private func isTrustedUpdateURL(_ urlString: String) -> Bool {
        guard let url = URL(string: urlString), let host = url.host else { return false }
        return Self.allowedUpdateHosts.contains(host)
    }

    public func skipUpdate(version: String) {
        guard version.isEmpty == false else {
            isUpdateAvailable = false
            return
        }
        UserDefaults.standard.set(version, forKey: Self.skippedVersionKey)
        isUpdateAvailable = false
    }

    private var skippedUpdateVersion: String? {
        UserDefaults.standard.string(forKey: Self.skippedVersionKey)
    }
    
    private func isVersion(_ a: String, newerThan b: String) -> Bool {
        compareVersions(a, b) == .orderedDescending
    }
    
    private func compareVersions(_ a: String, _ b: String) -> ComparisonResult {
        let aParts = a.split(separator: ".").map { Int($0) ?? 0 }
        let bParts = b.split(separator: ".").map { Int($0) ?? 0 }
        let count = max(aParts.count, bParts.count)
        
        for i in 0..<count {
            let left = i < aParts.count ? aParts[i] : 0
            let right = i < bParts.count ? bParts[i] : 0
            if left != right { return left > right ? .orderedDescending : .orderedAscending }
        }
        return .orderedSame
    }

    private static let skippedVersionKey = "SkippedUpdateVersion"
}
