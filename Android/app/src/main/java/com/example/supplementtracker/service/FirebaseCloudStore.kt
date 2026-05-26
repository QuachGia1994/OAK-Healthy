package com.example.supplementtracker.service

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object FirebaseCloudStore {
    private const val DB_URL = "https://oak-healthy-default-rtdb.asia-southeast1.firebasedatabase.app"
    private const val ROOT = "oakBins"

    private fun root() = FirebaseDatabase.getInstance(DB_URL).reference.child(ROOT)

    private suspend fun ensureSignedIn() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) return
        auth.signInAnonymously().awaitUnit()
    }

    suspend fun createBin(payload: String): String {
        ensureSignedIn()
        val id = root().push().key ?: error("Missing key")
        val rev = System.currentTimeMillis()
        root().child(id).updateChildren(mapOf("payload" to payload, "meta/rev" to rev)).awaitUnit()
        return id
    }

    suspend fun readIfChanged(id: String, knownRev: String?): CloudDownload {
        ensureSignedIn()
        val current = readRev(id)
        val currentTag = current?.toString()
        if (currentTag != null && currentTag == knownRev?.trim()) return CloudDownload(null, currentTag)
        val payload = readPayload(id).orEmpty()
        return CloudDownload(payload, currentTag)
    }

    suspend fun readAlways(id: String): CloudDownload {
        ensureSignedIn()
        val payload = readPayload(id).orEmpty()
        return CloudDownload(payload, readRev(id)?.toString())
    }

    suspend fun write(id: String, payload: String, expectedRev: String?): String {
        ensureSignedIn()
        val expected = expectedRev?.trim().orEmpty()
        val current = readRev(id)?.toString().orEmpty()
        if (expected.isNotEmpty() && current.isNotEmpty() && current != expected) throw ConflictError()
        val rev = System.currentTimeMillis()
        root().child(id).updateChildren(mapOf("payload" to payload, "meta/rev" to rev)).awaitUnit()
        return rev.toString()
    }

    suspend fun delete(id: String) {
        ensureSignedIn()
        root().child(id).removeValue().awaitUnit()
    }

    private suspend fun readRev(id: String): Long? {
        val snap = root().child(id).child("meta").child("rev").get().await()
        val asLong = snap.getValue(Long::class.java)
        if (asLong != null) return asLong
        val asString = snap.getValue(String::class.java)?.trim()
        return asString?.toLongOrNull()
    }

    private suspend fun readPayload(id: String): String? {
        val snap = root().child(id).child("payload").get().await()
        return snap.getValue(String::class.java)
    }
}

internal class ConflictError : Exception("Conflict")

private suspend fun <T> Task<T>.await(): T {
    return suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
}

private suspend fun Task<*>.awaitUnit() {
    await()
}
