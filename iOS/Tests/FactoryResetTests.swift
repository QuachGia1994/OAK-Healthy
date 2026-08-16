import XCTest
import SwiftData
@testable import OAKHealthy

@MainActor
final class FactoryResetTests: XCTestCase {
    func testClearStoredModelsRemovesAllLocalData() throws {
        let container = try makeContainer()
        let context = ModelContext(container)
        let client = ClientProfile(name: "Reset Client")
        let supplement = UserSupplement(
            name: "Creatine",
            startDate: .now,
            cycleConfig: CycleConfig(daysOn: 1, daysOff: 0, isContinuous: true),
            dailyDose: "5 g",
            intakeTime: "08:00",
            client: client
        )
        let record = IntakeRecord(
            date: .now,
            status: IntakeStatus.taken.rawValue,
            supplement: supplement
        )
        context.insert(client)
        context.insert(supplement)
        context.insert(record)
        try context.save()

        try FactoryResetService.clearStoredModels(modelContext: context)

        XCTAssertTrue(try context.fetch(FetchDescriptor<ClientProfile>()).isEmpty)
        XCTAssertTrue(try context.fetch(FetchDescriptor<UserSupplement>()).isEmpty)
        XCTAssertTrue(try context.fetch(FetchDescriptor<IntakeRecord>()).isEmpty)
    }

    func testClearPreferencesRemovesEntireAppDomain() {
        let suite = "FactoryResetTests.\(UUID().uuidString)"
        guard let defaults = UserDefaults(suiteName: suite) else {
            return XCTFail("Unable to create isolated defaults suite")
        }
        defer { defaults.removePersistentDomain(forName: suite) }
        defaults.set("dark", forKey: "appTheme")
        defaults.set(true, forKey: "isAutoSyncEnabled")
        defaults.set("manifest", forKey: "cloudSyncHostedBinId")
        defaults.set("pending", forKey: "oakPendingImportFilePath")

        FactoryResetService.clearPreferences(defaults: defaults, domainName: suite)

        XCTAssertNil(defaults.object(forKey: "appTheme"))
        XCTAssertNil(defaults.object(forKey: "isAutoSyncEnabled"))
        XCTAssertNil(defaults.object(forKey: "cloudSyncHostedBinId"))
        XCTAssertNil(defaults.object(forKey: "oakPendingImportFilePath"))
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
