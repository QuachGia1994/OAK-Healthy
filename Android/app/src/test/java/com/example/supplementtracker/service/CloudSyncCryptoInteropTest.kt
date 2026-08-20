package com.example.supplementtracker.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Base64

class CloudSyncCryptoInteropTest {
    private val key = Base64.getDecoder().decode("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=")
    private val nonce = "AAECAwQFBgcICQoL"
    private val ciphertext = "PCC5eq7H+DnkL+Puw4YIQPXnpUmVBOFtPs/RqbLwTBZTYsL5"

    @Test
    fun decryptsCrossPlatformAesGcmFixture() {
        assertEquals("{\"oak\":\"interop-v1\"}", decrypt(envelope(ciphertext)))
    }

    @Test
    fun decryptsLegacyNoncePrefixedCiphertext() {
        val legacy = "AAECAwQFBgcICQoLPCC5eq7H+DnkL+Puw4YIQPXnpUmVBOFtPs/RqbLwTBZTYsL5"
        assertEquals("{\"oak\":\"interop-v1\"}", decrypt(envelope(legacy)))
    }

    @Test
    fun rejectsTamperedCiphertext() {
        val replacement = if (ciphertext.last() == 'A') 'B' else 'A'
        val tampered = ciphertext.dropLast(1) + replacement
        assertThrows(CloudSyncCryptoError.CryptoFailed::class.java) { decrypt(envelope(tampered)) }
    }

    @Test
    fun rejectsInvalidKeyIdentifierBeforeKeyLookup() {
        val payload = envelope(ciphertext).replace("interop-key", "../key")
        assertThrows(CloudSyncCryptoError.InvalidPayload::class.java) { decrypt(payload) }
    }

    @Test
    fun rejectsPlaintextDowngradeWhenEncryptionIsEnabled() {
        assertThrows(CloudSyncCryptoError.InvalidPayload::class.java) {
            CloudSyncCrypto.validateEncryptionMode(
                localUsesEncryption = true,
                cloudUsesEncryption = false
            )
        }
    }

    @Test
    fun keyIdentifierValidationRejectsWhitespace() {
        assertEquals(false, CloudSyncCrypto.isValidKeyId(" interop-key "))
    }

    private fun decrypt(payload: String): String {
        return CloudSyncCrypto.unwrapDownloadedWithKeyResolver(payload) { keyId ->
            key.takeIf { keyId == "interop-key" }
        }
    }

    private fun envelope(ct: String): String {
        return """{"enc":{"v":1,"alg":"A256GCM","kid":"interop-key","nonce":"$nonce","ct":"$ct"}}"""
    }
}
