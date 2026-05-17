import Foundation

/// Từ điển cục bộ chứa thông tin mặc định của các loại thực phẩm bổ sung.
///
/// Cung cấp dữ liệu tĩnh cho tính năng Smart Auto-Suggest, bao gồm thời điểm uống tối ưu và chu kỳ mặc định.
public enum SupplementDictionary {
    /// Danh sách các chất bổ sung phổ biến.
    public static let references: [SupplementReference] = [
        SupplementReference(
            name: "Ashwagandha",
            advice: "supplement_advice_ashwagandha",
            preferredTime: "21:00",
            preferredDose: nil,
            defaultCycle: CycleConfig(daysOn: 28, daysOff: 7)
        ),
        SupplementReference(
            name: "Boron",
            advice: "supplement_advice_boron",
            preferredTime: "08:00",
            preferredDose: nil,
            defaultCycle: CycleConfig(daysOn: 14, daysOff: 7)
        ),
        SupplementReference(
            name: "Caffeine",
            advice: "supplement_advice_caffeine",
            preferredTime: "07:30",
            preferredDose: "200 mg",
            defaultCycle: .continuous
        ),
        SupplementReference(
            name: "Vitamin D3",
            advice: "supplement_advice_vitamin_d3",
            preferredTime: "08:00",
            preferredDose: nil,
            defaultCycle: .continuous
        ),
        SupplementReference(
            name: "Omega-3",
            advice: "supplement_advice_omega_3",
            preferredTime: "12:00",
            preferredDose: nil,
            defaultCycle: .continuous
        ),
        SupplementReference(
            name: "CoQ10",
            advice: "supplement_advice_coq10",
            preferredTime: "12:00",
            preferredDose: nil,
            defaultCycle: .continuous
        ),
        SupplementReference(
            name: "Creatine",
            advice: "supplement_advice_creatine",
            preferredTime: "12:00",
            preferredDose: nil,
            defaultCycle: .continuous
        ),
        SupplementReference(
            name: "NAC",
            advice: "supplement_advice_nac",
            preferredTime: "22:00",
            preferredDose: nil,
            defaultCycle: .continuous
        ),
        SupplementReference(
            name: "Magnesium",
            advice: "supplement_advice_magnesium",
            preferredTime: "21:00",
            preferredDose: nil,
            defaultCycle: .continuous
        ),
        SupplementReference(
            name: "Zinc",
            advice: "supplement_advice_zinc",
            preferredTime: "21:00",
            preferredDose: nil,
            defaultCycle: .continuous
        )
    ]
}
