package com.example.supplementtracker.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FirebaseRealtimeSyncListener(
    private val context: Context,
    private val onSyncNeeded: suspend () -> Boolean
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val databaseUrl = "https://oak-healthy-default-rtdb.asia-southeast1.firebasedatabase.app"
    private val rootKey = "oakBins"
    private var listeners = mutableListOf<Pair<com.google.firebase.database.DatabaseReference, ValueEventListener>>()
    private var activeManifestId: String? = null
    private var boundBinIds = mutableSetOf<String>()
    private var pendingRevisions = mutableMapOf<String, String>()
    private var syncJob: Job? = null
    private var generation = 0

    @Synchronized
    fun start(manifestId: String) {
        stop()
        if (!FirebaseRevision.isValidBinId(manifestId)) return
        activeManifestId = manifestId
        bindListeners(manifestId)
    }

    @Synchronized
    fun stop() {
        generation += 1
        syncJob?.cancel()
        syncJob = null
        pendingRevisions.clear()
        activeManifestId = null
        boundBinIds.clear()
        removeListeners()
    }

    override fun close() {
        stop()
        scope.coroutineContext[Job]?.cancel()
    }

    private fun bindListeners(manifestId: String) {
        val prefs = OakPrefs.get(context)
        val stackBinId = prefs.getString("cloudSyncStackBinId_$manifestId", "").orEmpty().trim()
        val historyBinId = prefs.getString("cloudSyncHistoryBinId_$manifestId", "").orEmpty().trim()
        val root = FirebaseDatabase.getInstance(databaseUrl).reference.child(rootKey)
        val validStack = FirebaseRevision.isValidBinId(stackBinId)
        val validHistory = FirebaseRevision.isValidBinId(historyBinId)
        if (validStack) addRevListener(root.child(stackBinId).child("meta").child("rev"), stackBinId, prefs)
        if (validHistory) addRevListener(root.child(historyBinId).child("meta").child("rev"), historyBinId, prefs)
        if (!validStack || !validHistory) addRevListener(root.child(manifestId).child("meta").child("rev"), manifestId, prefs)
    }

    private fun addRevListener(
        ref: com.google.firebase.database.DatabaseReference,
        binId: String,
        prefs: SharedPreferences
    ) {
        val key = "cloudSyncLastSeenRevV2_$binId"
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val newRevision = FirebaseRevision.fromValue(snapshot.value)?.toString() ?: return
                queueRevision(binId, newRevision, prefs, key)
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Log.w("OakRealtimeSync", "Revision listener cancelled", error.toException())
                scope.launch {
                    delay(1_000L)
                    restartListenersIfActive()
                }
            }
        }
        ref.addValueEventListener(listener)
        listeners.add(ref to listener)
        boundBinIds.add(binId)
    }

    @Synchronized
    private fun queueRevision(binId: String, revision: String, prefs: SharedPreferences, key: String) {
        if (revision.isEmpty()) {
            pendingRevisions[binId] = prefs.getString(key, "").orEmpty()
        } else {
            val oldRevision = prefs.getString(key, "")
            if (oldRevision == revision && pendingRevisions[binId] == revision) return
            pendingRevisions[binId] = revision
        }
        if (syncJob?.isActive == true) return
        val startGeneration = generation
        syncJob = scope.launch { processPendingRevisions(startGeneration, prefs) }
    }

    private suspend fun processPendingRevisions(startGeneration: Int, prefs: SharedPreferences) {
        var failureAttempt = 0
        while (scope.isActive && currentGeneration() == startGeneration) {
            val targets = pendingSnapshot()
            if (targets.isEmpty()) break
            val success = runCatching { onSyncNeeded() }.getOrDefault(false)
            if (!success) {
                failureAttempt = (failureAttempt + 1).coerceAtMost(4)
                delay(retryDelayMillis(failureAttempt))
                continue
            }
            failureAttempt = 0
            markProcessed(targets, prefs)
            refreshBindingsIfNeeded()
        }
        clearSyncJob()
    }

    @Synchronized
    private fun markProcessed(targets: Map<String, String>, prefs: SharedPreferences) {
        val editor = prefs.edit()
        targets.forEach { (binId, revision) ->
            if (pendingRevisions[binId] == revision && revision.isNotEmpty()) {
                editor.putString("cloudSyncLastSeenRevV2_$binId", revision)
                pendingRevisions.remove(binId)
            }
        }
        editor.apply()
    }

    @Synchronized
    private fun refreshBindingsIfNeeded() {
        val manifestId = activeManifestId ?: return
        val prefs = OakPrefs.get(context)
        val stackBinId = prefs.getString("cloudSyncStackBinId_$manifestId", "").orEmpty().trim()
        val historyBinId = prefs.getString("cloudSyncHistoryBinId_$manifestId", "").orEmpty().trim()
        val desired = buildSet {
            if (FirebaseRevision.isValidBinId(stackBinId)) add(stackBinId)
            if (FirebaseRevision.isValidBinId(historyBinId)) add(historyBinId)
            if (!FirebaseRevision.isValidBinId(stackBinId) || !FirebaseRevision.isValidBinId(historyBinId)) add(manifestId)
        }
        if (desired == boundBinIds) return
        removeListeners()
        boundBinIds.clear()
        bindListeners(manifestId)
    }

    @Synchronized
    private fun restartListenersIfActive() {
        val manifestId = activeManifestId ?: return
        removeListeners()
        boundBinIds.clear()
        bindListeners(manifestId)
    }

    @Synchronized
    private fun currentGeneration(): Int = generation

    @Synchronized
    private fun pendingSnapshot(): Map<String, String> = pendingRevisions.toMap()

    @Synchronized
    private fun clearSyncJob() {
        syncJob = null
    }

    private fun retryDelayMillis(attempt: Int): Long {
        return when (attempt) {
            1 -> 1_000L
            2 -> 3_000L
            3 -> 10_000L
            else -> 30_000L
        }
    }

    private fun removeListeners() {
        for ((ref, listener) in listeners) ref.removeEventListener(listener)
        listeners.clear()
    }
}
