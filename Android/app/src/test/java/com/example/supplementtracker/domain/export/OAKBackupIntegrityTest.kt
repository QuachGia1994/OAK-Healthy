package com.example.supplementtracker.domain.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OAKBackupIntegrityTest {
    @Test
    fun canonicalDigestMatchesCrossPlatformFixture() {
        assertEquals(
            "2e1efab8d9ebcae33d23c2dcc945ea7fd618bbf15864c77647f5717fc3f6daf4",
            OAKBackupIntegrity.digest(fixture())
        )
    }

    @Test
    fun previewReportsVerifiedManifest() {
        val preview = OAKBackupJson.preview(OAKBackupJson.encode(fixture())).getOrThrow()

        assertEquals("2.0", preview.version)
        assertEquals(1, preview.supplementCount)
        assertEquals(1, preview.historyCount)
        assertTrue(preview.integrityVerified)
    }

    @Test
    fun legacyPayloadPreviewRemainsReadableWithoutIntegrity() {
        val legacy = """{"version":"2.0","supplements":[],"historyLogs":[]}"""
        val preview = OAKBackupJson.preview(legacy).getOrThrow()

        assertEquals(0, preview.supplementCount)
        assertEquals(0, preview.historyCount)
        assertFalse(preview.integrityVerified)
    }

    @Test
    fun previewRejectsTamperedPayloadWithoutLegacyFallback() {
        val encoded = OAKBackupJson.encode(fixture())
        val tampered = encoded.replace("5 g", "6 g")

        assertTrue(OAKBackupJson.preview(tampered).isFailure)
        assertTrue(OAKBackupJson.decodeCompat(tampered).isFailure)
    }

    @Test
    fun changedPayloadFailsManifestValidation() {
        val data = fixture()
        val manifest = OAKBackupIntegrity.create(data)
        val changed = data.copy(stack = data.stack.map { it.copy(dailyDose = "6 g") })

        assertTrue(OAKBackupIntegrity.validate(changed, manifest).isFailure)
    }

    private fun fixture(): OAKBackupDataDTO {
        val cycle = SupplementExportCycleDTO(true, 1, 0, null)
        val supplement = OAKBackupSupplementDTO(
            id = "11111111-1111-1111-1111-111111111111",
            name = "Creatine",
            dailyDose = "5 g",
            intakeTime = "12:30",
            startDate = "2026-01-01",
            cycle = cycle,
            updatedAtEpochMs = 1_700_000_000_100,
            modifiedFields = setOf("name", "dailyDose", "intakeTime")
        )
        val history = OAKBackupHistoryDTO(
            id = "dose-key",
            supplementId = supplement.id,
            dateEpochMs = 1_700_000_000_200,
            status = "Taken",
            updatedAtEpochMs = 1_700_000_000_300
        )
        return OAKBackupDataDTO(
            version = "2.0",
            meta = OAKBackupMetaDTO(2, 1_700_000_000_000, "device-a"),
            stack = listOf(supplement),
            history = listOf(history)
        )
    }
}
