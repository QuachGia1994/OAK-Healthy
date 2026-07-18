package com.example.supplementtracker.presentation.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ponytail: centralized color tokens — no hardcoded Color(0xFF...) outside this file.
object OakColors {
    // Brand
    val Accent = Color(0xFF0F6F75)
    val AccentDark = Color(0xFF78D2CF)

    // Dose status
    val Taken = Color(0xFF237A4B)
    val Skipped = Color(0xFFB45309)
    val Missed = Color(0xFFC73538)
    val DueSoon = Color(0xFF1565C0)
    val TakenDark = Color(0xFF81C784)
    val SkippedDark = Color(0xFFFFB74D)
    val MissedDark = Color(0xFFEF9A9A)
    val DueSoonDark = Color(0xFF64B5F6)
    val Done = Color(0xFF22C55E)

    // Semantic
    val Error = Color(0xFFD32F2F)
    val ErrorDark = Color(0xFFC62828)
    val Success = Color(0xFF2E7D32)
    val Warning = Color(0xFFEF6C00)
    val Neutral = Color(0xFF607D8B)
    val NeutralDark = Color(0xFF546E7A)

    // Text
    val TextPrimary = Color(0xFF111111)
    val TextSecondary = Color(0xFF374151)
    val TextTertiary = Color(0xFF6B7280)

    // Chart
    val ChartBar = Color(0xFF2196F3)
    val ChartBarDark = Color(0xFF64B5F6)

    // Badge
    val BadgeStart = Color(0xFFFF5A5F)
    val BadgeEnd = Color(0xFFFF9F43)

    // Accent
    val StreakBorder = Color(0xFFFFB300)
    val InsightCardStart = Color(0xFF087887)
    val InsightCardEnd = Color(0xFF0C4D78)
    val SkippedRecord = Color(0xFFFF5252)
    val SkippedBg = Color(0xFFB71C1C)

    // Share image
    val ShareDarkStart = Color(0xFF1A1A1A)
    val ShareDarkEnd = Color(0xFF0D0D0D)
}

val OakLightColorScheme = lightColorScheme(
    primary = OakColors.Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC5EAE7),
    onPrimaryContainer = Color(0xFF073F43),
    secondary = Color(0xFF4D635C),
    background = Color(0xFFF3F8F5),
    surface = Color(0xFFFAFCF9),
    surfaceVariant = Color(0xFFE5EEE9),
    onSurface = Color(0xFF14201D),
    onSurfaceVariant = Color(0xFF52615C),
    outlineVariant = Color(0xFFC2CEC8)
)

val OakDarkColorScheme = darkColorScheme(
    primary = OakColors.AccentDark,
    onPrimary = Color(0xFF003739),
    primaryContainer = Color(0xFF15585B),
    onPrimaryContainer = Color(0xFFB9EFEC),
    secondary = Color(0xFFB4CCC2),
    background = Color(0xFF071413),
    surface = Color(0xFF0C1C1A),
    surfaceVariant = Color(0xFF1B2C29),
    onSurface = Color(0xFFE4F0EC),
    onSurfaceVariant = Color(0xFFB8C8C2),
    outlineVariant = Color(0xFF3D514C)
)
