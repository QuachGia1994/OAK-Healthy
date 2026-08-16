package com.example.supplementtracker.service

import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayPurchaseVerifierTest {
    @Test
    fun blankKeyFailsClosed() {
        val verifier = PlayPurchaseVerifier("")

        assertFalse(verifier.isConfigured)
        assertFalse(verifier.verify("{}", "signature"))
    }

    @Test
    fun validSignaturePassesAndTamperedPayloadFails() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val publicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val payload = "{\"productId\":\"oak_pro_annual\"}"
        val signature = sign(payload, keyPair.private)
        val verifier = PlayPurchaseVerifier(publicKey)

        assertTrue(verifier.isConfigured)
        assertTrue(verifier.verify(payload, signature))
        assertFalse(verifier.verify("$payload ", signature))
    }

    private fun sign(payload: String, privateKey: java.security.PrivateKey): String {
        val signer = Signature.getInstance("SHA1withRSA")
        signer.initSign(privateKey)
        signer.update(payload.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(signer.sign())
    }
}
