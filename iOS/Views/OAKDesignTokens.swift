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

    static func background(for colorScheme: ColorScheme) -> Color {
        colorScheme == .dark ? paperDark : paper
    }

    static func surface(for colorScheme: ColorScheme) -> Color {
        colorScheme == .dark ? paperRaisedDark : paperRaised
    }

    static func mutedSurface(for colorScheme: ColorScheme) -> Color {
        colorScheme == .dark ? paperMutedDark : paperMuted
    }

    static func primaryText(for colorScheme: ColorScheme) -> Color {
        colorScheme == .dark ? inkDark : ink
    }

    static func secondaryText(for colorScheme: ColorScheme) -> Color {
        colorScheme == .dark ? inkMutedDark : inkMuted
    }

    static func divider(for colorScheme: ColorScheme) -> Color {
        colorScheme == .dark ? hairlineDark : hairline
    }

    static func due(for colorScheme: ColorScheme) -> Color {
        colorScheme == .dark ? dueDark : due
    }

    static func missed(for colorScheme: ColorScheme) -> Color {
        colorScheme == .dark ? missedDark : missed
    }

    static func taken(for colorScheme: ColorScheme) -> Color {
        colorScheme == .dark ? takenDark : taken
    }

    static func skipped(for colorScheme: ColorScheme) -> Color {
        colorScheme == .dark ? skippedDark : skipped
    }
}

enum OAKSpacing {
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 12
    static let lg: CGFloat = 16
    static let xl: CGFloat = 24
    static let xxl: CGFloat = 32
    static let section: CGFloat = 28
}

enum OAKRadius {
    static let sm: CGFloat = 10
    static let md: CGFloat = 14
    static let lg: CGFloat = 18
    static let xl: CGFloat = 22
    static let pill: CGFloat = 999
}

enum OAKTypeScale {
    static let screenTitle: CGFloat = 30
    static let heroNumber: CGFloat = 42
    static let sectionTitle: CGFloat = 20
    static let metric: CGFloat = 24
    static let body: CGFloat = 16
    static let caption: CGFloat = 13
}
