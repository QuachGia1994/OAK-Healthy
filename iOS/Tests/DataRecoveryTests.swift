import SwiftData
import XCTest
@testable import OAKHealthy

@MainActor
final class DataRecoveryTests: XCTestCase {
    func testPreviewBlocksDuplicateSupplementIdsAndOrphanHistory() throws {
        let duplicate = supplement(id: supplementId, name: "Duplicate")
        let orphan = OAKBackupHistory(
            id: "orphan",
            supplementId: "33333333-3333-3333-3333-333333333333",
            dateEpochMs: 1_700_000_000_000,
            status: "Taken",
            updatedAtEpochMs: 1_700_000_000_100
        )
        let backup = OAKBackupData(
            version: "2.0",
            meta: nil,
            stack: [supplement(), duplicate],
            history: [orphan],
            historyZlibBase64: nil
        )

        let preview = try SupplementExportCodec.previewBackup(JSONEncoder().encode(backup))

        XCTAssertEqual(preview.sourceSchema, "oak-2.0")
        XCTAssertEqual(preview.duplicateSupplementIdCount, 1)
        XCTAssertEqual(preview.orphanHistoryCount, 1)
        XCTAssertFalse(preview.canRestore)
    }

    func testImportPreservesHistoryBeyondLegacyFiveThousandRecordCap() throws {
        let schema = Schema(versionedSchema: OAKSchemaV1.self)
        let configuration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
        let container = try ModelContainer(
            for: schema,
            migrationPlan: OAKSchemaMigrationPlan.self,
            configurations: [configuration]
        )
        let context = ModelContext(container)
        let client = ClientProfile(name: "Recovery Client")
        context.insert(client)
        try context.save()
        let backup = OAKBackupData(
            version: "2.0",
            meta: nil,
            stack: [supplement()],
            history: history(count: 5_001),
            historyZlibBase64: nil
        )

        try SupplementExportCodec.importBackupData(backup, client: client, context: context)

        let records = try context.fetch(FetchDescriptor<IntakeRecord>())
        XCTAssertEqual(records.count, 5_001)
        XCTAssertTrue(records.allSatisfy { $0.supplement?.client?.id == client.id })
    }

    func testRestoreTransactionRollsBackCapturedSnapshotOnApplyFailure() {
        let snapshot = OAKBackupData(
            version: "2.0",
            meta: nil,
            stack: [supplement(name: "Snapshot")],
            history: [],
            historyZlibBase64: nil
        )
        var restored: OAKBackupData?

        XCTAssertThrowsError(
            try BackupRestoreTransaction.run(
                snapshot: snapshot,
                apply: { throw RecoveryTestError.persistence },
                rollback: { restored = $0 }
            )
        )

        XCTAssertEqual(restored?.stack.map(\.name), ["Snapshot"])
    }

    private func history(count: Int) -> [OAKBackupHistory] {
        (0..<count).map { index in
            let timestamp = Int64(1_700_000_000_000 + index * 60_000)
            return OAKBackupHistory(
                id: "record-\(index)",
                supplementId: supplementId,
                dateEpochMs: timestamp,
                status: index.isMultiple(of: 2) ? "Taken" : "Skipped",
                updatedAtEpochMs: timestamp + 1
            )
        }
    }

    private func supplement(
        id: String = supplementId,
        name: String = "Magnesium"
    ) -> OAKBackupSupplement {
        OAKBackupSupplement(
            id: id,
            name: name,
            dailyDose: "200 mg",
            intakeTime: "21:00",
            startDate: "2026-01-01",
            cycle: SupplementExportCycle(
                isContinuous: true,
                daysOn: 1,
                daysOff: 0,
                durationMonths: nil,
                weeklyWeekdaysMask: nil,
                weeklyIntervalWeeks: nil,
                weeklyAnchorDate: nil,
                intervalDays: nil
            ),
            lastTakenLocalDate: "2026-01-02",
            updatedAtEpochMs: 1_700_000_000_000,
            deletedAtEpochMs: nil
        )
    }

    private var supplementId: String {
        "11111111-1111-1111-1111-111111111111"
    }
}

private enum RecoveryTestError: Error {
    case persistence
}
