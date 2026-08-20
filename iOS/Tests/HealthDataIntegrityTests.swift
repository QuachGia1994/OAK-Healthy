import XCTest
import SwiftData
@testable import OAKHealthy

@MainActor
final class HealthDataIntegrityTests: XCTestCase {
    func testLocalDayCodecPreservesCalendarDayAcrossTimeZones() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = try XCTUnwrap(TimeZone(identifier: "Asia/Ho_Chi_Minh"))
        let original = try XCTUnwrap(calendar.date(from: DateComponents(year: 2026, month: 8, day: 18, hour: 0, minute: 15)))

        let encoded = LocalDayCodec.string(from: original, calendar: calendar)
        let decoded = try XCTUnwrap(LocalDayCodec.date(from: encoded, calendar: calendar))

        XCTAssertEqual(encoded, "2026-08-18")
        XCTAssertEqual(calendar.component(.day, from: decoded), 18)
        XCTAssertEqual(LocalDayCodec.string(from: decoded, calendar: calendar), encoded)

        var buddhist = Calendar(identifier: .buddhist)
        buddhist.timeZone = calendar.timeZone
        XCTAssertEqual(LocalDayCodec.string(from: original, calendar: buddhist), "2026-08-18")
    }

    func testDoseTimingPolicyOwnsThresholdsAndCompletionFormula() {
        let scheduled: Int64 = 1_000_000
        XCTAssertTrue(DoseTimingPolicy.isDueSoon(
            scheduledAtEpochMs: scheduled,
            nowEpochMs: scheduled - DoseTimingPolicy.soonWindowMilliseconds
        ))
        XCTAssertFalse(DoseTimingPolicy.isMissed(
            scheduledAtEpochMs: scheduled,
            nowEpochMs: scheduled + DoseTimingPolicy.missedAfterMilliseconds
        ))
        XCTAssertTrue(DoseTimingPolicy.isMissed(
            scheduledAtEpochMs: scheduled,
            nowEpochMs: scheduled + DoseTimingPolicy.missedAfterMilliseconds + 1
        ))
        XCTAssertTrue(DoseTimingPolicy.isLateTaken(
            status: IntakeStatus.taken.rawValue,
            scheduledAtEpochMs: scheduled,
            updatedAtEpochMs: scheduled + DoseTimingPolicy.soonWindowMilliseconds + 1
        ))
        XCTAssertEqual(DoseTimingPolicy.completionPercent(taken: 3, skipped: 1), 75)
        XCTAssertNil(DoseTimingPolicy.completionPercent(taken: 0, skipped: 0))
    }

    func testDosePersistenceIsCanonicalAndIdempotent() throws {
        let context = ModelContext(try makeContainer())
        let client = ClientProfile(name: "Dose Owner")
        let supplement = makeSupplement(name: "Magnesium", client: client)
        context.insert(client)
        context.insert(supplement)
        try context.save()
        let scheduled = Date(timeIntervalSince1970: 1_777_000_000.123456)

        let first = try SupplementHistoryMutationStore.recordDose(
            supplement: supplement,
            scheduledAt: scheduled,
            intakeTime: "08:00",
            status: .taken,
            updatedAtEpochMs: 1_777_000_100_000,
            in: context
        )
        let second = try SupplementHistoryMutationStore.recordDose(
            supplement: supplement,
            scheduledAt: scheduled,
            intakeTime: "08:00",
            status: .taken,
            updatedAtEpochMs: 1_777_000_200_000,
            in: context
        )
        let records = try context.fetch(FetchDescriptor<IntakeRecord>())

        XCTAssertTrue(first.inserted)
        XCTAssertFalse(second.inserted)
        XCTAssertEqual(records.count, 1)
        XCTAssertEqual(first.record.id, second.record.id)
        XCTAssertEqual(Int64(first.record.date.timeIntervalSince1970 * 1_000), Int64(scheduled.timeIntervalSince1970 * 1_000))
        XCTAssertEqual(supplement.lastTakenLocalDate, LocalDayCodec.string(from: scheduled))
    }

    func testRoutineMutationEditsWithoutCreatingDuplicateModel() throws {
        let context = ModelContext(try makeContainer())
        let client = ClientProfile(name: "Routine Owner")
        let existing = makeSupplement(name: "Before", client: client)
        context.insert(client)
        context.insert(existing)
        try context.save()
        let draft = SupplementRoutineDraft(
            id: existing.id,
            name: "After",
            startDate: existing.startDate,
            cycleConfig: existing.cycleConfig,
            dailyDose: "2",
            intakeTime: "09:00",
            client: client
        )

        let result = try SupplementRoutineMutationStore.persist(
            draft: draft,
            editing: existing,
            at: 123,
            in: context
        )
        let supplements = try context.fetch(FetchDescriptor<UserSupplement>())

        XCTAssertTrue(result.wasEditing)
        XCTAssertEqual(supplements.count, 1)
        XCTAssertEqual(existing.name, "After")
        XCTAssertEqual(existing.dailyDose, "2")
        XCTAssertEqual(existing.intakeTime, "09:00")
        XCTAssertEqual(existing.updatedAtEpochMs, 123)
    }

    func testClientMutationRejectsCanonicalDuplicateName() throws {
        let context = ModelContext(try makeContainer())
        _ = try ClientProfileMutationStore.create(name: "Ánh", in: context)

        XCTAssertThrowsError(try ClientProfileMutationStore.create(name: " anh ", in: context)) { error in
            XCTAssertEqual(error as? ClientProfileMutationError, .duplicateName)
        }
        XCTAssertEqual(try context.fetch(FetchDescriptor<ClientProfile>()).count, 1)
    }

    func testPendingRecoveryTargetsStableClientIdNotAmbiguousName() async throws {
        let context = ModelContext(try makeContainer())
        let targetId = UUID(uuidString: "11111111-1111-1111-1111-111111111111")!
        let otherId = UUID(uuidString: "22222222-2222-2222-2222-222222222222")!
        let target = ClientProfile(id: targetId, name: "Same Name")
        let other = ClientProfile(id: otherId, name: "Same Name")
        context.insert(target)
        context.insert(other)
        try context.save()

        let sourceClient = ClientProfile(name: "Source")
        let sourceSupplement = makeSupplement(name: "Vitamin D", client: sourceClient)
        let data = try SupplementExportCodec.encodeBackup(supplements: [sourceSupplement], records: [])
        let preview = try SupplementExportCodec.previewBackup(data)
        let active = ActiveClientManager()
        let coordinator = PendingImportRecoveryCoordinator(
            modelContext: context,
            activeClientManager: active,
            entitlementManager: EntitlementManager(initialSnapshot: EntitlementSnapshot(plan: .coach)),
            notificationService: NotificationService()
        )

        let result = try await coordinator.apply(
            data: data,
            approvedPreview: preview,
            clientId: targetId.uuidString,
            clientName: "Same Name",
            linkedBinId: "",
            notificationsEnabled: false
        )
        let supplements = try context.fetch(FetchDescriptor<UserSupplement>())

        XCTAssertEqual(result, .applied)
        XCTAssertEqual(active.currentClientId, targetId)
        XCTAssertEqual(supplements.filter { $0.client?.id == targetId }.count, 1)
        XCTAssertEqual(supplements.filter { $0.client?.id == otherId }.count, 0)
    }

    func testPendingRecoveryRejectsMissingStableAndLegacyIdentity() async throws {
        let context = ModelContext(try makeContainer())
        let sourceClient = ClientProfile(name: "Source")
        let sourceSupplement = makeSupplement(name: "Vitamin D", client: sourceClient)
        let data = try SupplementExportCodec.encodeBackup(supplements: [sourceSupplement], records: [])
        let preview = try SupplementExportCodec.previewBackup(data)
        let coordinator = PendingImportRecoveryCoordinator(
            modelContext: context,
            activeClientManager: ActiveClientManager(),
            entitlementManager: EntitlementManager(initialSnapshot: EntitlementSnapshot(plan: .coach)),
            notificationService: NotificationService()
        )

        do {
            _ = try await coordinator.apply(
                data: data,
                approvedPreview: preview,
                clientId: "",
                clientName: "",
                linkedBinId: "",
                notificationsEnabled: false
            )
            XCTFail("Expected missing recovery identity to fail closed")
        } catch let error as PendingImportRecoveryError {
            XCTAssertEqual(error, .invalidClientIdentity)
        }
    }

    func testCoachCheckInStoreSurfacesCorruptPersistence() throws {
        let suite = "HealthDataIntegrityTests-\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let clientId = UUID()
        defaults.set(Data("not-json".utf8), forKey: "coachCheckIns_\(clientId.uuidString.lowercased())")

        XCTAssertThrowsError(try CoachCheckInStore.entries(clientId: clientId, defaults: defaults))
    }

    func testBackupDecodeRejectsUnknownIntakeStatus() throws {
        let data = Data("""
        {"version":"2.0","supplements":[{"id":"22222222-2222-2222-2222-222222222222","name":"Imported","dailyDose":"1","intakeTime":"08:00","startDate":"2026-08-10","cycle":{"isContinuous":true,"daysOn":1,"daysOff":0}}],"historyLogs":[{"id":"bad","supplementId":"22222222-2222-2222-2222-222222222222","dateEpochMs":1000,"status":"Maybe","updatedAtEpochMs":2000}]}
        """.utf8)

        XCTAssertThrowsError(try SupplementExportCodec.decodeBackupCompat(data: data))
    }

    func testBackupDecodeRejectsInvalidRecurrence() throws {
        let data = Data("""
        {"version":"2.0","supplements":[{"id":"22222222-2222-2222-2222-222222222222","name":"Imported","dailyDose":"1","intakeTime":"08:00","startDate":"2026-08-10","cycle":{"isContinuous":false,"daysOn":0,"daysOff":2}}],"historyLogs":[]}
        """.utf8)

        XCTAssertThrowsError(try SupplementExportCodec.decodeBackupCompat(data: data))
    }

    func testLegacyExportProducesStableDistinctIdsInsteadOfNameMerge() throws {
        let first = SupplementExportSupplement(
            name: "Magnesium",
            dailyDose: "100 mg",
            intakeTime: "08:00",
            startDate: "2026-08-18",
            category: nil,
            cycle: legacyCycle,
            lastTakenLocalDate: nil
        )
        let second = SupplementExportSupplement(
            name: "Magnesium",
            dailyDose: "200 mg",
            intakeTime: "20:00",
            startDate: "2026-08-18",
            category: nil,
            cycle: legacyCycle,
            lastTakenLocalDate: nil
        )
        let file = SupplementExportFile(schemaVersion: 1, exportedAtEpochMs: 1, supplements: [first, second])
        let data = try JSONEncoder().encode(file)

        let decodedA = try SupplementExportCodec.decodeBackupCompat(data: data)
        let decodedB = try SupplementExportCodec.decodeBackupCompat(data: data)
        let idsA = decodedA.stack.map(\.id)
        let idsB = decodedB.stack.map(\.id)

        XCTAssertEqual(idsA.count, 2)
        XCTAssertEqual(Set(idsA).count, 2)
        XCTAssertEqual(idsA, idsB)
    }

    private var legacyCycle: SupplementExportCycle {
        SupplementExportCycle(
            isContinuous: true,
            daysOn: 1,
            daysOff: 0,
            durationMonths: nil,
            weeklyWeekdaysMask: nil,
            weeklyIntervalWeeks: nil,
            weeklyAnchorDate: nil,
            intervalDays: nil
        )
    }

    private func makeSupplement(name: String, client: ClientProfile) -> UserSupplement {
        UserSupplement(
            name: name,
            startDate: .now,
            cycleConfig: .continuous,
            dailyDose: "1",
            intakeTime: "08:00",
            client: client
        )
    }

    private func makeContainer() throws -> ModelContainer {
        let configuration = ModelConfiguration(isStoredInMemoryOnly: true)
        return try ModelContainer(
            for: ClientProfile.self,
            UserSupplement.self,
            IntakeRecord.self,
            configurations: configuration
        )
    }
}
