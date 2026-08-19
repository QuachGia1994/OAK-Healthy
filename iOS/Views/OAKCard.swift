import SwiftUI

enum OAKCardVariant: Sendable {
    case paper
}

private struct OAKSecondaryTextModifier: ViewModifier {
    @Environment(\.colorScheme) private var colorScheme

    func body(content: Content) -> some View {
        content.foregroundStyle(OAKPalette.secondaryText(for: colorScheme))
    }
}

private struct OAKTertiaryTextModifier: ViewModifier {
    @Environment(\.colorScheme) private var colorScheme

    func body(content: Content) -> some View {
        content.foregroundStyle(OAKPalette.secondaryText(for: colorScheme).opacity(0.78))
    }
}

private struct OAKCardStyleModifier: ViewModifier {
    @Environment(\.colorScheme) private var colorScheme
    let cornerRadius: CGFloat
    let strokeOpacity: Double
    let shadowOpacity: Double
    let shadowRadius: CGFloat
    let shadowY: CGFloat

    func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: min(cornerRadius, 16), style: .continuous)
        content
            .background(shape.fill(OAKPalette.surface(for: colorScheme)))
            .overlay(shape.stroke(OAKPalette.divider(for: colorScheme).opacity(max(0.55, strokeOpacity)), lineWidth: 0.75))
            .shadow(
                color: .black.opacity(min(shadowOpacity, 0.035)),
                radius: min(shadowRadius, 6),
                x: 0,
                y: min(shadowY, 3)
            )
    }
}

extension View {
    func oakSecondaryText() -> some View {
        modifier(OAKSecondaryTextModifier())
    }

    func oakTertiaryText() -> some View {
        modifier(OAKTertiaryTextModifier())
    }

    func oakTouchTarget() -> some View {
        frame(minWidth: 44, minHeight: 44)
    }

    func oakCardStyle(
        _ variant: OAKCardVariant = .paper,
        cornerRadius: CGFloat = 14,
        strokeOpacity: Double = 0.12,
        shadowOpacity: Double = 0,
        shadowRadius: CGFloat = 0,
        shadowY: CGFloat = 0
    ) -> some View {
        modifier(
            OAKCardStyleModifier(
                cornerRadius: cornerRadius,
                strokeOpacity: strokeOpacity,
                shadowOpacity: shadowOpacity,
                shadowRadius: shadowRadius,
                shadowY: shadowY
            )
        )
    }
}

struct OAKBackgroundModifier: ViewModifier {
    @Environment(\.colorScheme) private var colorScheme

    func body(content: Content) -> some View {
        content.background(OAKPalette.background(for: colorScheme).ignoresSafeArea())
    }
}

extension View {
    func oakBackground() -> some View {
        modifier(OAKBackgroundModifier())
    }
}

extension Font {
    static func oakDisplay(size: CGFloat, weight: Font.Weight = .semibold) -> Font {
        .system(size: size, weight: weight, design: .serif)
    }
}
