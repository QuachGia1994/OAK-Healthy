package com.example.supplementtracker.service

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FirebaseRealtimeSyncListener(
    private val context: Context,
    private val onSyncNeeded: suspend () -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val DB_URL = "https://oak-healthy-default-rtdb.asia-southeast1.firebasedatabase.app"
    private val ROOT = "oakBins"
    private var listeners = mutableListOf<Pair<com.google.firebase.database.DatabaseReference, ValueEventListener>>()

    fun start(manifestId: String) {
        stop()
        if (manifestId.isEmpty()) return
        val prefs = context.getSharedPreferences("oak_settings", Context.MODE_PRIVATE)
        val stackBinId = prefs.getString("cloudSyncStackBinId_$manifestId", "").orEmpty().trim()
        val historyBinId = prefs.getString("cloudSyncHistoryBinId_$manifestId", "").orEmpty().trim()
        val root = FirebaseDatabase.getInstance(DB_URL).reference.child(ROOT)

        if (stackBinId.isNotEmpty()) {
            addRevListener(root.child(stackBinId).child("meta").child("rev"), stackBinId, prefs)
        }
        if (historyBinId.isNotEmpty()) {
            addRevListener(root.child(historyBinId).child("meta").child("rev"), historyBinId, prefs)
        }
        if (stackBinId.isEmpty() || historyBinId.isEmpty()) {
            addRevListener(root.child(manifestId).child("meta").child("rev"), manifestId, prefs)
        }
    }

    fun stop() {
        for ((ref, listener) in listeners) {
            ref.removeEventListener(listener)
        }
        listeners.clear()
    }

    private fun addRevListener(ref: com.google.firebase.database.DatabaseReference, binId: String, prefs: SharedPreferences) {
        val key = "cloudSyncLastSeenRev_$binId"
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val newRev = snapshot.getValue(Long::class.java)?.toString()
                    ?: snapshot.getValue(String::class.java)?.trim()
                    ?: return
                val oldRev = prefs.getString(key, "")
                if (oldRev == newRev) return
                prefs.edit().putString(key, newRev).apply()
                scope.launch { onSyncNeeded() }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        listeners.add(ref to listener)
    }
}
