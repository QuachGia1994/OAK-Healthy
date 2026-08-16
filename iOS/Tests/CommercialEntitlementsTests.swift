import XCTest
@testable import OAKHealthy

final class CommercialEntitlementsTests: XCTestCase {
    func testFreePlanAllowsOnlyCoreTrackingFeatures() {
        XCTAssertTrue(EntitlementPolicy.allows(plan: .free, feature: .basicTracking))
        XCTAssertTrue(EntitlementPolicy.allows(plan: .free, feature: .reminders))
        XCTAssertFalse(EntitlementPolicy.allows(plan: .free, feature: .advancedCycles))
        XCTAssertFalse(EntitlementPolicy.allows(plan: .free, feature: .multiClient))
    }

    func testProPlanExcludesCoachFeatures() {
        XCTAssertTrue(EntitlementPolicy.allows(plan: .pro, feature: .advancedCycles))
        XCTAssertTrue(EntitlementPolicy.allows(plan: .pro, feature: .encryptedCloudSync))
        XCTAssertTrue(EntitlementPolicy.allows(plan: .pro, feature: .dataExport))
        XCTAssertFalse(EntitlementPolicy.allows(plan: .pro, feature: .multiClient))
        XCTAssertFalse(EntitlementPolicy.allows(plan: .pro, feature: .coachReports))
    }

    func testCoachPlanAllowsEveryDefinedFeature() {
        for feature in CommercialFeature.allCases {
            XCTAssertTrue(EntitlementPolicy.allows(plan: .coach, feature: feature))
        }
    }

    func testClientLimitsMatchCommercialPositioning() {
        XCTAssertEqual(EntitlementPolicy.maxClients(plan: .free), 1)
        XCTAssertEqual(EntitlementPolicy.maxClients(plan: .pro), 1)
        XCTAssertNil(EntitlementPolicy.maxClients(plan: .coach))
    }

    func testHistoryWindowsMatchPlanLimits() {
        XCTAssertEqual(EntitlementPolicy.historyDays(plan: .free), 7)
        XCTAssertEqual(EntitlementPolicy.historyDays(plan: .pro), 90)
        XCTAssertEqual(EntitlementPolicy.historyDays(plan: .coach), 365)
    }

    func testProductCatalogUsesUniqueStableIdentifiers() {
        let ids = CommercialProductCatalog.products.map(\.productId)

        XCTAssertEqual(Set(ids).count, ids.count)
        XCTAssertEqual(
            Set(ids),
            Set(["oak_pro_monthly", "oak_pro_annual", "oak_coach_monthly", "oak_coach_annual"])
        )
    }

    func testResolverFailsClosedForUnknownProducts() {
        XCTAssertEqual(CommercialEntitlementResolver.resolve(productIds: []), .free)
        XCTAssertEqual(CommercialEntitlementResolver.resolve(productIds: ["unknown"]), .free)
    }

    func testResolverChoosesHighestOwnedPlan() {
        let resolved = CommercialEntitlementResolver.resolve(
            productIds: [CommercialProductCatalog.proAnnual, CommercialProductCatalog.coachMonthly]
        )

        XCTAssertEqual(resolved.plan, .coach)
        XCTAssertEqual(resolved.activeProductId, CommercialProductCatalog.coachMonthly)
    }

    @MainActor
    func testEntitlementManagerFailsClosedToFree() {
        let manager = EntitlementManager()

        XCTAssertEqual(manager.snapshot, .free)
        manager.replaceFromStore(.init(plan: .pro, activeProductId: CommercialProductCatalog.proAnnual))
        XCTAssertEqual(manager.snapshot.plan, .pro)
        manager.resetToFree()
        XCTAssertEqual(manager.snapshot, .free)
        XCTAssertFalse(manager.canUse(.encryptedCloudSync))
        XCTAssertEqual(manager.maxClients, 1)
        XCTAssertEqual(manager.historyDays, 7)
    }
}
