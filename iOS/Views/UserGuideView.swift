import SwiftUI

public struct UserGuideView: View {
    public init() {}
    
    public var body: some View {
        List {
            Section {
                GuideRowView(
                    systemImage: "plus.circle.fill",
                    title: "Mục 1: Thiết lập Stack",
                    body: "settings_guide_1".localized
                )
            } header: {
                EmptyView()
            }
            
            Section {
                GuideRowView(
                    systemImage: "arrow.triangle.2.circlepath",
                    title: "Mục 2: Đồng bộ đa thiết bị",
                    body: "settings_guide_2".localized
                )
            } header: {
                EmptyView()
            }
            
            Section {
                GuideRowView(
                    systemImage: "eye.slash",
                    title: "Mục 3: Bảo mật tối cao",
                    body: "settings_guide_3".localized
                )
            } header: {
                EmptyView()
            }
            
            Section {
                GuideRowView(
                    systemImage: "bell.badge.fill",
                    title: "Mục 4: Chẩn đoán thông báo",
                    body: "settings_guide_4".localized
                )
            } header: {
                EmptyView()
            }
        }
        .navigationTitle("user_guide_title".localized)
    }
}

private struct GuideRowView: View {
    let systemImage: String
    let title: String
    let body: String
    
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: systemImage)
                .foregroundStyle(.blue)
                .font(.title3)
                .frame(width: 28)
            VStack(alignment: .leading, spacing: 6) {
                Text(title)
                    .font(.headline)
                Text(body)
                    .font(.body)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 6)
    }
}
