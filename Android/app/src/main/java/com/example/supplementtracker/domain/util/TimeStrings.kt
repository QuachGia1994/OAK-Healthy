package com.example.supplementtracker.domain.util

import java.time.LocalTime
import java.time.format.DateTimeFormatter

object TimeStrings {
    private val outputFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun parseLenient(token: String): LocalTime? {
        val trimmed = token.trim()
        if (trimmed.isBlank()) return null
        val parts = trimmed.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null
        if (hour !in 0..23) return null
        if (minute !in 0..59) return null
        return runCatching { LocalTime.of(hour, minute) }.getOrNull()
    }

    fun normalizeList(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return emptyList()
        val tokens = trimmed.split(',', ';', '|').map { it.trim() }.filter { it.isNotBlank() }
        val times = tokens.mapNotNull { parseLenient(it) }.distinct().sorted()
        return times.map { outputFormatter.format(it) }
    }

    fun normalizeString(raw: String): String {
        val normalized = normalizeList(raw)
        return normalized.joinToString(", ")
    }
}

