import Foundation

/// Từ điển cục bộ chứa thông tin mặc định của các loại thực phẩm bổ sung.
///
/// Cung cấp dữ liệu tĩnh cho tính năng Smart Auto-Suggest, bao gồm thời điểm uống tối ưu và chu kỳ mặc định.
public enum SupplementDictionary {
    /// Danh sách các chất bổ sung phổ biến.
    public static let references: [SupplementReference] = [
        SupplementReference(
            name: "Ashwagandha",
            advice: "Supports stress reduction and sleep quality.",
            preferredTime: "21:00",
            preferredDose: nil,
            defaultCycle: CycleConfig(daysOn: 28, daysOff: 7)
        ),
        SupplementReference(
            name: "Boron",
            advice: "Cycle it to support hormone optimization.",
            preferredTime: "08:00",
            preferredDose: nil,
            defaultCycle: CycleConfig(daysOn: 14, daysOff: 7)
        ),
        SupplementReference(
            name: "Caffeine",
            advice: "Boosts focus and energy for workouts and trading sessions. Take ~30 minutes pre-workout.",
            preferredTime: "07:30",
            preferredDose: "200 mg",
            defaultCycle: .continuous
        ),
        SupplementReference(
            name: "Vitamin D3",
            advice: "Take with a fat-containing meal for better absorption.",
            preferredTime: "08:00",
            preferredDose: nil,
            defaultCycle: .continuous
        ),
        SupplementReference(
            name: "Omega-3",
            advice: "Take after a fatty meal for best absorption.",
            preferredTime: "12:00",
            preferredDose: nil,
            defaultCycle: .continuous
        ),
        SupplementReference(
            name: "CoQ10",
            advice: "Supports cellular energy. Take after a meal.",
            preferredTime: "12:00",
            preferredDose: nil,
            defaultCycle: .continuous
        ),
        SupplementReference(
            name: "Creatine",
            advice: "Supports strength and performance. Take daily.",
            preferredTime: "12:00",
            preferredDose: nil,
            defaultCycle: .continuous
        ),
        SupplementReference(
            name: "Magnesium",
            advice: "Supports muscles and nervous system. Take before sleep.",
            preferredTime: "21:00",
            preferredDose: nil,
            defaultCycle: .continuous
        ),
        SupplementReference(
            name: "Zinc",
            advice: "Avoid taking on an empty stomach.",
            preferredTime: "21:00",
            preferredDose: nil,
            defaultCycle: .continuous
        )
    ]
}
