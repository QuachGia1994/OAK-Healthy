package com.example.supplementtracker.service

import android.content.SharedPreferences

enum class SyncMutationPart {
    STACK,
    HISTORY
}

data class SyncMutationEntry(
    val part: SyncMutationPart,
    val enqueuedAtEpochMs: Long
)

class SyncMutationQueue(private val prefs: SharedPreferences) {
    fun markDirty(clientId: String, part: SyncMutationPart, nowEpochMs: Long = System.currentTimeMillis()) {
        val entries = read(clientId).associateBy { it.part }.toMutableMap()
        val current = entries[part]?.enqueuedAtEpochMs ?: 0L
        entries[part] = SyncMutationEntry(part, maxOf(current, nowEpochMs))
        write(clientId, entries.values)
    }

    fun pending(clientId: String): List<SyncMutationEntry> = read(clientId)
        .sortedBy { it.part.name }

    fun clearSynced(
        clientId: String,
        parts: Set<SyncMutationPart>,
        syncStartedEpochMs: Long
    ) {
        val remaining = read(clientId).filterNot { entry ->
            entry.part in parts && entry.enqueuedAtEpochMs <= syncStartedEpochMs
        }
        write(clientId, remaining)
    }

    private fun read(clientId: String): List<SyncMutationEntry> {
        val raw = prefs.getString(key(clientId), "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split(';').mapNotNull(::decode)
    }

    private fun write(clientId: String, entries: Collection<SyncMutationEntry>) {
        val raw = entries.sortedBy { it.part.name }
            .joinToString(";") { "${it.part.name}:${it.enqueuedAtEpochMs}" }
        prefs.edit().putString(key(clientId), raw).apply()
    }

    private fun decode(raw: String): SyncMutationEntry? {
        val parts = raw.split(':', limit = 2)
        val part = runCatching { SyncMutationPart.valueOf(parts.firstOrNull().orEmpty()) }.getOrNull() ?: return null
        val timestamp = parts.getOrNull(1)?.toLongOrNull() ?: return null
        return SyncMutationEntry(part, timestamp)
    }

    private fun key(clientId: String): String = "cloudSyncMutationQueue_${clientId.trim().lowercase()}"
}
