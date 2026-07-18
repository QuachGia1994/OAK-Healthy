import SwiftUI

enum OAKPalette {
    static let accent = Color(red: 0.06, green: 0.43, blue: 0.46)
    static let due = Color(red: 0.08, green: 0.40, blue: 0.75)
    static let missed = Color(red: 0.78, green: 0.21, blue: 0.22)
    static let taken = Color(red: 0.14, green: 0.48, blue: 0.29)
    static let skipped = Color(red: 0.71, green: 0.33, blue: 0.04)
    static let dueDark = Color(red: 0.39, green: 0.71, blue: 0.96)
    static let missedDark = Color(red: 0.94, green: 0.60, blue: 0.60)
    static let takenDark = Color(red: 0.51, green: 0.78, blue: 0.52)
    static let skippedDark = Color(red: 1.00, green: 0.72, blue: 0.30)
    static let heroStart = Color(red: 0.04, green: 0.47, blue: 0.54)
    static let heroEnd = Color(red: 0.05, green: 0.30, blue: 0.48)

    static func due(for colorScheme: ColorScheme) -> Color { colorScheme == .dark ? dueDark : due }
    static func missed(for colorScheme: ColorScheme) -> Color { colorScheme == .dark ? missedDark : missed }
    static func taken(for colorScheme: ColorScheme) -> Color { colorScheme == .dark ? takenDark : taken }
    static func skipped(for colorScheme: ColorScheme) -> Color { colorScheme == .dark ? skippedDark : skipped }
}

enum OAKCardVariant: Sendable {
    case glass
}

extension View {
    func oakCardStyle(
        _ variant: OAKCardVariant = .glass,
        cornerRadius: CGFloat = 16,
        strokeOpacity: Double = 0.18,
        shadowOpacity: Double = 0.10,
        shadowRadius: CGFloat = 12,
        shadowY: CGFloat = 6
    ) -> some View {
        let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
        switch variant {
        case .glass:
            return background {
                shape
                    .fill(.ultraThinMaterial)
                    .overlay(
                        LinearGradient(
                            colors: [.white.opacity(0.12), OAKPalette.accent.opacity(0.035)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                        .clipShape(shape)
                    )
            }
                .overlay(shape.stroke(Color.secondary.opacity(strokeOpacity), lineWidth: 0.75))
                .shadow(color: .black.opacity(shadowOpacity), radius: shadowRadius, x: 0, y: shadowY)
        }
    }
}

struct OAKBackgroundModifier: ViewModifier {
    @Environment(\.colorScheme) private var colorScheme

    func body(content: Content) -> some View {
        content
            .background(
                ZStack {
                    LinearGradient(
                        colors: backgroundColors,
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                    Circle()
                        .fill(OAKPalette.due.opacity(colorScheme == .dark ? 0.10 : 0.08))
                        .frame(width: 320, height: 320)
                        .blur(radius: 72)
                        .offset(x: 150, y: -260)
                    Circle()
                        .fill(OAKPalette.taken.opacity(colorScheme == .dark ? 0.08 : 0.07))
                        .frame(width: 280, height: 280)
                        .blur(radius: 78)
                        .offset(x: -170, y: 300)
                }
                .ignoresSafeArea()
            )
    }

    private var backgroundColors: [Color] {
        colorScheme == .dark
            ? [Color(red: 0.03, green: 0.09, blue: 0.10), Color(red: 0.02, green: 0.05, blue: 0.06)]
            : [Color(red: 0.95, green: 0.98, blue: 0.97), Color(red: 0.92, green: 0.96, blue: 0.97)]
    }
}

extension View {
    func oakBackground() -> some View {
        modifier(OAKBackgroundModifier())
    }
}
