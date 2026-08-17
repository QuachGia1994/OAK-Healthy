import XCTest
@testable import OAKHealthy

final class ActivationRetentionTests: XCTestCase {
    func testFirstValueRequiresOnlyCoreMilestones() {
        let progress = ActivationProgress(completed: [.clientReady, .routineReady, .firstAction])

        XCTAssertTrue(progress.firstValueReached)
        XCTAssertEqual(progress.coreCompletedCount, 3)
        XCTAssertFalse(progress.completed.contains(.reminderReady))
    }

    func testReconcileIsDurableAndDeterministic() throws {
        let suiteName = "ActivationRetentionTests.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suiteName))
        defer { defaults.removePersistentDomain(forName: suiteName) }

        var progress = ActivationRetentionStore.reconcile(
            clientReady: true,
            routineReady: false,
            firstAction: false,
            reminderReady: false,
            defaults: defaults
        )
        XCTAssertEqual(progress.nextCoreMilestone, .routineReady)

        progress = ActivationRetentionStore.reconcile(
            clientReady: true,
            routineReady: true,
            firstAction: true,
            reminderReady: false,
            defaults: defaults
        )
        XCTAssertTrue(progress.firstValueReached)
        XCTAssertEqual(ActivationRetentionStore.progress(defaults: defaults), progress)
    }

    func testActivationTelemetryDropsSensitiveFields() throws {
        let sanitized = try XCTUnwrap(DiagnosticsPrivacyPolicy.sanitize(
            event: "activation_milestone",
            fields: [
                "milestone": "FIRST_ACTION",
                "state": "REACHED",
                "client_id": "private-client",
                "supplement": "Creatine",
                "dose": "5g",
                "note": "private",
                "product_id": "oak_pro_monthly",
                "plan": "PRO"
            ]
        ))

        XCTAssertEqual(sanitized.1["milestone"], "first_action")
        XCTAssertEqual(sanitized.1["state"], "reached")
        XCTAssertEqual(Set(sanitized.1.keys), Set(["milestone", "state"]))
    }
}
