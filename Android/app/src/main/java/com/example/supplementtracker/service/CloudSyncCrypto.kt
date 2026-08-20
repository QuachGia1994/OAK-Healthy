package com.example.supplementtracker.service

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

sealed class CloudSyncCryptoError(message: String) : Exception(message) {
    data class MissingKey(val keyId: String) : CloudSyncCryptoError("Missing cloud sync key: $keyId")
    data class InvalidPayload(val reason: String) : CloudSyncCryptoError("Invalid encrypted payload: $reason")
    data class CryptoFailed(val reason: String) : CloudSyncCryptoError("Crypto failed: $reason")
}

object CloudSyncCrypto {
    private const val prefsName = "oak_settings"
    private const val enabledKey = "cloudSyncEncryptionEnabled"
    private const val currentKeyIdKey = "cloudSyncEncCurrentKeyId"
    private const val previousKeyIdKey = "cloudSyncEncPreviousKeyId"
    private const val keyPrefix = "cloudSyncEncKey_"
    private const val wrappedPrefix = "w1:"
    private const val keystoreAlias = "cloudSyncEncMasterKeyV1"
    private val keyIdPattern = Regex("^[A-Za-z0-9_-]{1,64}$")

    internal fun isValidKeyId(keyId: String): Boolean = keyIdPattern.matches(keyId)

    fun isEnabled(context: Context): Boolean {
        val prefs = OakPrefs.get(context)
        return prefs.getBoolean(enabledKey, false)
    }

    fun setEnabled(context: Context, enabled: Boolean): Result<Unit> {
        val prefs = OakPrefs.get(context)
        prefs.edit().putBoolean(enabledKey, enabled).apply()
        if (!enabled) return Result.success(Unit)
        return runCatching { ensureKeyExists(context) }
            .map { Unit }
            .onFailure {
                prefs.edit().putBoolean(enabledKey, false).apply()
            }
    }

    fun ensureKeyExists(context: Context): String {
        val prefs = OakPrefs.get(context)
        val current = prefs.getString(currentKeyIdKey, null)?.trim().orEmpty()
        if (isValidKeyId(current) && resolveKey(context, current) != null) return current
        return rotateKey(context)
    }

    fun exportCurrentKey(context: Context): String? {
        val prefs = OakPrefs.get(context)
        val keyId = prefs.getString(currentKeyIdKey, null)?.trim().orEmpty()
        if (!isValidKeyId(keyId)) return null
        val raw = resolveKey(context, keyId) ?: return null
        val encoded = Base64.getEncoder().encodeToString(raw)
        return "$keyId:$encoded"
    }

    fun importKey(context: Context, exported: String): String {
        val raw = exported.trim()
        val parts = raw.split(":")
        if (parts.size != 2) throw CloudSyncCryptoError.InvalidPayload("Expected format keyId:base64")
        val keyId = parts[0].trim()
        val b64 = parts[1].trim()
        if (!isValidKeyId(keyId) || b64.isEmpty()) throw CloudSyncCryptoError.InvalidPayload("Invalid keyId or empty key data")
        val decoded = runCatching { Base64.getDecoder().decode(b64) }.getOrNull()
            ?: throw CloudSyncCryptoError.InvalidPayload("Invalid base64")
        if (decoded.size != 32) throw CloudSyncCryptoError.InvalidPayload("Expected 32 bytes key")
        val prefs = OakPrefs.get(context)
        val stored = wrapKeyForStorage(decoded)
        val old = prefs.getString(currentKeyIdKey, null)?.trim().orEmpty()
        val editor = prefs.edit()
            .putString(keyPrefix + keyId, stored)
            .putString(currentKeyIdKey, keyId)
        if (old.isNotEmpty() && old != keyId) editor.putString(previousKeyIdKey, old)
        editor.apply()
        return keyId
    }

    fun clearLocalKeyMaterial(): Result<Unit> = runCatching {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(keystoreAlias)) {
            keyStore.deleteEntry(keystoreAlias)
        }
    }

    private fun rotateKey(context: Context): String {
        val prefs = OakPrefs.get(context)
        val old = prefs.getString(currentKeyIdKey, null)?.trim().orEmpty()
        val keyId = UUID.randomUUID().toString()
        val keyBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val stored = wrapKeyForStorage(keyBytes)
        val editor = prefs.edit()
            .putString(keyPrefix + keyId, stored)
            .putString(currentKeyIdKey, keyId)
        if (old.isNotEmpty()) editor.putString(previousKeyIdKey, old)
        editor.apply()
        return keyId
    }

    fun wrapForUploadIfEnabled(context: Context, plaintextJson: String): String {
        if (!isEnabled(context)) return plaintextJson
        val keyId = ensureKeyExists(context)
        val key = requireKey(context, keyId)
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val ciphertext = encryptAesGcm(key, nonce, plaintextJson.toByteArray(Charsets.UTF_8))
        val enc = JSONObject()
            .put("v", 1)
            .put("alg", "A256GCM")
            .put("kid", keyId)
            .put("nonce", Base64.getEncoder().encodeToString(nonce))
            .put("ct", Base64.getEncoder().encodeToString(ciphertext))
        return JSONObject().put("enc", enc).toString()
    }

    fun unwrapDownloadedIfNeeded(context: Context, payloadJson: String): String {
        val objectValue = runCatching { JSONObject(payloadJson) }.getOrNull()
        val cloudUsesEncryption = objectValue?.has("enc") == true
        val localUsesEncryption = isEnabled(context)
        validateEncryptionMode(localUsesEncryption, cloudUsesEncryption)
        val plaintext = unwrapDownloadedWithKeyResolver(payloadJson) { keyId -> resolveKey(context, keyId) }
        if (cloudUsesEncryption && !localUsesEncryption) {
            OakPrefs.get(context).edit().putBoolean(enabledKey, true).apply()
        }
        return plaintext
    }

    internal fun validateEncryptionMode(localUsesEncryption: Boolean, cloudUsesEncryption: Boolean) {
        if (localUsesEncryption && !cloudUsesEncryption) {
            throw CloudSyncCryptoError.InvalidPayload("Rejected plaintext downgrade")
        }
    }

    internal fun unwrapDownloadedWithKeyResolver(
        payloadJson: String,
        keyResolver: (String) -> ByteArray?
    ): String {
        val envelope = parseEnvelope(payloadJson) ?: return payloadJson
        val key = keyResolver(envelope.keyId) ?: throw CloudSyncCryptoError.MissingKey(envelope.keyId)
        val plaintext = decryptWithLegacyFallback(key, envelope.nonce, envelope.ciphertext)
        return plaintext.toString(Charsets.UTF_8)
    }

    private fun parseEnvelope(payloadJson: String): CloudSyncEnvelope? {
        val obj = runCatching { JSONObject(payloadJson) }.getOrNull() ?: return null
        if (!obj.has("enc")) return null
        val enc = obj.optJSONObject("enc") ?: throw CloudSyncCryptoError.InvalidPayload("Invalid enc envelope")
        val version = enc.optInt("v", -1)
        val alg = enc.optString("alg").trim()
        if (version != 1 || alg != "A256GCM") throw CloudSyncCryptoError.InvalidPayload("Unsupported enc header")
        val kid = enc.optString("kid").trim()
        if (!isValidKeyId(kid)) throw CloudSyncCryptoError.InvalidPayload("Invalid kid")
        val nonceB64 = enc.optString("nonce").trim()
        val ctB64 = enc.optString("ct").trim()
        if (nonceB64.isEmpty() || ctB64.isEmpty()) throw CloudSyncCryptoError.InvalidPayload("Missing nonce/ct")
        val nonce = runCatching { Base64.getDecoder().decode(nonceB64) }.getOrNull()
            ?: throw CloudSyncCryptoError.InvalidPayload("Invalid nonce base64")
        if (nonce.size != 12) throw CloudSyncCryptoError.InvalidPayload("Invalid nonce length")
        val ciphertextRaw = runCatching { Base64.getDecoder().decode(ctB64) }.getOrNull()
            ?: throw CloudSyncCryptoError.InvalidPayload("Invalid ct base64")
        if (ciphertextRaw.size < 16) throw CloudSyncCryptoError.InvalidPayload("Missing GCM tag")
        return CloudSyncEnvelope(kid, nonce, ciphertextRaw)
    }

    private fun decryptWithLegacyFallback(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray): ByteArray {
        return runCatching { decryptAesGcm(key, nonce, ciphertext) }.getOrElse { error ->
            val hasNoncePrefix = ciphertext.size >= 28 &&
                ciphertext.copyOfRange(0, 12).contentEquals(nonce)
            if (!hasNoncePrefix) throw error
            decryptAesGcm(key, nonce, ciphertext.copyOfRange(12, ciphertext.size))
        }
    }

    private fun resolveKey(context: Context, keyId: String): ByteArray? {
        if (!isValidKeyId(keyId)) return null
        val prefs = OakPrefs.get(context)
        val direct = readKeyEntry(prefs, keyId)
        if (direct != null) return direct
        val previous = prefs.getString(previousKeyIdKey, null)?.trim().orEmpty()
        if (previous == keyId) return readKeyEntry(prefs, previous)
        return null
    }

    private fun readKeyEntry(prefs: android.content.SharedPreferences, keyId: String): ByteArray? {
        if (!isValidKeyId(keyId)) return null
        val stored = prefs.getString(keyPrefix + keyId, null)?.trim().orEmpty()
        if (stored.isEmpty()) return null
        if (stored.startsWith(wrappedPrefix)) return unwrapKeyFromStorage(stored)
        val decoded = runCatching { Base64.getDecoder().decode(stored) }.getOrNull() ?: return null
        if (decoded.size != 32) return null
        val migrated = wrapKeyForStorage(decoded)
        prefs.edit().putString(keyPrefix + keyId, migrated).apply()
        return decoded
    }

    private fun requireKey(context: Context, keyId: String): ByteArray {
        return resolveKey(context, keyId) ?: throw CloudSyncCryptoError.MissingKey(keyId)
    }
    
    private fun wrapKeyForStorage(rawKey: ByteArray): String {
        if (rawKey.size != 32) throw CloudSyncCryptoError.CryptoFailed("Expected 32 bytes key")
        return runCatching {
            val master = getOrCreateMasterKey()
            val (iv, ciphertext) = encryptAesGcm(master, rawKey)
            val nonceB64 = Base64.getEncoder().encodeToString(iv)
            val ctB64 = Base64.getEncoder().encodeToString(ciphertext)
            wrappedPrefix + nonceB64 + ":" + ctB64
        }.getOrElse { throw CloudSyncCryptoError.CryptoFailed(it.message ?: "Key wrap failed") }
    }
    
    private fun unwrapKeyFromStorage(stored: String): ByteArray? {
        val raw = stored.trim()
        if (!raw.startsWith(wrappedPrefix)) return null
        val pieces = raw.removePrefix(wrappedPrefix).split(":")
        if (pieces.size != 2) return null
        val nonce = runCatching { Base64.getDecoder().decode(pieces[0]) }.getOrNull() ?: return null
        val ciphertext = runCatching { Base64.getDecoder().decode(pieces[1]) }.getOrNull() ?: return null
        if (nonce.size !in 12..16) return null
        if (ciphertext.size < 16) return null
        val plaintext = runCatching {
            val master = getOrCreateMasterKey()
            decryptAesGcm(master, nonce, ciphertext)
        }.getOrNull() ?: return null
        return plaintext.takeIf { it.size == 32 }
    }
    
    private fun getOrCreateMasterKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = (ks.getEntry(keystoreAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey
        if (existing != null) return existing
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            keystoreAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encryptAesGcm(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray {
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(128, nonce)
            )
            cipher.doFinal(plaintext)
        }.getOrElse { throw CloudSyncCryptoError.CryptoFailed(it.message ?: "Encrypt failed") }
    }

    private fun encryptAesGcm(key: SecretKey, plaintext: ByteArray): Pair<ByteArray, ByteArray> {
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val ciphertext = cipher.doFinal(plaintext)
            Pair(cipher.iv, ciphertext)
        }.getOrElse { throw CloudSyncCryptoError.CryptoFailed(it.message ?: "Encrypt failed") }
    }

    private fun decryptAesGcm(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray): ByteArray {
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(128, nonce)
            )
            cipher.doFinal(ciphertext)
        }.getOrElse { throw CloudSyncCryptoError.CryptoFailed(it.message ?: "Decrypt failed") }
    }

    private fun decryptAesGcm(key: SecretKey, nonce: ByteArray, ciphertext: ByteArray): ByteArray {
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(128, nonce)
            )
            cipher.doFinal(ciphertext)
        }.getOrElse { throw CloudSyncCryptoError.CryptoFailed(it.message ?: "Decrypt failed") }
    }
}

internal data class CloudSyncEnvelope(
    val keyId: String,
    val nonce: ByteArray,
    val ciphertext: ByteArray
)
