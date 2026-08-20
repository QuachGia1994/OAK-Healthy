import XCTest
import SwiftData
@testable import OAKHealthy

@MainActor
final class ClientScopedStoreTests: XCTestCase {
    func testActiveSupplementsAreClientScopedAndExcludeDeleted() throws {
        let context = ModelContext(try makeContainer())
        let target = ClientProfile(name: "Target")
        let other = ClientProfile(name: "Other")
        context.insert(target)
        context.insert(other)
        context.insert(makeSupplement(name: "Zinc", client: target))
        context.insert(makeSupplement(name: "Magnesium", client: other))
        context.insert(makeSupplement(name: "Deleted", client: target, deletedAt: 10))
        try context.save()

        let result = try ClientScopedStore.activeSupplements(
            modelContext: context,
            clientId: target.id
        )

        XCTAssertEqual(result.map(\.name), ["Zinc"])
    }

    func testHistoryLimitAppliesAfterClientPredicate() throws {
        let context = ModelContext(try makeContainer())
        let (target, other) = try seedClients(context: context)
        let targetSupplement = makeSupplement(name: "Target", client: target)
        let otherSupplement = makeSupplement(name: "Other", client: other)
        context.insert(targetSupplement)
        context.insert(otherSupplement)
        for day in 1...3 {
            context.insert(makeRecord(day: day, supplement: targetSupplement))
        }
        for day in 10...14 {
            context.insert(makeRecord(day: day, supplement: otherSupplement))
        }
        try context.save()

        let records = try ClientScopedStore.historyRecords(
            modelContext: context,
            clientId: target.id,
            limit: 2
        )

        XCTAssertEqual(records.count, 2)
        XCTAssertTrue(records.allSatisfy { $0.supplement?.client?.id == target.id })
        XCTAssertGreaterThan(records[0].date, records[1].date)
    }

    func testChangeDetectionIgnoresOtherClients() throws {
        let context = ModelContext(try makeContainer())
        let (target, other) = try seedClients(context: context)
        let targetSupplement = makeSupplement(name: "Target", client: target, updatedAt: 50)
        let otherSupplement = makeSupplement(name: "Other", client: other, updatedAt: 200)
        context.insert(targetSupplement)
        context.insert(otherSupplement)
        context.insert(makeRecord(day: 1, supplement: otherSupplement, updatedAt: 200))
        try context.save()

        XCTAssertFalse(try ClientScopedStore.hasSupplementChanges(
            modelContext: context,
            clientId: target.id,
            since: 100
        ))
        XCTAssertFalse(try ClientScopedStore.hasHistoryChanges(
            modelContext: context,
            clientId: target.id,
            since: 100
        ))

        context.insert(makeRecord(day: 2, supplement: targetSupplement, updatedAt: 300))
        try context.save()
        XCTAssertTrue(try ClientScopedStore.hasHistoryChanges(
            modelContext: context,
            clientId: target.id,
            since: 100
        ))
    }

    private func seedClients(context: ModelContext) throws -> (ClientProfile, ClientProfile) {
        let target = ClientProfile(name: "Target")
        let other = ClientProfile(name: "Other")
        context.insert(target)
        context.insert(other)
        try context.save()
        return (target, other)
    }

    private func makeSupplement(
        name: String,
        client: ClientProfile,
        updatedAt: Int64 = 0,
        deletedAt: Int64? = nil
    ) -> UserSupplement {
        UserSupplement(
            name: name,
            startDate: .now,
            cycleConfig: CycleConfig(daysOn: 1, daysOff: 0, isContinuous: true),
            dailyDose: "1",
            intakeTime: "08:00",
            updatedAtEpochMs: updatedAt,
            deletedAtEpochMs: deletedAt,
            client: client
        )
    }

    private func makeRecord(
        day: Int,
        supplement: UserSupplement,
        updatedAt: Int64 = 0
    ) -> IntakeRecord {
        IntakeRecord(
            date: Date(timeIntervalSince1970: TimeInterval(day * 86_400)),
            status: IntakeStatus.taken.rawValue,
            updatedAtEpochMs: updatedAt,
            supplement: supplement
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
