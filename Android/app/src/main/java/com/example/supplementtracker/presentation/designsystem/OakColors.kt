package com.example.supplementtracker.presentation.designsystem

import androidx.compose.ui.graphics.Color

// ponytail: centralized color tokens — no hardcoded Color(0xFF...) outside this file.
object OakColors {
    // Dose status
    val Taken = Color(0xFF2E7D32)
    val Skipped = Color(0xFFFF9800)
    val Missed = Color(0xFFD32F2F)
    val DueSoon = Color(0xFF42A5F5)
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
    val InsightCardStart = Color(0xFF1A8CFF)
    val InsightCardEnd = Color(0xFF0D63F2)
    val SkippedRecord = Color(0xFFFF5252)
    val SkippedBg = Color(0xFFB71C1C)

    // Share image
    val ShareDarkStart = Color(0xFF1A1A1A)
    val ShareDarkEnd = Color(0xFF0D0D0D)
}
