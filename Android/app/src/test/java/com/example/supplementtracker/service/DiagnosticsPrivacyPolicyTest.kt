package com.example.supplementtracker.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsPrivacyPolicyTest {
    @Test
    fun unknownEventsAreRejected() {
        assertNull(DiagnosticsPrivacyPolicy.sanitize("supplement_taken", emptyMap()))
    }

    @Test
    fun sensitiveAndUnknownFieldsAreDropped() {
        val sanitized = DiagnosticsPrivacyPolicy.sanitize(
            "plan_access_view",
            mapOf(
                "plan" to "PRO",
                "client_id" to "11111111-1111-1111-1111-111111111111",
                "supplement" to "Vitamin D3",
                "error" to "raw server body"
            )
        )

        assertEquals("plan_access_view", sanitized?.first)
        assertEquals(mapOf("plan" to "pro"), sanitized?.second)
        assertTrue(sanitized?.second?.keys?.none { it.contains("client") } == true)
    }

    @Test
    fun valuesAreNormalizedAndBounded() {
        val value = "PRO Annual / Storefront with spaces and extra text"
        val sanitized = DiagnosticsPrivacyPolicy.sanitize(
            "billing_purchase_started",
            mapOf("billing_period" to value)
        )

        val result = sanitized?.second?.get("billing_period").orEmpty()
        assertTrue(result.length <= 40)
        assertTrue(result.matches(Regex("[a-z0-9_-]*")))
    }

    @Test
    fun commercialProductFieldsContainOnlyCatalogMetadata() {
        val fields = CommercialTelemetryFields.product(CommercialProductCatalog.PRO_ANNUAL, "play_store")

        assertEquals("oak_pro_annual", fields["product_id"])
        assertEquals("PRO", fields["plan"])
        assertEquals("ANNUAL", fields["billing_period"])
        assertEquals("play_store", fields["source"])
    }

    @Test
    fun purchaseResultAllowsOnlyCommercialOutcomeFields() {
        val sanitized = DiagnosticsPrivacyPolicy.sanitize(
            "billing_purchase_result",
            CommercialTelemetryFields.result("SUCCESS", "play_store", CommercialProductCatalog.COACH_MONTHLY) +
                mapOf("receipt" to "secret", "supplement" to "Creatine")
        )

        assertEquals("success", sanitized?.second?.get("result"))
        assertEquals("oak_coach_monthly", sanitized?.second?.get("product_id"))
        assertTrue(sanitized?.second?.containsKey("receipt") == false)
        assertTrue(sanitized?.second?.containsKey("supplement") == false)
    }
}
