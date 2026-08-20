import XCTest
@testable import OAKHealthy

final class BackupIntegrityTests: XCTestCase {
    func testCanonicalDigestMatchesCrossPlatformFixture() {
        XCTAssertEqual(
            OAKBackupIntegrityCodec.digest(fixture()),
            "2e1efab8d9ebcae33d23c2dcc945ea7fd618bbf15864c77647f5717fc3f6daf4"
        )
    }

    @MainActor
    func testPreviewReportsVerifiedManifest() throws {
        var data = fixture()
        data.integrity = OAKBackupIntegrityCodec.create(data)
        let encoded = try JSONEncoder().encode(data)

        let preview = try SupplementExportCodec.previewBackup(encoded)

        XCTAssertEqual(preview.version, "2.0")
        XCTAssertEqual(preview.supplementCount, 1)
        XCTAssertEqual(preview.historyCount, 1)
        XCTAssertTrue(preview.integrityVerified)
    }

    @MainActor
    func testPreviewRejectsTamperedManifest() throws {
        var data = fixture()
        data.integrity = OAKBackupIntegrityCodec.create(data)
        data.stack[0].dailyDose = "6 g"
        let encoded = try JSONEncoder().encode(data)

        XCTAssertThrowsError(try SupplementExportCodec.previewBackup(encoded))
    }

    func testCompatDecodeDoesNotDowngradeTamperedManifestToLegacy() throws {
        var data = fixture()
        data.integrity = OAKBackupIntegrityCodec.create(data)
        data.stack[0].dailyDose = "6 g"
        let encoded = try JSONEncoder().encode(data)

        XCTAssertThrowsError(try SupplementExportCodec.decodeBackupCompat(data: encoded))
    }

    func testChangedPayloadFailsManifestValidation() {
        let data = fixture()
        let manifest = OAKBackupIntegrityCodec.create(data)
        var changed = data
        changed.stack[0].dailyDose = "6 g"

        XCTAssertThrowsError(try OAKBackupIntegrityCodec.validate(changed, manifest: manifest))
    }

    private func fixture() -> OAKBackupData {
        let cycle = SupplementExportCycle(
            isContinuous: true,
            daysOn: 1,
            daysOff: 0,
            durationMonths: nil,
            weeklyWeekdaysMask: nil,
            weeklyIntervalWeeks: nil,
            weeklyAnchorDate: nil,
            intervalDays: nil
        )
        let supplement = OAKBackupSupplement(
            id: "11111111-1111-1111-1111-111111111111",
            name: "Creatine",
            dailyDose: "5 g",
            intakeTime: "12:30",
            startDate: "2026-01-01",
            cycle: cycle,
            lastTakenLocalDate: nil,
            updatedAtEpochMs: 1_700_000_000_100,
            deletedAtEpochMs: nil,
            modifiedFields: ["name", "dailyDose", "intakeTime"]
        )
        let history = OAKBackupHistory(
            id: "dose-key",
            supplementId: supplement.id,
            dateEpochMs: 1_700_000_000_200,
            status: "Taken",
            updatedAtEpochMs: 1_700_000_000_300
        )
        return OAKBackupData(
            version: "2.0",
            meta: OAKBackupMeta(schemaVersion: 2, updatedAtEpochMs: 1_700_000_000_000, deviceId: "device-a"),
            stack: [supplement],
            history: [history],
            historyZlibBase64: nil
        )
    }
}
