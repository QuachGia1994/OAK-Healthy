import Foundation
import SwiftData

/// Định nghĩa các thời điểm uống trong ngày.
///
/// Hỗ trợ phân loại lịch uống vào các buổi sáng, trưa, chiều, tối.
public enum IntakeTime: String, Codable, CaseIterable, Sendable {
    case morning = "Sáng"
    case afternoon = "Trưa"
    case evening = "Chiều"
    case night = "Tối"
    
    /// Thứ tự ưu tiên hiển thị trong ngày.
    public var order: Int {
        switch self {
        case .morning: return 0
        case .afternoon: return 1
        case .evening: return 2
        case .night: return 3
        }
    }
}

/// Cấu hình chu kỳ uống (Cycling On/Off).
///
/// Chứa thông tin về số ngày uống (On) và số ngày nghỉ (Off).
public struct CycleConfig: Codable, Sendable, Equatable {
    /// Số ngày uống liên tiếp.
    public let daysOn: Int
    /// Số ngày nghỉ liên tiếp.
    public let daysOff: Int
    /// Cờ xác định việc uống liên tục không nghỉ.
    public let isContinuous: Bool
    /// Tổng thời hạn tính bằng tháng (nil là vô thời hạn).
    public let durationMonths: Int?
    
    /// Khởi tạo cấu hình chu kỳ.
    /// - Parameters:
    ///   - daysOn: Số ngày uống.
    ///   - daysOff: Số ngày nghỉ.
    ///   - isContinuous: Có uống liên tục hay không.
    ///   - durationMonths: Tổng thời hạn.
    public init(daysOn: Int, daysOff: Int, isContinuous: Bool = false, durationMonths: Int? = nil) {
        self.daysOn = daysOn
        self.daysOff = daysOff
        self.isContinuous = isContinuous
        self.durationMonths = durationMonths
    }
    
    /// Cấu hình mặc định cho việc uống liên tục.
    public static let continuous = CycleConfig(daysOn: 1, daysOff: 0, isContinuous: true)
}

/// Dữ liệu chất bổ sung của người dùng (SwiftData Model).
///
/// Thực thể chính quản lý thông tin thực phẩm bổ sung, chu kỳ và lịch sử uống.
@Model
public final class UserSupplement: Identifiable {
    /// Định danh duy nhất cho mỗi chất.
    @Attribute(.unique) public var id: UUID
    /// Tên chất bổ sung.
    public var name: String
    /// Ngày bắt đầu theo dõi.
    public var startDate: Date
    /// Cấu hình chu kỳ On/Off.
    public var cycleConfig: CycleConfig
    /// Liều lượng hàng ngày (VD: 1000 IU).
    public var dailyDose: String
    /// Thời điểm uống trong ngày (Định dạng HH:mm).
    public var intakeTime: String
    
    /// Liên kết tới Profile người dùng.
    public var profile: UserProfile?
    
    /// Danh sách nhật ký lịch sử uống (Relationship cascade).
    @Relationship(deleteRule: .cascade, inverse: \IntakeRecord.supplement)
    public var intakeRecords: [IntakeRecord] = []
    
    /// Khởi tạo một UserSupplement mới.
    /// - Parameters:
    ///   - id: UUID duy nhất.
    ///   - name: Tên chất.
    ///   - startDate: Ngày bắt đầu.
    ///   - cycleConfig: Cấu hình chu kỳ.
    ///   - dailyDose: Liều lượng.
    ///   - intakeTime: Thời điểm uống.
    ///   - profile: Profile sở hữu.
    public init(
        id: UUID = UUID(),
        name: String,
        startDate: Date,
        cycleConfig: CycleConfig,
        dailyDose: String,
        intakeTime: String,
        profile: UserProfile? = nil
    ) {
        self.id = id
        self.name = name
        self.startDate = startDate
        self.cycleConfig = cycleConfig
        self.dailyDose = dailyDose
        self.intakeTime = intakeTime
        self.profile = profile
        self.intakeRecords = []
    }
}

/// Profile người dùng (SwiftData Model).
@Model
public final class UserProfile: Identifiable {
    @Attribute(.unique) public var id: UUID
    public var name: String
    
    @Relationship(deleteRule: .cascade, inverse: \UserSupplement.profile)
    public var supplements: [UserSupplement] = []
    
    public init(id: UUID = UUID(), name: String) {
        self.id = id
        self.name = name
        self.supplements = []
    }
}

/// Nhật ký uống thực phẩm bổ sung.
///
/// Lưu lại thời điểm và trạng thái mỗi lần người dùng xác nhận đã uống.
@Model
public final class IntakeRecord: Identifiable {
    /// Định danh duy nhất cho record.
    @Attribute(.unique) public var id: UUID
    /// Thời điểm thực hiện uống.
    public var date: Date
    /// Trạng thái (mặc định là "Taken").
    public var status: String
    /// Liên kết ngược tới chất bổ sung.
    public var supplement: UserSupplement?
    
    /// Khởi tạo nhật ký uống.
    /// - Parameters:
    ///   - id: UUID duy nhất.
    ///   - date: Ngày uống.
    ///   - status: Trạng thái.
    ///   - supplement: Chất bổ sung liên quan.
    public init(
        id: UUID = UUID(), 
        date: Date = .now, 
        status: String = "Taken", 
        supplement: UserSupplement? = nil
    ) {
        self.id = id
        self.date = date
        self.status = status
        self.supplement = supplement
    }
}

/// Dữ liệu tham khảo từ từ điển cục bộ.
///
/// Dùng để gợi ý thông tin mặc định khi người dùng thêm chất mới.
public struct SupplementReference: Codable, Sendable, Identifiable {
    public var id: String { name }
    /// Tên chất.
    public let name: String
    /// Ghi chú/Lời khuyên uống.
    public let advice: String?
    /// Thời điểm uống khuyến nghị (HH:mm).
    public let preferredTime: String
    /// Chu kỳ uống mặc định.
    public let defaultCycle: CycleConfig
}

/// Thông tin bổ sung cho chất đang nghỉ (Dùng trong UI).
///
/// Chứa thông tin về số ngày nghỉ còn lại trong chu kỳ hiện tại.
public struct RestingSupplementInfo: Identifiable {
    public var id: UUID { supplement.id }
    /// Chất bổ sung đang trong giai đoạn nghỉ.
    public let supplement: UserSupplement
    /// Số ngày nghỉ còn lại.
    public let daysRemaining: Int
}
