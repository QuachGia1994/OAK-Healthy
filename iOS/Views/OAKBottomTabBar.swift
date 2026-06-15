import SwiftUI

struct OAKBottomTabBar: View {
    @Binding var selectedTab: Int
    let homeBadgeCount: Int
    
    @Namespace private var selectionAnimation
    @Environment(\.colorScheme) private var colorScheme
    
    var body: some View {
        GeometryReader { proxy in
            let metrics = metrics(for: proxy.size.width)
            HStack(spacing: metrics.itemSpacing) {
                item(
                    title: "tab_home".localized,
                    systemImage: "house.fill",
                    tab: 0,
                    metrics: metrics,
                    badgeCount: homeBadgeCount
                )
                item(
                    title: "tab_stack".localized,
                    systemImage: "square.stack.3d.up.fill",
                    tab: 1,
                    metrics: metrics
                )
                item(
                    title: "tab_history".localized,
                    systemImage: "clock.fill",
                    tab: 2,
                    metrics: metrics
                )
            }
            .padding(.horizontal, metrics.outerHorizontalPadding)
            .padding(.vertical, metrics.outerVerticalPadding)
            .frame(maxWidth: min(max(proxy.size.width, 0), metrics.maxWidth))
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background {
                RoundedRectangle(cornerRadius: metrics.containerCornerRadius, style: .continuous)
                    .fill(.ultraThinMaterial)
                    .overlay {
                        RoundedRectangle(cornerRadius: metrics.containerCornerRadius, style: .continuous)
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
                        RoundedRectangle(cornerRadius: metrics.containerCornerRadius, style: .continuous)
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
                        RoundedRectangle(cornerRadius: metrics.containerCornerRadius, style: .continuous)
                            .stroke(Color.white.opacity(colorScheme == .dark ? 0.12 : 0.18), lineWidth: 1)
                    }
                    .overlay(alignment: .top) {
                        Capsule()
                            .fill(Color.white.opacity(colorScheme == .dark ? 0.16 : 0.24))
                            .frame(height: 1)
                            .padding(.horizontal, metrics.outerHorizontalPadding + 14)
                            .padding(.top, 1)
                    }
                    .overlay(alignment: .bottom) {
                        Capsule()
                            .fill(Color.black.opacity(colorScheme == .dark ? 0.16 : 0.06))
                            .frame(height: 10)
                            .blur(radius: 10)
                            .padding(.horizontal, metrics.outerHorizontalPadding + 10)
                            .padding(.bottom, 3)
                    }
                    .overlay {
                        activeAura(metrics: metrics)
                    }
                    .shadow(color: Color.black.opacity(colorScheme == .dark ? 0.30 : 0.12), radius: 30, x: 0, y: 16)
                    .shadow(color: Color.black.opacity(colorScheme == .dark ? 0.16 : 0.06), radius: 10, x: 0, y: 3)
                    .shadow(color: Color.accentColor.opacity(0.12), radius: 22, x: 0, y: 6)
            }
        }
        .frame(height: 84)
    }
    
    private func item(
        title: String,
        systemImage: String,
        tab: Int,
        metrics: Metrics,
        badgeCount: Int = 0
    ) -> some View {
        let isSelected = selectedTab == tab
        
        return Button {
            withAnimation(.snappy(duration: 0.26, extraBounce: 0.08)) {
                selectedTab = tab
            }
        } label: {
            VStack(spacing: metrics.labelSpacing) {
                ZStack(alignment: .topTrailing) {
                    Image(systemName: systemImage)
                        .font(.system(size: isSelected ? metrics.selectedIconSize : metrics.iconSize, weight: .semibold))
                        .symbolRenderingMode(.hierarchical)
                        .frame(width: metrics.iconFrameWidth, height: metrics.iconFrameHeight)
                        .scaleEffect(isSelected ? 1.04 : 1.0)
                    
                    if badgeCount > 0 {
                        badge(count: badgeCount)
                            .offset(x: metrics.badgeOffsetX, y: metrics.badgeOffsetY)
                    }
                }
                .frame(maxWidth: .infinity)
                
                Text(title)
                    .font(.system(size: metrics.labelSize, weight: isSelected ? .semibold : .medium))
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
            }
            .foregroundStyle(isSelected ? Color.white : Color.primary.opacity(0.76))
            .frame(maxWidth: .infinity)
            .padding(.vertical, isSelected ? metrics.selectedVerticalPadding : metrics.verticalPadding)
            .padding(.horizontal, isSelected ? metrics.selectedHorizontalPadding : metrics.horizontalPadding)
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
                        .overlay {
                            Capsule()
                                .fill(
                                    RadialGradient(
                                        colors: [
                                            Color.white.opacity(colorScheme == .dark ? 0.22 : 0.16),
                                            Color.clear
                                        ],
                                        center: .topLeading,
                                        startRadius: 2,
                                        endRadius: metrics.pillLiquidRadius
                                    )
                                )
                                .blendMode(.screen)
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
                        .overlay(alignment: .leading) {
                            Capsule()
                                .fill(
                                    LinearGradient(
                                        colors: [
                                            Color.white.opacity(0.16),
                                            Color.clear
                                        ],
                                        startPoint: .leading,
                                        endPoint: .trailing
                                    )
                                )
                                .frame(width: metrics.pillSheenWidth)
                                .blur(radius: 5)
                                .padding(.leading, 10)
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
    
    private func activeAura(metrics: Metrics) -> some View {
        HStack(spacing: metrics.itemSpacing) {
            ForEach(0..<3, id: \.self) { tab in
                Color.clear
                    .frame(maxWidth: .infinity)
                    .overlay {
                        if selectedTab == tab {
                            Capsule()
                                .fill(
                                    RadialGradient(
                                        colors: [
                                            Color.accentColor.opacity(colorScheme == .dark ? 0.20 : 0.14),
                                            Color.accentColor.opacity(0.05),
                                            Color.clear
                                        ],
                                        center: .center,
                                        startRadius: 4,
                                        endRadius: metrics.auraRadius
                                    )
                                )
                                .frame(width: metrics.auraWidth, height: metrics.auraHeight)
                                .blur(radius: 10)
                                .matchedGeometryEffect(id: "oak-tab-aura", in: selectionAnimation)
                        }
                    }
            }
        }
        .padding(.horizontal, metrics.outerHorizontalPadding)
        .padding(.vertical, metrics.outerVerticalPadding)
    }
    
    private func metrics(for width: CGFloat) -> Metrics {
        if width <= 360 {
            return .small
        }
        if width >= 430 {
            return .large
        }
        return .regular
    }
}

private extension OAKBottomTabBar {
    struct Metrics {
        let itemSpacing: CGFloat
        let outerHorizontalPadding: CGFloat
        let outerVerticalPadding: CGFloat
        let horizontalPadding: CGFloat
        let selectedHorizontalPadding: CGFloat
        let verticalPadding: CGFloat
        let selectedVerticalPadding: CGFloat
        let iconSize: CGFloat
        let selectedIconSize: CGFloat
        let iconFrameWidth: CGFloat
        let iconFrameHeight: CGFloat
        let labelSize: CGFloat
        let labelSpacing: CGFloat
        let containerCornerRadius: CGFloat
        let badgeOffsetX: CGFloat
        let badgeOffsetY: CGFloat
        let auraWidth: CGFloat
        let auraHeight: CGFloat
        let auraRadius: CGFloat
        let pillSheenWidth: CGFloat
        let pillLiquidRadius: CGFloat
        let maxWidth: CGFloat
        
        static let small = Metrics(
            itemSpacing: 8,
            outerHorizontalPadding: 10,
            outerVerticalPadding: 10,
            horizontalPadding: 8,
            selectedHorizontalPadding: 10,
            verticalPadding: 10,
            selectedVerticalPadding: 12,
            iconSize: 20,
            selectedIconSize: 22,
            iconFrameWidth: 30,
            iconFrameHeight: 22,
            labelSize: 12,
            labelSpacing: 3,
            containerCornerRadius: 30,
            badgeOffsetX: 12,
            badgeOffsetY: -8,
            auraWidth: 84,
            auraHeight: 44,
            auraRadius: 56,
            pillSheenWidth: 34,
            pillLiquidRadius: 42,
            maxWidth: 380
        )
        
        static let regular = Metrics(
            itemSpacing: 10,
            outerHorizontalPadding: 12,
            outerVerticalPadding: 12,
            horizontalPadding: 10,
            selectedHorizontalPadding: 12,
            verticalPadding: 11,
            selectedVerticalPadding: 13,
            iconSize: 21,
            selectedIconSize: 23,
            iconFrameWidth: 32,
            iconFrameHeight: 24,
            labelSize: 12.5,
            labelSpacing: 4,
            containerCornerRadius: 32,
            badgeOffsetX: 14,
            badgeOffsetY: -9,
            auraWidth: 94,
            auraHeight: 48,
            auraRadius: 64,
            pillSheenWidth: 38,
            pillLiquidRadius: 46,
            maxWidth: 420
        )
        
        static let large = Metrics(
            itemSpacing: 12,
            outerHorizontalPadding: 14,
            outerVerticalPadding: 12,
            horizontalPadding: 12,
            selectedHorizontalPadding: 14,
            verticalPadding: 11,
            selectedVerticalPadding: 13,
            iconSize: 22,
            selectedIconSize: 24,
            iconFrameWidth: 34,
            iconFrameHeight: 24,
            labelSize: 13,
            labelSpacing: 4,
            containerCornerRadius: 34,
            badgeOffsetX: 14,
            badgeOffsetY: -9,
            auraWidth: 108,
            auraHeight: 52,
            auraRadius: 72,
            pillSheenWidth: 44,
            pillLiquidRadius: 52,
            maxWidth: 460
        )
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
