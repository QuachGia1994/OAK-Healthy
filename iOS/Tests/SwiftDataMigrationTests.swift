import SwiftData
import XCTest
@testable import OAKHealthy

@MainActor
final class SwiftDataMigrationTests: XCTestCase {
    func testVersionedSchemaMatchesLegacyDefaultVersion() {
        let legacySchema = Schema([ClientProfile.self, UserSupplement.self, IntakeRecord.self])
        let versionedSchema = Schema(versionedSchema: OAKSchemaV1.self)

        XCTAssertEqual(legacySchema.version, OAKSchemaV1.versionIdentifier)
        XCTAssertEqual(versionedSchema.version, OAKSchemaV1.versionIdentifier)
    }

    func testVersionedContainerRegistersMigrationPlan() throws {
        let schema = Schema(versionedSchema: OAKSchemaV1.self)
        let configuration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
        let container = try ModelContainer(
            for: schema,
            migrationPlan: OAKSchemaMigrationPlan.self,
            configurations: [configuration]
        )

        XCTAssertNotNil(container.migrationPlan)
        XCTAssertEqual(container.schema.version, OAKSchemaV1.versionIdentifier)
    }

    func testLegacyStoreReopensThroughVersionedMigrationPlan() throws {
        let storeURL = temporaryStoreURL()
        defer { removeStoreFiles(at: storeURL) }

        try seedLegacyStore(at: storeURL)
        try verifyVersionedStore(at: storeURL)
    }

    private func seedLegacyStore(at storeURL: URL) throws {
        let schema = Schema([ClientProfile.self, UserSupplement.self, IntakeRecord.self])
        let configuration = ModelConfiguration(schema: schema, url: storeURL)
        let container = try ModelContainer(for: schema, configurations: [configuration])
        let context = ModelContext(container)
        let client = ClientProfile(name: "Migration Client")
        let supplement = UserSupplement(
            name: "Magnesium",
            startDate: Date(timeIntervalSince1970: 1_700_000_000),
            cycleConfig: .continuous,
            dailyDose: "200 mg",
            intakeTime: "21:00",
            client: client
        )
        context.insert(client)
        context.insert(supplement)
        context.insert(IntakeRecord(
            date: Date(timeIntervalSince1970: 1_700_003_600),
            intakeTime: "21:00",
            supplement: supplement
        ))
        try context.save()
    }

    private func verifyVersionedStore(at storeURL: URL) throws {
        let schema = Schema(versionedSchema: OAKSchemaV1.self)
        let configuration = ModelConfiguration(schema: schema, url: storeURL)
        let container = try ModelContainer(
            for: schema,
            migrationPlan: OAKSchemaMigrationPlan.self,
            configurations: [configuration]
        )
        let context = ModelContext(container)
        let clients = try context.fetch(FetchDescriptor<ClientProfile>())
        let supplements = try context.fetch(FetchDescriptor<UserSupplement>())
        let records = try context.fetch(FetchDescriptor<IntakeRecord>())

        XCTAssertEqual(clients.map(\.name), ["Migration Client"])
        XCTAssertEqual(supplements.map(\.name), ["Magnesium"])
        XCTAssertEqual(records.map(\.intakeTime), ["21:00"])
    }

    private func temporaryStoreURL() -> URL {
        FileManager.default.temporaryDirectory
            .appendingPathComponent("oak-swiftdata-\(UUID().uuidString).store")
    }

    private func removeStoreFiles(at storeURL: URL) {
        let paths = [storeURL.path, storeURL.path + "-shm", storeURL.path + "-wal"]
        for path in paths where FileManager.default.fileExists(atPath: path) {
            try? FileManager.default.removeItem(atPath: path)
        }
    }
}
