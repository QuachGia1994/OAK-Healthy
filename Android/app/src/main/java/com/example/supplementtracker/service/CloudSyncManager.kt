package com.example.supplementtracker.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CloudDownload(val json: String?, val etag: String?)

class CloudSyncManager {
    suspend fun uploadBackup(jsonString: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                Result.success(FirebaseCloudStore.createBin(jsonString))
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }
    }
    
    suspend fun upsertBackup(binId: String, jsonString: String): Result<Unit> {
        val id = binId.trim()
        if (!FirebaseRevision.isValidBinId(id)) return Result.failure(InvalidBinIdError())
        return withContext(Dispatchers.IO) {
            try {
                FirebaseCloudStore.write(id, jsonString, expectedRev = null)
                Result.success(Unit)
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }
    }
    
    suspend fun upsertBackup(binId: String, jsonString: String, ifMatchEtag: String?): Result<String?> {
        val id = binId.trim()
        val tag = ifMatchEtag.orEmpty().trim()
        if (!FirebaseRevision.isValidBinId(id)) return Result.failure(InvalidBinIdError())
        return withContext(Dispatchers.IO) {
            try {
                val newTag = FirebaseCloudStore.write(id, jsonString, expectedRev = tag.takeIf { it.isNotEmpty() })
                Result.success(newTag)
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }
    }

    suspend fun downloadBackup(binId: String): Result<String> {
        val id = binId.trim()
        if (!FirebaseRevision.isValidBinId(id)) return Result.failure(InvalidBinIdError())
        return withContext(Dispatchers.IO) {
            try {
                Result.success(FirebaseCloudStore.readAlways(id).json.orEmpty())
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }
    }

    suspend fun downloadBackupIfChanged(binId: String, etag: String?): Result<CloudDownload> {
        val id = binId.trim()
        val tag = etag.orEmpty().trim()
        if (!FirebaseRevision.isValidBinId(id)) return Result.failure(InvalidBinIdError())
        return withContext(Dispatchers.IO) {
            try {
                Result.success(FirebaseCloudStore.readIfChanged(id, knownRev = tag.takeIf { it.isNotEmpty() }))
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }
    }
    
    suspend fun downloadBackupAlways(binId: String): Result<CloudDownload> {
        val id = binId.trim()
        if (!FirebaseRevision.isValidBinId(id)) return Result.failure(InvalidBinIdError())
        return withContext(Dispatchers.IO) {
            try {
                Result.success(FirebaseCloudStore.readAlways(id))
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }
    }
    
    suspend fun deleteBackup(binId: String): Result<Unit> {
        val id = binId.trim()
        if (!FirebaseRevision.isValidBinId(id)) return Result.failure(InvalidBinIdError())
        return withContext(Dispatchers.IO) {
            try {
                FirebaseCloudStore.delete(id)
                Result.success(Unit)
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }
    }
}
