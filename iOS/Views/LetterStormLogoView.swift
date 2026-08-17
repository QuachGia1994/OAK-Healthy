import SwiftUI

/// A lightweight launch animation inspired by oak growth rings.
public struct OAKLoadingLogoView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var isRevealed = false
    @State private var isPulsing = false

    /// Creates the launch brand animation.
    public init() {}

    public var body: some View {
        VStack(spacing: 26) {
            ZStack {
                ForEach(0..<3, id: \.self) { index in
                    RoundedRectangle(cornerRadius: 36, style: .continuous)
                        .stroke(OAKPalette.accent.opacity(0.34), lineWidth: 1.2)
                        .frame(width: 126, height: 126)
                        .scaleEffect(isPulsing ? 1.46 : 0.82)
                        .opacity(isPulsing ? 0 : 0.58)
                        .animation(ringAnimation(delay: Double(index) * 0.34), value: isPulsing)
                }

                OAKLogoView(size: 108, showsWordmark: false)
                    .scaleEffect(isRevealed ? 1 : 0.78)
                    .opacity(isRevealed ? 1 : 0)
            }
            .frame(width: 184, height: 184)

            VStack(spacing: 9) {
                Text("app_name".localized.uppercased())
                    .font(.system(size: 22, weight: .bold, design: .rounded))
                    .tracking(2.8)
                    .foregroundStyle(.primary)

                loadingTrack
            }
            .opacity(isRevealed ? 1 : 0)
            .offset(y: isRevealed ? 0 : 8)
        }
        .onAppear(perform: startAnimation)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("app_name".localized)
    }

    private var loadingTrack: some View {
        ZStack(alignment: .leading) {
            Capsule().fill(OAKPalette.accent.opacity(0.14))
            Capsule()
                .fill(OAKPalette.accent)
                .frame(width: 30)
                .offset(x: isPulsing ? 66 : 0)
        }
        .frame(width: 96, height: 4)
        .clipShape(Capsule())
    }

    private func ringAnimation(delay: Double) -> Animation? {
        guard !reduceMotion else { return nil }
        return .easeOut(duration: 1.7).delay(delay)
    }

    private func startAnimation() {
        guard !reduceMotion else {
            isRevealed = true
            return
        }
        withAnimation(.spring(response: 0.62, dampingFraction: 0.78)) {
            isRevealed = true
        }
        withAnimation(.easeOut(duration: 1.25)) {
            isPulsing = true
        }
    }
}

#Preview {
    OAKLoadingLogoView()
        .padding(40)
}
