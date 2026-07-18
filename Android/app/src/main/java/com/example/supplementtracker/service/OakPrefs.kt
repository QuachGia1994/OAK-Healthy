package com.example.supplementtracker.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

// ponytail: single entry point for encrypted SharedPreferences.
// Fallback to plain prefs only if KeyStore is corrupted (rooted device edge case).
object OakPrefs {
    private const val PREFS_NAME = "oak_settings"
    private const val RECOVERY_PREFS_NAME = "oak_settings_recovery"
    @Volatile private var cached: SharedPreferences? = null

    fun get(context: Context): SharedPreferences {
        cached?.let { return it }
        return synchronized(this) {
            cached?.let { return@synchronized it }
            val instance = try {
                val masterKey = MasterKey.Builder(context.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context.applicationContext,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (error: Exception) {
                Log.e("OakPrefs", "Encrypted preferences unavailable; using app-sandboxed recovery storage", error)
                context.applicationContext.getSharedPreferences(RECOVERY_PREFS_NAME, Context.MODE_PRIVATE)
            }
            cached = instance
            instance
        }
    }
}
