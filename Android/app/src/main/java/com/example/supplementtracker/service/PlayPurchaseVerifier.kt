package com.example.supplementtracker.service

import java.nio.charset.StandardCharsets
import java.util.Base64
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

interface PurchaseSignatureVerifier {
    val isConfigured: Boolean
    fun verify(originalJson: String, signature: String): Boolean
}

class PlayPurchaseVerifier(
    publicKeyBase64: String
) : PurchaseSignatureVerifier {
    private val normalizedKey = publicKeyBase64.filterNot(Char::isWhitespace)

    override val isConfigured: Boolean
        get() = normalizedKey.isNotEmpty()

    override fun verify(originalJson: String, signature: String): Boolean {
        if (!isConfigured || originalJson.isBlank() || signature.isBlank()) return false
        return runCatching {
            val publicKey = decodePublicKey(normalizedKey)
            val verifier = Signature.getInstance("SHA1withRSA")
            verifier.initVerify(publicKey)
            verifier.update(originalJson.toByteArray(StandardCharsets.UTF_8))
            verifier.verify(Base64.getDecoder().decode(signature))
        }.getOrDefault(false)
    }

    private fun decodePublicKey(encodedKey: String) = KeyFactory.getInstance("RSA").generatePublic(
        X509EncodedKeySpec(Base64.getDecoder().decode(encodedKey))
    )
}
