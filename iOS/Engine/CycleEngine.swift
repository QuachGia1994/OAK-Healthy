import Foundation

/// Các lỗi có thể xảy ra trong quá trình tính toán chu kỳ.
public enum CycleError: Error, Sendable {
    case invalidStartDate
    case calculationOverflow
}

/// Trạng thái của chu kỳ tại một thời điểm nhất định.
public enum CycleStatus: String, Sendable {
    case on = "Được uống"
    case off = "Nghỉ"
}

/// Giao thức định nghĩa việc tính toán chu kỳ.
public protocol CycleCalculating: Sendable {
    /// Tính toán trạng thái chu kỳ dựa trên ngày bắt đầu và cấu hình.
    /// - Parameters:
    ///   - startDate: Ngày bắt đầu chu kỳ.
    ///   - config: Cấu hình On/Off.
    ///   - currentDate: Ngày cần kiểm tra (mặc định là hiện tại).
    /// - Returns: Trạng thái `CycleStatus`.
    func determineStatus(
        for startDate: Date,
        config: CycleConfig,
        at currentDate: Date
    ) throws(CycleError) -> CycleStatus
}

/// Bộ máy tính toán chu kỳ thực phẩm bổ sung.
public struct CycleCalculator: CycleCalculating {
    
    public init() {}
    
    /// Tính toán xem ngày hiện tại thuộc giai đoạn "On" hay "Off".
    /// Tuân thủ quy tắc POP và Early Return.
    public func determineStatus(
        for startDate: Date,
        config: CycleConfig,
        at currentDate: Date = .now
    ) throws(CycleError) -> CycleStatus {
        let calendar = Calendar.current
        
        // 1. Kiểm tra thời hạn (Duration)
        if let months = config.durationMonths,
           let endDate = calendar.date(byAdding: .month, value: months, to: startDate) {
            guard currentDate <= endDate else { return .off }
        }
        
        // 2. Early return cho trường hợp uống liên tục
        guard !config.isContinuous else { return .on }
        
        // 3. Early return nếu ngày kiểm tra trước ngày bắt đầu
        // Sử dụng startOfDay để so sánh ngày chính xác
        let startDay = calendar.startOfDay(for: startDate)
        let currentDay = calendar.startOfDay(for: currentDate)
        guard currentDay >= startDay else { return .on }
        
        let components = calendar.dateComponents([.day], from: startDay, to: currentDay)
        
        guard let daysElapsed = components.day else {
            throw CycleError.calculationOverflow
        }
        
        return calculateStatus(daysElapsed: daysElapsed, config: config)
    }
    
    /// Logic tính toán số ngày dựa trên modulo.
    private func calculateStatus(daysElapsed: Int, config: CycleConfig) -> CycleStatus {
        let totalCycleDays = config.daysOn + config.daysOff
        
        // Tránh chia cho 0 mặc dù config hợp lệ sẽ không bao giờ bằng 0
        guard totalCycleDays > 0 else { return .on }
        
        let dayInCycle = daysElapsed % totalCycleDays
        
        // Nếu số ngày đã qua nằm trong khoảng daysOn thì là 'on'
        return dayInCycle < config.daysOn ? .on : .off
    }
}
