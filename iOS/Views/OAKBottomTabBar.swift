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
                                    Color.white.opacity(colorScheme == .dark ? 0.16 : 0.24),
                                    Color.clear,
                                    Color.black.opacity(colorScheme == .dark ? 0.10 : 0.04)
                                ],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )
                }
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
                .overlay(alignment: .bottom) {
                    Capsule()
                        .fill(Color.black.opacity(colorScheme == .dark ? 0.16 : 0.06))
                        .frame(height: 10)
                        .blur(radius: 10)
                        .padding(.horizontal, 22)
                        .padding(.bottom, 3)
                }
                .shadow(color: Color.black.opacity(colorScheme == .dark ? 0.30 : 0.12), radius: 30, x: 0, y: 16)
                .shadow(color: Color.black.opacity(colorScheme == .dark ? 0.16 : 0.06), radius: 10, x: 0, y: 3)
                .shadow(color: Color.accentColor.opacity(0.12), radius: 22, x: 0, y: 6)
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
            .padding(.vertical, isSelected ? 13 : 11)
            .padding(.horizontal, isSelected ? 12 : 10)
            .background {
                if isSelected {
                    Capsule()
                        .fill(.regularMaterial)
                        .overlay {
                            Capsule()
                                .fill(
                                    LinearGradient(
                                        colors: [
                                            Color.accentColor.opacity(0.78),
                                            Color.accentColor.opacity(0.50),
                                            Color.white.opacity(0.12)
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
                                .fill(Color.white.opacity(0.18))
                                .frame(height: 1.5)
                                .padding(.horizontal, 16)
                                .padding(.top, 1)
                        }
                        .overlay(alignment: .bottom) {
                            Capsule()
                                .fill(Color.black.opacity(0.14))
                                .frame(height: 10)
                                .blur(radius: 9)
                                .padding(.horizontal, 18)
                                .padding(.bottom, 2)
                        }
                        .shadow(color: Color.accentColor.opacity(0.34), radius: 18, x: 0, y: 8)
                        .shadow(color: .black.opacity(0.18), radius: 14, x: 0, y: 8)
                        .matchedGeometryEffect(id: "oak-tab-selection", in: selectionAnimation)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(OAKTabPressStyle())
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

private struct OAKTabPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.965 : 1.0)
            .offset(y: configuration.isPressed ? 1 : 0)
            .animation(
                .spring(response: 0.24, dampingFraction: 0.72, blendDuration: 0.12),
                value: configuration.isPressed
            )
    }
}
