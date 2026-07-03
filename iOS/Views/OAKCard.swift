import SwiftUI

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
            return background(.ultraThinMaterial, in: shape)
                .overlay(shape.stroke(Color.secondary.opacity(strokeOpacity), lineWidth: 1))
                .shadow(color: .black.opacity(shadowOpacity), radius: shadowRadius, x: 0, y: shadowY)
        }
    }
}

struct OAKBackgroundModifier: ViewModifier {
    @Environment(\.colorScheme) private var colorScheme

    func body(content: Content) -> some View {
        let colors: [Color] = colorScheme == .dark
            ? [Color(.systemBackground).opacity(0.95), .black]
            : [Color(.systemGroupedBackground), Color(.systemBackground)]
        content
            .background(
                LinearGradient(colors: colors, startPoint: .topLeading, endPoint: .bottomTrailing)
                    .ignoresSafeArea()
            )
    }
}

extension View {
    func oakBackground() -> some View {
        modifier(OAKBackgroundModifier())
    }
}
