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

    private const val MIN_SIGN_IN_INTERVAL_MS = 30_000L
    @Volatile private var lastSignInAttemptMs = 0L

    private suspend fun ensureSignedIn() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) return
        // ponytail: rate-limit anonymous sign-in to prevent quota exhaustion
        val now = System.currentTimeMillis()
        if (now - lastSignInAttemptMs < MIN_SIGN_IN_INTERVAL_MS) return
        lastSignInAttemptMs = now
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

    // ponytail: retry reads up to 3 times to match iOS behavior.
    private suspend fun <T> retryRead(maxAttempts: Int = 3, block: suspend () -> T): T {
        var lastException: Exception? = null
        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxAttempts - 1) {
                    kotlinx.coroutines.delay(1000L * (attempt + 1))
                }
            }
        }
        throw lastException ?: IllegalStateException("Retry failed")
    }

    private suspend fun readRev(id: String): Long? = retryRead {
        val snap = root().child(id).child("meta").child("rev").get().await()
        val asLong = snap.getValue(Long::class.java)
        if (asLong != null) return@retryRead asLong
        val asString = snap.getValue(String::class.java)?.trim()
        asString?.toLongOrNull()
    }

    private suspend fun readPayload(id: String): String? = retryRead {
        val snap = root().child(id).child("payload").get().await()
        snap.getValue(String::class.java)
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
