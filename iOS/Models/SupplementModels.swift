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

public struct WeeklyRecurrenceConfig: Codable, Sendable, Equatable {
    public let weekdaysMask: Int
    public let intervalWeeks: Int
    public let anchorDate: Date
    
    public init(weekdaysMask: Int, intervalWeeks: Int, anchorDate: Date) {
        self.weekdaysMask = max(1, min(127, weekdaysMask))
        self.intervalWeeks = max(1, min(52, intervalWeeks))
        self.anchorDate = anchorDate
    }
    
    enum CodingKeys: String, CodingKey {
        case weekdaysMask
        case intervalWeeks
        case anchorDate
    }
    
    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let mask = Self.decodeInt(from: container, key: .weekdaysMask) ?? 127
        let interval = Self.decodeInt(from: container, key: .intervalWeeks) ?? 1
        let date = Self.decodeDate(from: container, key: .anchorDate) ?? .now
        self.init(weekdaysMask: mask, intervalWeeks: interval, anchorDate: date)
    }
    
    private static func decodeInt(from container: KeyedDecodingContainer<CodingKeys>, key: CodingKeys) -> Int? {
        if let value = try? container.decodeIfPresent(Int.self, forKey: key) { return value }
        if let raw = try? container.decodeIfPresent(String.self, forKey: key) { return Int(raw.trimmingCharacters(in: .whitespacesAndNewlines)) }
        return nil
    }
    
    private static func decodeDate(from container: KeyedDecodingContainer<CodingKeys>, key: CodingKeys) -> Date? {
        if let date = try? container.decodeIfPresent(Date.self, forKey: key) { return date }
        if let seconds = try? container.decodeIfPresent(Double.self, forKey: key) { return Date(timeIntervalSince1970: seconds) }
        if let raw = try? container.decodeIfPresent(String.self, forKey: key) {
            let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            let formatter = DateFormatter()
            formatter.calendar = Calendar(identifier: .gregorian)
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.timeZone = TimeZone(secondsFromGMT: 0)
            formatter.dateFormat = "yyyy-MM-dd"
            return formatter.date(from: trimmed) ?? ISO8601DateFormatter().date(from: trimmed)
        }
        return nil
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
    public let weeklyRecurrence: WeeklyRecurrenceConfig?
    public let intervalDays: Int?
    
    /// Khởi tạo cấu hình chu kỳ.
    /// - Parameters:
    ///   - daysOn: Số ngày uống.
    ///   - daysOff: Số ngày nghỉ.
    ///   - isContinuous: Có uống liên tục hay không.
    ///   - durationMonths: Tổng thời hạn.
    public init(
        daysOn: Int,
        daysOff: Int,
        isContinuous: Bool = false,
        durationMonths: Int? = nil,
        weeklyRecurrence: WeeklyRecurrenceConfig? = nil,
        intervalDays: Int? = nil
    ) {
        if isContinuous {
            self.daysOn = 1
            self.daysOff = 0
            self.isContinuous = true
            self.durationMonths = durationMonths
            self.weeklyRecurrence = weeklyRecurrence
            self.intervalDays = nil
            return
        }
        
        let safeDaysOn = max(1, min(3650, daysOn))
        let safeDaysOff = max(0, min(3650, daysOff))
        let safeDurationMonths = durationMonths.map { max(1, min(120, $0)) }
        let safeIntervalDays = intervalDays.map { max(2, min(3650, $0)) }
        
        self.daysOn = safeDaysOn
        self.daysOff = safeDaysOff
        self.isContinuous = false
        self.durationMonths = safeDurationMonths
        self.weeklyRecurrence = weeklyRecurrence
        self.intervalDays = safeIntervalDays
    }
    
    enum CodingKeys: String, CodingKey {
        case daysOn
        case daysOff
        case isContinuous
        case durationMonths
        case weeklyRecurrence
        case intervalDays
    }
    
    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let daysOn = Self.decodeInt(from: container, key: .daysOn) ?? 1
        let daysOff = Self.decodeInt(from: container, key: .daysOff) ?? 0
        let isContinuous = Self.decodeBool(from: container, key: .isContinuous) ?? false
        let durationMonths = Self.decodeInt(from: container, key: .durationMonths)
        let weekly = try? container.decodeIfPresent(WeeklyRecurrenceConfig.self, forKey: .weeklyRecurrence)
        let intervalDays = Self.decodeInt(from: container, key: .intervalDays)
        self.init(daysOn: daysOn, daysOff: daysOff, isContinuous: isContinuous, durationMonths: durationMonths, weeklyRecurrence: weekly ?? nil, intervalDays: intervalDays)
    }
    
    private static func decodeInt(from container: KeyedDecodingContainer<CodingKeys>, key: CodingKeys) -> Int? {
        if let value = try? container.decodeIfPresent(Int.self, forKey: key) { return value }
        if let raw = try? container.decodeIfPresent(String.self, forKey: key) { return Int(raw.trimmingCharacters(in: .whitespacesAndNewlines)) }
        return nil
    }
    
    private static func decodeBool(from container: KeyedDecodingContainer<CodingKeys>, key: CodingKeys) -> Bool? {
        if let value = try? container.decodeIfPresent(Bool.self, forKey: key) { return value }
        if let intVal = try? container.decodeIfPresent(Int.self, forKey: key) { return intVal != 0 }
        if let raw = try? container.decodeIfPresent(String.self, forKey: key) {
            let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            if ["true", "1", "yes"].contains(trimmed) { return true }
            if ["false", "0", "no"].contains(trimmed) { return false }
        }
        return nil
    }
    
    /// Cấu hình mặc định cho việc uống liên tục.
    public static let continuous = CycleConfig(daysOn: 1, daysOff: 0, isContinuous: true, weeklyRecurrence: nil)
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

    public var lastTakenLocalDate: String?
    
    public var updatedAtEpochMs: Int64
    
    public var deletedAtEpochMs: Int64?
    
    /// Liên kết tới học viên/khách hàng (Coach Mode).
    public var client: ClientProfile?
    
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
        lastTakenLocalDate: String? = nil,
        updatedAtEpochMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        deletedAtEpochMs: Int64? = nil,
        client: ClientProfile? = nil
    ) {
        self.id = id
        self.name = name
        self.startDate = startDate
        self.cycleConfig = cycleConfig
        self.dailyDose = dailyDose
        self.intakeTime = intakeTime
        self.lastTakenLocalDate = lastTakenLocalDate
        self.updatedAtEpochMs = updatedAtEpochMs
        self.deletedAtEpochMs = deletedAtEpochMs
        self.client = client
        self.intakeRecords = []
    }
    
    public var instruction: String? {
        let normalizedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalizedName.isEmpty else { return nil }
        
        let reference = SupplementDictionary.references.first {
            $0.name.compare(normalizedName, options: [.caseInsensitive, .diacriticInsensitive]) == .orderedSame
        }
        return reference?.advice
    }
}

/// Hồ sơ học viên/khách hàng (Coach Mode).
@Model
public final class ClientProfile: Identifiable {
    @Attribute(.unique) public var id: UUID
    public var name: String
    
    public var avatarColorHex: String
    public var createdAt: Date
    
    @Relationship(deleteRule: .cascade, inverse: \UserSupplement.client)
    public var supplements: [UserSupplement] = []
    
    public init(
        id: UUID = UUID(),
        name: String,
        avatarColorHex: String = "#00D084",
        createdAt: Date = .now
    ) {
        self.id = id
        self.name = name
        self.avatarColorHex = avatarColorHex
        self.createdAt = createdAt
        self.supplements = []
    }
}

public enum IntakeStatus: String, Sendable {
    case taken = "Taken"
    case skipped = "Skipped"
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
    /// Khung giờ uống (HH:mm). Rỗng nếu dữ liệu cũ chưa có.
    public var intakeTime: String
    
    public var updatedAtEpochMs: Int64
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
        status: String = IntakeStatus.taken.rawValue,
        intakeTime: String = "",
        updatedAtEpochMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        supplement: UserSupplement? = nil
    ) {
        self.id = id
        self.date = date
        self.status = status
        self.intakeTime = intakeTime
        self.updatedAtEpochMs = updatedAtEpochMs
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
    /// Liều lượng khuyến nghị (VD: 200 mg).
    public let preferredDose: String?
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
