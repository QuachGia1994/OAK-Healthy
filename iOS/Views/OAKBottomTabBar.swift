import SwiftUI

struct OAKBottomTabBar: View {
    @Binding var selectedTab: Int
    let homeBadgeCount: Int
    
    @Namespace private var selectionAnimation
    @Environment(\.colorScheme) private var colorScheme
    
    var body: some View {
        HStack(spacing: 10) {
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
        .padding(.horizontal, 12)
        .padding(.vertical, 12)
        .frame(maxWidth: 420)
        .frame(maxWidth: .infinity)
        .background {
            RoundedRectangle(cornerRadius: 32, style: .continuous)
                .fill(.ultraThinMaterial)
                .overlay {
                    RoundedRectangle(cornerRadius: 32, style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [
                                    Color.white.opacity(colorScheme == .dark ? 0.14 : 0.22),
                                    Color.white.opacity(0.03)
                                ],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )
                }
                .overlay {
                    RoundedRectangle(cornerRadius: 32, style: .continuous)
                        .stroke(Color.white.opacity(colorScheme == .dark ? 0.12 : 0.18), lineWidth: 1)
                }
                .overlay(alignment: .top) {
                    Capsule()
                        .fill(Color.white.opacity(colorScheme == .dark ? 0.16 : 0.24))
                        .frame(height: 1)
                        .padding(.horizontal, 26)
                        .padding(.top, 1)
                }
                .shadow(color: Color.black.opacity(colorScheme == .dark ? 0.24 : 0.10), radius: 24, x: 0, y: 12)
                .shadow(color: Color.accentColor.opacity(0.10), radius: 18, x: 0, y: 4)
        }
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
            VStack(spacing: 4) {
                ZStack(alignment: .topTrailing) {
                    Image(systemName: systemImage)
                        .font(.system(size: isSelected ? 23 : 21, weight: .semibold))
                        .symbolRenderingMode(.hierarchical)
                        .frame(width: 32, height: 24)
                        .scaleEffect(isSelected ? 1.02 : 1.0)
                    
                    if badgeCount > 0 {
                        badge(count: badgeCount)
                            .offset(x: 14, y: -9)
                    }
                }
                .frame(maxWidth: .infinity)
                
                Text(title)
                    .font(.system(size: 12.5, weight: isSelected ? .semibold : .medium))
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
            }
            .foregroundStyle(isSelected ? Color.white : Color.primary.opacity(0.76))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 11)
            .padding(.horizontal, 10)
            .background {
                if isSelected {
                    Capsule()
                        .fill(.regularMaterial)
                        .overlay {
                            Capsule()
                                .fill(
                                    LinearGradient(
                                        colors: [
                                            Color.accentColor.opacity(0.70),
                                            Color.accentColor.opacity(0.42),
                                            Color.white.opacity(0.10)
                                        ],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    )
                                )
                        }
                        .overlay(
                            Capsule()
                                .stroke(Color.white.opacity(0.22), lineWidth: 1)
                        )
                        .overlay(alignment: .top) {
                            Capsule()
                                .fill(Color.white.opacity(0.14))
                                .frame(height: 1)
                                .padding(.horizontal, 18)
                                .padding(.top, 1)
                        }
                        .shadow(color: Color.accentColor.opacity(0.30), radius: 14, x: 0, y: 6)
                        .shadow(color: .black.opacity(0.16), radius: 12, x: 0, y: 6)
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
                    .fill(
                        LinearGradient(
                            colors: [Color.red, Color.orange],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
            )
            .overlay(
                Capsule()
                    .stroke(Color.white.opacity(0.16), lineWidth: 1)
            )
            .shadow(color: Color.red.opacity(0.22), radius: 8, x: 0, y: 3)
    }
}
