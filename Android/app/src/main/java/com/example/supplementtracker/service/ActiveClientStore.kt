package com.example.supplementtracker.service

import android.content.Context
import java.util.UUID

class ActiveClientStore(context: Context) {
    private val prefs = OakPrefs.get(context)

    fun currentClientId(): UUID? {
        val raw = prefs.getString(KEY_ACTIVE_CLIENT_ID, null) ?: return null
        return runCatching { UUID.fromString(raw) }.getOrNull()
    }

    fun setCurrentClientId(id: UUID?) {
        prefs.edit().putString(KEY_ACTIVE_CLIENT_ID, id?.toString()).apply()
    }

    private companion object {
        const val KEY_ACTIVE_CLIENT_ID = "activeClientId"
    }
}
