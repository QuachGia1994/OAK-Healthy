import SwiftUI

/// OAK Healthy Typography System — iOS equivalent of Android OakTypographySystem.
/// All size values come from OAKTypeScale to ensure single source of truth across platforms.
enum OAKFont {
    // MARK: - Display / Hero (Serif)
    /// Hero number — 42pt, used for streak count, big metrics
    static let heroNumber = Font.system(size: OAKTypeScale.heroNumber, weight: .heavy, design: .serif)
    /// Screen title — 30pt, top-level page headings
    static let screenTitle = Font.system(size: OAKTypeScale.screenTitle, weight: .bold, design: .serif)
    /// Metric — 24pt, dashboard numbers
    static let metric = Font.system(size: OAKTypeScale.metric, weight: .bold, design: .serif)

    // MARK: - Headlines (Sans-serif)
    /// Section title — 20pt, card/section headings
    static let sectionTitle = Font.system(size: OAKTypeScale.sectionTitle, weight: .semibold, design: .default)
    /// Headline medium — 18pt
    static let headlineMedium = Font.system(size: 18, weight: .semibold, design: .default)
    /// Headline small — 16pt
    static let headlineSmall = Font.system(size: 16, weight: .semibold, design: .default)

    // MARK: - Body
    /// Body large — 16pt, primary reading text
    static let bodyLarge = Font.system(size: OAKTypeScale.body, weight: .regular, design: .default)
    /// Body medium — 14pt
    static let bodyMedium = Font.system(size: 14, weight: .regular, design: .default)
    /// Body small / Caption — 13pt, secondary info
    static let caption = Font.system(size: OAKTypeScale.caption, weight: .regular, design: .default)

    // MARK: - Labels / UI Controls
    /// Label large — 14pt semibold, buttons, chips
    static let labelLarge = Font.system(size: 14, weight: .semibold, design: .default)
    /// Label medium — 12pt medium, badges, tags
    static let labelMedium = Font.system(size: 12, weight: .medium, design: .default)
    /// Label small — 11pt medium, timestamps, fine print
    static let labelSmall = Font.system(size: 11, weight: .medium, design: .default)
}

// MARK: - View Modifier convenience
extension View {
    func oakFont(_ font: Font) -> some View {
        self.font(font)
    }
}
