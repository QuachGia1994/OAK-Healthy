import XCTest
@testable import OAKHealthy

final class SyncOperationPolicyTests: XCTestCase {
    func testStaleRemoteDeletionCannotEraseNewerLocalEdit() {
        let decision = SyncConflictPolicy.decide(
            localExists: true,
            localTs: 2_000,
            remoteTs: 1_500,
            differs: true
        )

        XCTAssertEqual(decision, .localWins)
        XCTAssertFalse(SyncConflictPolicy.remoteMayApply(localTs: 2_000, remoteTs: 1_500))
    }

    func testEqualTimestampConflictDeterministicallyKeepsLocal() {
        XCTAssertEqual(
            SyncConflictPolicy.decide(localExists: true, localTs: 2_000, remoteTs: 2_000, differs: true),
            .tieLocalWins
        )
    }

    func testNewerRemoteMayApply() {
        XCTAssertTrue(SyncConflictPolicy.remoteMayApply(localTs: 1_500, remoteTs: 2_000))
        XCTAssertEqual(
            SyncConflictPolicy.decide(localExists: true, localTs: 1_500, remoteTs: 2_000, differs: true),
            .remoteWins
        )
    }

    func testBackoffGrowsAndCaps() {
        XCTAssertEqual(SyncBackoffPolicy.delayMs(failureCount: 1), 15_000)
        XCTAssertEqual(SyncBackoffPolicy.delayMs(failureCount: 2), 30_000)
        XCTAssertEqual(SyncBackoffPolicy.delayMs(failureCount: 16), 10 * 60_000)
    }

    func testMutationQueuedAfterSyncStartSurvivesSuccessClear() {
        let suite = "SyncOperationPolicyTests-\(UUID().uuidString)"
        guard let defaults = UserDefaults(suiteName: suite) else {
            return XCTFail("Could not create isolated defaults")
        }
        defer { defaults.removePersistentDomain(forName: suite) }
        let clientId = UUID()
        SyncMutationQueueStore.markDirty(clientId: clientId, part: .stack, nowEpochMs: 100, defaults: defaults)
        SyncMutationQueueStore.markDirty(clientId: clientId, part: .history, nowEpochMs: 300, defaults: defaults)

        SyncMutationQueueStore.clearSynced(
            clientId: clientId,
            parts: Set(SyncMutationPart.allCases),
            syncStartedEpochMs: 200,
            defaults: defaults
        )

        let pending = SyncMutationQueueStore.pending(clientId: clientId, defaults: defaults)
        XCTAssertEqual(pending.map(\.part), [.history])
        XCTAssertEqual(pending.first?.enqueuedAtEpochMs, 300)
    }
}
