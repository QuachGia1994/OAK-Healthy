import Foundation
import Observation

/// Dịch vụ quản lý học viên/khách hàng đang hoạt động (Coach Mode).
@Observable
@MainActor
public final class ActiveClientManager {
    public var currentClientId: UUID?

    public init() {
        currentClientId = nil
    }
    
    public func loadFromStorage() {
        currentClientId = loadClientId()
    }
    
    public func setCurrentClientId(_ id: UUID?) {
        currentClientId = id
        saveClientId(id)
    }

    private func loadClientId() -> UUID? {
        guard let raw = UserDefaults.standard.string(forKey: Self.storageKey) else { return nil }
        return UUID(uuidString: raw)
    }

    private func saveClientId(_ id: UUID?) {
        guard let id else {
            UserDefaults.standard.removeObject(forKey: Self.storageKey)
            return
        }
        UserDefaults.standard.set(id.uuidString, forKey: Self.storageKey)
    }

    private static let storageKey = "activeClientId"
}
