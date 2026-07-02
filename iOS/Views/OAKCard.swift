import SwiftUI

enum OAKCardVariant: Sendable {
    case glass
}

extension View {
    func oakCardStyle(
        _ variant: OAKCardVariant = .glass,
        cornerRadius: CGFloat = 20,
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
