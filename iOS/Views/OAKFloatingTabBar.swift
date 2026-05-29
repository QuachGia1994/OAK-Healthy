import SwiftUI

struct OAKFloatingTabBar: View {
    @Binding var selectedTab: Int
    let homeBadgeCount: Int
    
    var body: some View {
        HStack(spacing: 0) {
            item(titleKey: "tab_home", systemImage: "house.fill", tab: 0, badgeCount: homeBadgeCount)
            item(titleKey: "tab_stack", systemImage: "list.bullet", tab: 1)
            item(titleKey: "tab_history", systemImage: "clock.fill", tab: 2)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 12)
        .oakCardStyle(
            .glass,
            cornerRadius: 28,
            strokeOpacity: 0.14,
            shadowOpacity: 0.16,
            shadowRadius: 18,
            shadowY: 10
        )
    }
    
    private func item(titleKey: String, systemImage: String, tab: Int, badgeCount: Int? = nil) -> some View {
        let isSelected = selectedTab == tab
        return Button {
            withAnimation(.spring(response: 0.22, dampingFraction: 0.85)) {
                selectedTab = tab
            }
        } label: {
            VStack(spacing: 4) {
                ZStack(alignment: .topTrailing) {
                    Image(systemName: systemImage)
                        .font(.system(size: 18, weight: .semibold))
                        .frame(height: 20)
                        .frame(maxWidth: .infinity)
                    
                    if let badgeCount, badgeCount > 0 {
                        Text("\(min(badgeCount, 99))")
                            .font(.system(size: 11, weight: .bold, design: .rounded))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Capsule().fill(.red))
                            .offset(x: 10, y: -8)
                    }
                }
                
                Text(titleKey.localized)
                    .font(.system(size: 11, weight: .semibold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
            }
            .foregroundStyle(isSelected ? Color.primary : Color.secondary)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
            .background(
                Group {
                    if isSelected {
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .fill(Color.primary.opacity(0.08))
                    }
                }
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(titleKey.localized))
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}
