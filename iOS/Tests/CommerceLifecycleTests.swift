import XCTest
@testable import OAKHealthy

final class CommerceLifecycleTests: XCTestCase {
    func testActiveVerifiedEventAppliesHighestPlanOnce() {
        var snapshot = EntitlementSnapshot.free
        var processor = CommerceLifecycleProcessor(
            verifier: FixedCommerceVerifier(.verified([
                CommercialProductCatalog.proAnnual,
                CommercialProductCatalog.coachMonthly
            ]))
        )
        let event = makeEvent(id: "active-1", state: .active)

        XCTAssertEqual(processor.process(event) { snapshot = $0 }, .applied)
        XCTAssertEqual(snapshot.plan, .coach)
        XCTAssertEqual(processor.process(event) { snapshot = $0 }, .duplicate)
    }

    func testGracePeriodRetainsVerifiedPaidAccess() {
        var snapshot = EntitlementSnapshot.free
        var processor = CommerceLifecycleProcessor(
            verifier: FixedCommerceVerifier(.verified([CommercialProductCatalog.proMonthly]))
        )

        _ = processor.process(makeEvent(id: "grace-1", state: .gracePeriod)) { snapshot = $0 }

        XCTAssertEqual(snapshot.plan, .pro)
    }

    func testHoldAndUnverifiedFailClosed() {
        var snapshot = EntitlementSnapshot(plan: .coach, activeProductId: CommercialProductCatalog.coachAnnual)
        var processor = CommerceLifecycleProcessor(
            verifier: FixedCommerceVerifier(.verified([CommercialProductCatalog.coachAnnual]))
        )

        XCTAssertEqual(
            processor.process(makeEvent(id: "hold-1", state: .onHold)) { snapshot = $0 },
            .rejected
        )
        XCTAssertEqual(snapshot, .free)

        snapshot = .init(plan: .pro, activeProductId: CommercialProductCatalog.proMonthly)
        var unverified = CommerceLifecycleProcessor(verifier: FixedCommerceVerifier(.unverified))
        XCTAssertEqual(
            unverified.process(makeEvent(id: "bad-1", state: .active)) { snapshot = $0 },
            .rejected
        )
        XCTAssertEqual(snapshot, .free)
    }

    func testUnavailableVerificationCanRetrySameEvent() {
        let verifier = MutableCommerceVerifier(.unavailable)
        var snapshot = EntitlementSnapshot.free
        var processor = CommerceLifecycleProcessor(verifier: verifier)
        let event = makeEvent(id: "retry-1", state: .active)

        XCTAssertEqual(processor.process(event) { snapshot = $0 }, .deferred)
        verifier.result = .verified([CommercialProductCatalog.proAnnual])
        XCTAssertEqual(processor.process(event) { snapshot = $0 }, .applied)
        XCTAssertEqual(snapshot.plan, .pro)
    }

    func testUnknownVerifiedProductNeverGrantsPaidAccess() {
        var snapshot = EntitlementSnapshot(plan: .coach, activeProductId: CommercialProductCatalog.coachMonthly)
        var processor = CommerceLifecycleProcessor(
            verifier: FixedCommerceVerifier(.verified(["unknown_product"]))
        )

        XCTAssertEqual(
            processor.process(makeEvent(id: "unknown-1", state: .active)) { snapshot = $0 },
            .rejected
        )
        XCTAssertEqual(snapshot, .free)
    }

    private func makeEvent(id: String, state: CommerceLifecycleState) -> CommerceLifecycleEvent {
        CommerceLifecycleEvent(
            eventId: id,
            source: .sandboxFixture,
            productIds: [CommercialProductCatalog.proAnnual],
            state: state,
            observedAtEpochMs: 1_700_000_000_000
        )
    }
}

private struct FixedCommerceVerifier: CommerceEntitlementVerifying {
    let result: CommerceVerificationResult

    init(_ result: CommerceVerificationResult) {
        self.result = result
    }

    func verify(_ event: CommerceLifecycleEvent) -> CommerceVerificationResult {
        result
    }
}

private final class MutableCommerceVerifier: CommerceEntitlementVerifying {
    var result: CommerceVerificationResult

    init(_ result: CommerceVerificationResult) {
        self.result = result
    }

    func verify(_ event: CommerceLifecycleEvent) -> CommerceVerificationResult {
        result
    }
}
