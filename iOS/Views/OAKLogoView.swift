import SwiftUI

/// The shared OAK Healthy brand lockup used throughout the app.
public struct OAKLogoView: View {
    private let size: CGFloat
    private let showsWordmark: Bool

    /// Creates the brand mark with an optional wordmark.
    public init(size: CGFloat = 100, showsWordmark: Bool = true) {
        self.size = size
        self.showsWordmark = showsWordmark
    }

    public var body: some View {
        VStack(spacing: 10) {
            Image("OakBrandIcon")
                .resizable()
                .scaledToFit()
                .frame(width: size, height: size)
                .clipShape(RoundedRectangle(cornerRadius: size * 0.22, style: .continuous))
                .shadow(color: Color(red: 0.02, green: 0.18, blue: 0.18).opacity(0.28), radius: size * 0.12, y: size * 0.07)

            if showsWordmark {
                Text("app_name".localized.uppercased())
                    .font(.system(size: size * 0.22, weight: .bold, design: .rounded))
                    .tracking(size * 0.018)
                    .foregroundStyle(OAKPalette.accent)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("app_name".localized)
    }
}

#Preview {
    OAKLogoView()
        .padding(40)
}
