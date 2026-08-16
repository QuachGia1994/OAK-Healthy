package com.example.supplementtracker.service

import java.util.UUID

object ActiveProfileNotificationPolicy {
    fun allows(activeClientId: UUID?, supplementClientId: UUID?): Boolean {
        return activeClientId != null && activeClientId == supplementClientId
    }

    fun allows(activeClientId: UUID?, supplementClientId: String?): Boolean {
        val parsedClientId = supplementClientId
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        return allows(activeClientId, parsedClientId)
    }
}
