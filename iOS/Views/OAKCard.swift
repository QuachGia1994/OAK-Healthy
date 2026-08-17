import SwiftUI

enum OAKPalette {
    static let accent = Color(red: 0.1216, green: 0.4196, blue: 0.3020)
    static let accentDark = Color(red: 0.4902, green: 0.8275, blue: 0.6588)

    static let paper = Color(red: 0.9647, green: 0.9686, blue: 0.9608)
    static let paperRaised = Color.white
    static let paperMuted = Color(red: 0.9333, green: 0.9451, blue: 0.9333)
    static let ink = Color(red: 0.0667, green: 0.0824, blue: 0.0745)
    static let inkMuted = Color(red: 0.3490, green: 0.3804, blue: 0.3608)
    static let hairline = Color(red: 0.8627, green: 0.8863, blue: 0.8667)

    static let paperDark = Color(red: 0.0431, green: 0.0588, blue: 0.0510)
    static let paperRaisedDark = Color(red: 0.0667, green: 0.0902, blue: 0.0784)
    static let paperMutedDark = Color(red: 0.0941, green: 0.1255, blue: 0.1098)
    static let inkDark = Color(red: 0.9490, green: 0.9608, blue: 0.9529)
    static let inkMutedDark = Color(red: 0.6667, green: 0.7098, blue: 0.6824)
    static let hairlineDark = Color(red: 0.2510, green: 0.3137, blue: 0.2784)

    static let due = Color(red: 0.27, green: 0.42, blue: 0.55)
    static let missed = Color(red: 0.71, green: 0.28, blue: 0.25)
    static let taken = accent
    static let skipped = Color(red: 0.60, green: 0.40, blue: 0.12)
    static let dueDark = Color(red: 0.57, green: 0.68, blue: 0.78)
    static let missedDark = Color(red: 0.89, green: 0.55, blue: 0.51)
    static let takenDark = accentDark
    static let skippedDark = Color(red: 0.82, green: 0.64, blue: 0.37)

    static func background(for colorScheme: ColorScheme) -> Color { colorScheme == .dark ? paperDark : paper }
    static func surface(for colorScheme: ColorScheme) -> Color { colorScheme == .dark ? paperRaisedDark : paperRaised }
    static func mutedSurface(for colorScheme: ColorScheme) -> Color { colorScheme == .dark ? paperMutedDark : paperMuted }
    static func primaryText(for colorScheme: ColorScheme) -> Color { colorScheme == .dark ? inkDark : ink }
    static func secondaryText(for colorScheme: ColorScheme) -> Color { colorScheme == .dark ? inkMutedDark : inkMuted }
    static func divider(for colorScheme: ColorScheme) -> Color { colorScheme == .dark ? hairlineDark : hairline }
    static func due(for colorScheme: ColorScheme) -> Color { colorScheme == .dark ? dueDark : due }
    static func missed(for colorScheme: ColorScheme) -> Color { colorScheme == .dark ? missedDark : missed }
    static func taken(for colorScheme: ColorScheme) -> Color { colorScheme == .dark ? takenDark : taken }
    static func skipped(for colorScheme: ColorScheme) -> Color { colorScheme == .dark ? skippedDark : skipped }
}

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
