import Foundation

/// Từ điển cục bộ chứa thông tin mặc định của các loại thực phẩm bổ sung.
///
/// Cung cấp dữ liệu tĩnh cho tính năng Smart Auto-Suggest, bao gồm thời điểm uống tối ưu và chu kỳ mặc định.
public enum SupplementDictionary {
    /// Danh sách các chất bổ sung phổ biến.
    public static let references: [SupplementReference] = [
        SupplementReference(
            name: "Ashwagandha",
            advice: "Giúp giảm căng thẳng và cải thiện giấc ngủ.",
            preferredTime: "21:00",
            defaultCycle: CycleConfig(daysOn: 56, daysOff: 14)
        ),
        SupplementReference(
            name: "Boron",
            advice: "Nên uống theo chu kỳ để tối ưu nội tiết tố.",
            preferredTime: "08:00",
            defaultCycle: CycleConfig(daysOn: 14, daysOff: 7)
        ),
        SupplementReference(
            name: "Vitamin D3",
            advice: "Nên uống cùng bữa ăn có chất béo.",
            preferredTime: "08:00",
            defaultCycle: .continuous
        ),
        SupplementReference(
            name: "Omega-3",
            advice: "Nên uống sau bữa ăn giàu chất béo để hấp thu tốt nhất.",
            preferredTime: "12:00",
            defaultCycle: .continuous
        ),
        SupplementReference(
            name: "CoQ10",
            advice: "Cải thiện năng lượng tế bào, uống sau bữa ăn.",
            preferredTime: "12:00",
            defaultCycle: .continuous
        ),
        SupplementReference(
            name: "Magnesium",
            advice: "Hỗ trợ cơ bắp và thần kinh, uống trước khi ngủ.",
            preferredTime: "21:00",
            defaultCycle: .continuous
        ),
        SupplementReference(
            name: "Zinc",
            advice: "Không nên uống khi bụng đói.",
            preferredTime: "21:00",
            defaultCycle: CycleConfig(daysOn: 30, daysOff: 7)
        )
    ]
}
