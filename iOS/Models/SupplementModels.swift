import Foundation
import SwiftData

/// Định nghĩa các thời điểm uống trong ngày.
public enum IntakeTime: String, Codable, CaseIterable, Sendable {
    case morning = "Sáng"
    case afternoon = "Trưa"
    case evening = "Chiều"
    case night = "Tối"
    
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
public struct CycleConfig: Codable, Sendable, Equatable {
    public let daysOn: Int
    public let daysOff: Int
    public let isContinuous: Bool
    
    public init(daysOn: Int, daysOff: Int, isContinuous: Bool = false) {
        self.daysOn = daysOn
        self.daysOff = daysOff
        self.isContinuous = isContinuous
    }
    
    public static let continuous = CycleConfig(daysOn: 1, daysOff: 0, isContinuous: true)
}

/// Dữ liệu chất bổ sung của người dùng (SwiftData Model).
@Model
public final class UserSupplement: Identifiable {
    @Attribute(.unique) public var id: UUID
    public var name: String
    public var startDate: Date
    public var cycleConfig: CycleConfig
    public var dailyDose: String
    public var intakeTime: IntakeTime
    
    @Relationship(deleteRule: .cascade, inverse: \IntakeRecord.supplement)
    public var intakeRecords: [IntakeRecord] = []
    
    public init(
        id: UUID = UUID(),
        name: String,
        startDate: Date,
        cycleConfig: CycleConfig,
        dailyDose: String,
        intakeTime: IntakeTime
    ) {
        self.id = id
        self.name = name
        self.startDate = startDate
        self.cycleConfig = cycleConfig
        self.dailyDose = dailyDose
        self.intakeTime = intakeTime
        self.intakeRecords = []
    }
}

/// Nhật ký uống thực phẩm bổ sung.
@Model
public final class IntakeRecord: Identifiable {
    @Attribute(.unique) public var id: UUID
    public var date: Date
    public var status: String // "Taken"
    public var supplement: UserSupplement?
    
    public init(id: UUID = UUID(), date: Date = .now, status: String = "Taken", supplement: UserSupplement? = nil) {
        self.id = id
        self.date = date
        self.status = status
        self.supplement = supplement
    }
}

/// Dữ liệu tham khảo từ từ điển cục bộ.
public struct SupplementReference: Codable, Sendable, Identifiable {
    public var id: String { name }
    public let name: String
    public let preferredTime: IntakeTime
    public let defaultCycle: CycleConfig
}

/// Thông tin bổ sung cho chất đang nghỉ (Dùng trong UI).
public struct RestingSupplementInfo: Identifiable, Sendable {
    public var id: UUID { supplement.id }
    public let supplement: UserSupplement
    public let daysRemaining: Int
}
