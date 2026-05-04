import SwiftUI
import SwiftData

/// Màn hình Cài đặt và Thông tin ứng dụng (iOS).
public struct SettingsView: View {
    @Query(sort: \UserSupplement.name) private var supplements: [UserSupplement]
    
    public init() {}
    
    public var body: some View {
        NavigationStack {
            List {
                Section {
                    VStack {
                        OAKLogoView()
                            .padding(.vertical, 20)
                        
                        Text("Dedicated to my Sư phụ and Benefactors")
                            .font(.subheadline)
                            .italic()
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.bottom, 10)
                    }
                    .frame(maxWidth: .infinity)
                }
                .listRowBackground(Color.clear)

                Section("Danh sách của tôi") {
                    if supplements.isEmpty {
                        Text("Chưa có chất bổ sung nào.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(supplements) { supplement in
                            VStack(alignment: .leading) {
                                Text(supplement.name)
                                    .font(.headline)
                                Text(getCycleSummary(for: supplement))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
                
                Section("Hướng dẫn sử dụng") {
                    VStack(alignment: .leading, spacing: 8) {
                        GuideRow(number: "1", text: "Nhấn (+) để thêm thực phẩm bổ sung mới.")
                        GuideRow(number: "2", text: "Chọn từ gợi ý để tự động điền chu kỳ.")
                        GuideRow(number: "3", text: "Tích chọn vòng tròn ở Dashboard sau khi uống.")
                        GuideRow(number: "4", text: "Theo dõi chu kỳ On/Off và biểu đồ ở tab Lịch sử.")
                    }
                    .padding(.vertical, 4)
                }
                
                Section("Giới thiệu") {
                    Text("OAK Healthy - Trợ lý quản lý thực phẩm bổ sung chuyên nghiệp dành cho Trader và Vận động viên. Giúp tối ưu hóa hiệu suất và duy trì kỷ luật sức khỏe.")
                        .font(.body)
                        .foregroundStyle(.secondary)
                }
                
                Section("Bản quyền & Tác giả") {
                    VStack(alignment: .leading, spacing: 4) {
                        LabeledContent("Tên app", value: "OAK Healthy v1.0")
                        LabeledContent("Tác giả", value: "Mr. Phong (Personal Trader)")
                        Text("© 2026 OAK Healthy. All rights reserved.")
                            .font(.caption)
                            .foregroundStyle(.tertiary)
                            .padding(.top, 4)
                    }
                }
            }
            .navigationTitle("Cài đặt")
        }
    }
    
    private func getCycleSummary(for supplement: UserSupplement) -> String {
        let config = supplement.cycleConfig
        let cycleEngine = CycleCalculator()
        let status = try? cycleEngine.determineStatus(for: supplement.startDate, config: config, at: .now)
        let statusText = status == .on ? "Đang trong chu kỳ" : "Đang trong kỳ nghỉ"
        
        if config.isContinuous {
            return "Uống liên tục"
        }
        return "\(statusText): \(config.daysOn) ngày uống / \(config.daysOff) ngày nghỉ"
    }
}

private struct GuideRow: View {
    let number: String
    let text: String
    
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Text(number)
                .font(.caption)
                .fontWeight(.bold)
                .foregroundStyle(.white)
                .frame(width: 20, height: 20)
                .background(Color.blue)
                .clipShape(Circle())
            
            Text(text)
                .font(.subheadline)
        }
    }
}

#Preview {
    SettingsView()
}
