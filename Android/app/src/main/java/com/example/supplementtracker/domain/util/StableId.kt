package com.example.supplementtracker.domain.util

import java.security.MessageDigest
import java.util.UUID

object StableId {
    fun hexSha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val b = bytes[i].toInt() and 0xff
            out[i * 2] = "0123456789abcdef"[b ushr 4]
            out[i * 2 + 1] = "0123456789abcdef"[b and 0x0f]
        }
        return String(out)
    }

    fun uuidFromString(key: String): UUID {
        val bytes = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        return UUID(
            toLong(bytes, 0),
            toLong(bytes, 8)
        )
    }
    
    private fun toLong(bytes: ByteArray, offset: Int): Long {
        var value = 0L
        for (i in 0 until 8) {
            value = (value shl 8) or (bytes[offset + i].toLong() and 0xffL)
        }
        return value
    }
}
