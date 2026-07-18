package com.example.supplementtracker.service

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object FirebaseCloudStore {
    private const val DB_URL = "https://oak-healthy-default-rtdb.asia-southeast1.firebasedatabase.app"
    private const val ROOT = "oakBins"

    private fun root() = FirebaseDatabase.getInstance(DB_URL).reference.child(ROOT)

    private val signInMutex = Mutex()

    private suspend fun ensureSignedIn() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) return
        signInMutex.withLock {
            if (auth.currentUser == null) auth.signInAnonymously().awaitUnit()
        }
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
        requireValidBinId(id)
        val node = readNode(id)
        if (node.rev != null && node.rev == knownRev?.trim()) return CloudDownload(null, node.rev)
        return CloudDownload(node.payload.orEmpty(), node.rev)
    }

    suspend fun readAlways(id: String): CloudDownload {
        ensureSignedIn()
        requireValidBinId(id)
        val node = readNode(id)
        return CloudDownload(node.payload.orEmpty(), node.rev)
    }

    suspend fun write(id: String, payload: String, expectedRev: String?): String {
        ensureSignedIn()
        requireValidBinId(id)
        val expected = expectedRev?.trim().orEmpty()
        if (expected.isNotEmpty() && readNode(id).rev != expected) throw ConflictError()
        return writeTransaction(id, payload, expected)
    }

    suspend fun delete(id: String) {
        ensureSignedIn()
        requireValidBinId(id)
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

    private suspend fun readNode(id: String): FirebaseCloudNode = retryRead {
        val snapshot = root().child(id).get().await()
        FirebaseCloudNode(
            payload = snapshot.child("payload").getValue(String::class.java),
            rev = FirebaseRevision.fromSnapshot(snapshot)?.toString()
        )
    }

    private suspend fun writeTransaction(id: String, payload: String, expected: String): String {
        return suspendCancellableCoroutine { continuation ->
            root().child(id).runTransaction(object : Transaction.Handler {
                override fun doTransaction(data: MutableData): Transaction.Result {
                    val current = FirebaseRevision.fromValue(data.child("meta").child("rev").value)
                    if (!FirebaseRevision.matchesExpected(current, expected)) return Transaction.abort()
                    val next = FirebaseRevision.next(current)
                    data.child("payload").value = payload
                    data.child("meta").child("rev").value = next
                    return Transaction.success(data)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, data: DataSnapshot?) {
                    if (!continuation.isActive) return
                    if (error != null) {
                        continuation.resumeWithException(error.toException())
                        return
                    }
                    val revision = data?.let(FirebaseRevision::fromSnapshot)
                    if (!committed || revision == null) continuation.resumeWithException(ConflictError())
                    else continuation.resume(revision.toString())
                }
            }, false)
        }
    }

    private fun requireValidBinId(id: String) {
        if (!FirebaseRevision.isValidBinId(id)) throw InvalidBinIdError()
    }
}

internal data class FirebaseCloudNode(val payload: String?, val rev: String?)

internal object FirebaseRevision {
    private val validBinIdPattern = Regex("^[A-Za-z0-9_-]{1,64}$")

    fun isValidBinId(id: String): Boolean = validBinIdPattern.matches(id)

    fun fromSnapshot(snapshot: DataSnapshot): Long? {
        return fromValue(snapshot.child("meta").child("rev").value)
    }

    fun fromValue(value: Any?): Long? {
        return when (value) {
            is Number -> value.toLong()
            is String -> value.trim().toLongOrNull()
            else -> null
        }
    }

    fun matchesExpected(current: Long?, expected: String): Boolean {
        return expected.isEmpty() || current?.toString() == expected
    }

    fun next(current: Long?, now: Long = System.currentTimeMillis()): Long {
        val incremented = current?.let { if (it == Long.MAX_VALUE) it else it + 1L } ?: 1L
        return maxOf(now, incremented)
    }
}

internal class ConflictError : Exception("Conflict")
internal class InvalidBinIdError : Exception("Invalid Link Code")

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
