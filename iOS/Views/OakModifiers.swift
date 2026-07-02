import SwiftUI

extension View {
    func oakBackground() -> some View {
        modifier(OakBackgroundModifier())
    }
}

enum OakGlassRow {
    @ViewBuilder
    static var background: some View {
        Color.clear.background(.ultraThinMaterial)
    }
}

private struct OakBackgroundModifier: ViewModifier {
    @Environment(\.colorScheme) private var colorScheme

    func body(content: Content) -> some View {
        let colors: [Color] = colorScheme == .dark
            ? [Color(red: 0.08, green: 0.0, blue: 0.15), .black]
            : [Color(.systemGroupedBackground), Color(.systemBackground)]
        content
            .background(
                LinearGradient(colors: colors, startPoint: .topLeading, endPoint: .bottomTrailing)
                    .ignoresSafeArea()
            )
    }
}
