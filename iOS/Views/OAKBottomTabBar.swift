import SwiftUI

struct OAKBottomTabBar: View {
    @Binding var selectedTab: Int
    let homeBadgeCount: Int
    
    @Namespace private var selectionAnimation
    
    var body: some View {
        HStack(spacing: 8) {
            item(
                title: "tab_home".localized,
                systemImage: "house.fill",
                tab: 0,
                badgeCount: homeBadgeCount
            )
            item(
                title: "tab_stack".localized,
                systemImage: "square.stack.3d.up.fill",
                tab: 1
            )
            item(
                title: "tab_history".localized,
                systemImage: "clock.fill",
                tab: 2
            )
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 10)
        .frame(maxWidth: 420)
        .frame(maxWidth: .infinity)
        .oakCardStyle(
            .glass,
            cornerRadius: 30,
            strokeOpacity: 0.14,
            shadowOpacity: 0.18,
            shadowRadius: 20,
            shadowY: 10
        )
    }
    
    private func item(
        title: String,
        systemImage: String,
        tab: Int,
        badgeCount: Int = 0
    ) -> some View {
        let isSelected = selectedTab == tab
        
        return Button {
            withAnimation(.snappy(duration: 0.26, extraBounce: 0.08)) {
                selectedTab = tab
            }
        } label: {
            VStack(spacing: 5) {
                ZStack(alignment: .topTrailing) {
                    Image(systemName: systemImage)
                        .font(.system(size: 22, weight: .semibold))
                        .frame(height: 22)
                    
                    if badgeCount > 0 {
                        badge(count: badgeCount)
                            .offset(x: 12, y: -8)
                    }
                }
                .frame(maxWidth: .infinity)
                
                Text(title)
                    .font(.system(size: 13, weight: isSelected ? .semibold : .medium))
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
            }
            .foregroundStyle(isSelected ? Color.accentColor : Color.primary.opacity(0.78))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .padding(.horizontal, 8)
            .background {
                if isSelected {
                    Capsule()
                        .fill(.regularMaterial)
                        .overlay(
                            Capsule()
                                .stroke(Color.white.opacity(0.10), lineWidth: 1)
                        )
                        .shadow(color: .black.opacity(0.14), radius: 10, x: 0, y: 5)
                        .matchedGeometryEffect(id: "oak-tab-selection", in: selectionAnimation)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
    
    private func badge(count: Int) -> some View {
        let text = count > 99 ? "99+" : "\(count)"
        return Text(text)
            .font(.system(size: 10, weight: .bold))
            .foregroundStyle(.white)
            .padding(.horizontal, text.count > 2 ? 6 : 5)
            .frame(height: 18)
            .background(
                Capsule()
                    .fill(Color.red)
            )
    }
}
