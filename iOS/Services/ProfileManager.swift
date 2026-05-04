import Foundation
import Observation

/// Lỗi liên quan đến quản lý Profile.
public enum ProfileError: Error, Sendable {
    case profileNotFound
    case duplicateName
}

/// Dịch vụ quản lý Profile người dùng.
@Observable
@MainActor
public final class ProfileManager {
    public private(set) var profiles: [UserProfile] = []
    public var currentProfileID: UUID?
    
    public var currentProfile: UserProfile? {
        profiles.first { $0.id == currentProfileID }
    }
    
    public init() {
        // Load data from storage if needed
    }
    
    /// Thêm một profile mới.
    /// - Parameter name: Tên người dùng.
    public func addProfile(name: String) throws(ProfileError) {
        guard !profiles.contains(where: { $0.name == name }) else {
            throw .duplicateName
        }
        
        let newProfile = UserProfile(name: name)
        profiles.append(newProfile)
        
        if currentProfileID == nil {
            currentProfileID = newProfile.id
        }
    }
    
    /// Chuyển đổi giữa các profile.
    public func switchProfile(to id: UUID) {
        currentProfileID = id
    }
    
    /// Thêm một thực phẩm bổ sung cho profile hiện tại.
    public func addSupplementToCurrentProfile(_ supplement: UserSupplement) throws(ProfileError) {
        guard let index = profiles.firstIndex(where: { $0.id == currentProfileID }) else {
            throw .profileNotFound
        }
        
        profiles[index].supplements.append(supplement)
    }
}
