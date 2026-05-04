import Foundation

/// Từ điển cục bộ chứa thông tin mặc định của các loại thực phẩm bổ sung.
///
/// Cung cấp dữ liệu tĩnh cho tính năng Smart Auto-Suggest, bao gồm thời điểm uống tối ưu và chu kỳ mặc định.
public enum SupplementDictionary {
    /// Danh sách các chất bổ sung phổ biến.
    public static let references: [SupplementReference] = [
        SupplementReference(
            name: "Ashwagandha",
            preferredTime: .night,
            defaultCycle: CycleConfig(daysOn: 56, daysOff: 14)
        ),
        SupplementReference(
            name: "Boron",
            preferredTime: .morning,
            defaultCycle: CycleConfig(daysOn: 14, daysOff: 7)
        ),
        SupplementReference(
            name: "Vitamin D3",
            preferredTime: .morning,
            defaultCycle: .continuous
        ),
        SupplementReference(
            name: "Magnesium",
            preferredTime: .night,
            defaultCycle: .continuous
        ),
        SupplementReference(
            name: "Creatine",
            preferredTime: .afternoon,
            defaultCycle: .continuous
        ),
        SupplementReference(
            name: "Zinc",
            preferredTime: .night,
            defaultCycle: CycleConfig(daysOn: 30, daysOff: 7)
        )
    ]
}
