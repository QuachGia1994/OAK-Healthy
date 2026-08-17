package com.example.supplementtracker.presentation.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

object OakColors {
    val Accent = Color(0xFF2D5B45)
    val AccentDark = Color(0xFF9BC6A8)

    val Paper = Color(0xFFF5F0E6)
    val PaperRaised = Color(0xFFFBF8F1)
    val PaperMuted = Color(0xFFEAE3D6)
    val Ink = Color(0xFF1D211C)
    val InkMuted = Color(0xFF5E645C)
    val Hairline = Color(0xFFD5CDBF)

    val PaperDark = Color(0xFF111713)
    val PaperRaisedDark = Color(0xFF1A211B)
    val PaperMutedDark = Color(0xFF242D25)
    val InkDark = Color(0xFFF8F3E9)
    val InkMutedDark = Color(0xFFD0D5CB)
    val HairlineDark = Color(0xFF56645A)

    val Taken = Accent
    val Skipped = Color(0xFF9A661F)
    val Missed = Color(0xFFB5473F)
    val DueSoon = Color(0xFF466A8D)
    val TakenDark = AccentDark
    val SkippedDark = Color(0xFFD2A35F)
    val MissedDark = Color(0xFFE28C82)
    val DueSoonDark = Color(0xFF91AEC8)
    val Done = Accent

    val Error = Missed
    val ErrorDark = MissedDark
    val Success = Taken
    val Warning = Skipped
    val Neutral = Color(0xFF74786F)
    val NeutralDark = Color(0xFF9CA299)

    val TextPrimary = Ink
    val TextSecondary = InkMuted
    val TextTertiary = Color(0xFF7B8077)

    val ChartBar = Accent
    val ChartBarDark = AccentDark

    val BadgeStart = Accent
    val BadgeEnd = Accent
    val StreakBorder = Color(0xFFB1802C)
    val InsightCardStart = Color(0xFF294D3B)
    val InsightCardEnd = Color(0xFF294D3B)
    val SkippedRecord = Missed
    val SkippedBg = Color(0xFF6C302C)

    val ShareDarkStart = Color(0xFF171A17)
    val ShareDarkEnd = Color(0xFF101310)
}

val OakLightColorScheme = lightColorScheme(
    primary = OakColors.Accent,
    onPrimary = OakColors.PaperRaised,
    primaryContainer = Color(0xFFDCE7DD),
    onPrimaryContainer = Color(0xFF173326),
    secondary = Color(0xFF667267),
    onSecondary = OakColors.PaperRaised,
    background = OakColors.Paper,
    surface = OakColors.PaperRaised,
    surfaceVariant = OakColors.PaperMuted,
    onSurface = OakColors.Ink,
    onSurfaceVariant = OakColors.InkMuted,
    outline = Color(0xFF9E988D),
    outlineVariant = OakColors.Hairline,
    error = OakColors.Error
)

val OakDarkColorScheme = darkColorScheme(
    primary = OakColors.AccentDark,
    onPrimary = Color(0xFF173326),
    primaryContainer = Color(0xFF294638),
    onPrimaryContainer = Color(0xFFDCEBDD),
    secondary = Color(0xFFB6C0B6),
    onSecondary = Color(0xFF202820),
    background = OakColors.PaperDark,
    surface = OakColors.PaperRaisedDark,
    surfaceVariant = OakColors.PaperMutedDark,
    onSurface = OakColors.InkDark,
    onSurfaceVariant = OakColors.InkMutedDark,
    outline = Color(0xFF9AA59C),
    outlineVariant = OakColors.HairlineDark,
    error = OakColors.ErrorDark
)
