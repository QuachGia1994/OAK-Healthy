package com.example.supplementtracker.service

import android.content.Context
import android.content.SharedPreferences

enum class ActivationMilestone(val wireName: String) {
    CLIENT_READY("client_ready"),
    ROUTINE_READY("routine_ready"),
    FIRST_ACTION("first_action"),
    REMINDER_READY("reminder_ready")
}

data class ActivationProgress(val completed: Set<ActivationMilestone>) {
    val coreCompletedCount: Int
        get() = coreMilestones.count { it in completed }

    val firstValueReached: Boolean
        get() = coreMilestones.all { it in completed }

    val nextCoreMilestone: ActivationMilestone?
        get() = coreMilestones.firstOrNull { it !in completed }

    companion object {
        val coreMilestones = listOf(
            ActivationMilestone.CLIENT_READY,
            ActivationMilestone.ROUTINE_READY,
            ActivationMilestone.FIRST_ACTION
        )
    }
}

class ActivationRetentionStore(
    private val context: Context,
    private val prefs: SharedPreferences = OakPrefs.get(context)
) {
    fun progress(): ActivationProgress {
        val completed = ActivationMilestone.entries.filterTo(mutableSetOf()) { milestone ->
            prefs.getBoolean(key(milestone), false)
        }
        return ActivationProgress(completed)
    }

    fun mark(milestone: ActivationMilestone): Boolean {
        if (prefs.getBoolean(key(milestone), false)) return false
        prefs.edit().putBoolean(key(milestone), true).apply()
        DiagnosticsReporter.event(
            context,
            "activation_milestone",
            mapOf("milestone" to milestone.wireName, "state" to "reached")
        )
        return true
    }

    fun reconcile(
        clientReady: Boolean,
        routineReady: Boolean,
        firstAction: Boolean,
        reminderReady: Boolean
    ): ActivationProgress {
        if (clientReady) mark(ActivationMilestone.CLIENT_READY)
        if (routineReady) mark(ActivationMilestone.ROUTINE_READY)
        if (firstAction) mark(ActivationMilestone.FIRST_ACTION)
        if (reminderReady) mark(ActivationMilestone.REMINDER_READY)
        return progress()
    }

    private fun key(milestone: ActivationMilestone): String {
        return "oakActivation_${milestone.wireName}"
    }
}
