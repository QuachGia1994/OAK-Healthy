import SwiftUI

public struct UserGuideView: View {
    public init() {}
    
    public var body: some View {
        List {
            Section {
                GuideRowView(
                    systemImage: "plus.circle.fill",
                    title: "user_guide_section_1_title".localized,
                    detailText: "settings_guide_1".localized
                )
            } header: {
                EmptyView()
            }
            
            Section {
                GuideRowView(
                    systemImage: "arrow.triangle.2.circlepath",
                    title: "user_guide_section_2_title".localized,
                    detailText: "settings_guide_2".localized
                )
            } header: {
                EmptyView()
            }
            
            Section {
                GuideRowView(
                    systemImage: "eye.slash",
                    title: "user_guide_section_3_title".localized,
                    detailText: "settings_guide_3".localized
                )
            } header: {
                EmptyView()
            }
            
            Section {
                GuideRowView(
                    systemImage: "bell.badge.fill",
                    title: "user_guide_section_4_title".localized,
                    detailText: "settings_guide_4".localized
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
    let detailText: String
    
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: systemImage)
                .foregroundStyle(.blue)
                .font(.title3)
                .frame(width: 28)
            VStack(alignment: .leading, spacing: 6) {
                Text(title)
                    .font(.headline)
                Text(detailText)
                    .font(.body)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 6)
    }
}
