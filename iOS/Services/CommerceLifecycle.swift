import Foundation

public enum CommerceSource: Equatable {
    case appStore
    case playStore
    case sandboxFixture
}

public enum CommerceLifecycleState: Equatable {
    case active
    case gracePeriod
    case onHold
    case expired
    case revoked
    case refunded
    case unverified
}

public struct CommerceLifecycleEvent: Equatable {
    public let eventId: String
    public let source: CommerceSource
    public let productIds: Set<String>
    public let state: CommerceLifecycleState
    public let observedAtEpochMs: Int64

    public init(
        eventId: String,
        source: CommerceSource,
        productIds: Set<String>,
        state: CommerceLifecycleState,
        observedAtEpochMs: Int64
    ) {
        self.eventId = eventId
        self.source = source
        self.productIds = productIds
        self.state = state
        self.observedAtEpochMs = observedAtEpochMs
    }
}

public enum CommerceVerificationResult: Equatable {
    case verified(Set<String>)
    case unverified
    case unavailable
}

public protocol CommerceEntitlementVerifying {
    func verify(_ event: CommerceLifecycleEvent) -> CommerceVerificationResult
}

public enum CommerceProcessResult: Equatable {
    case applied
    case rejected
    case deferred
    case duplicate
}

public struct CommerceReplayLedger {
    private let capacity: Int
    private var eventIds: [String] = []
    private var eventIdSet: Set<String> = []

    public init(capacity: Int = 256) {
        precondition(capacity > 0, "capacity must be positive")
        self.capacity = capacity
    }

    public func contains(_ eventId: String) -> Bool {
        eventIdSet.contains(eventId)
    }

    public mutating func record(_ eventId: String) {
        guard !eventId.isEmpty, eventIdSet.insert(eventId).inserted else { return }
        eventIds.append(eventId)
        while eventIds.count > capacity {
            let removed = eventIds.removeFirst()
            eventIdSet.remove(removed)
        }
    }
}

public struct CommerceLifecycleProcessor {
    private let verifier: any CommerceEntitlementVerifying
    private var replayLedger: CommerceReplayLedger

    public init(
        verifier: any CommerceEntitlementVerifying,
        replayLedger: CommerceReplayLedger = CommerceReplayLedger()
    ) {
        self.verifier = verifier
        self.replayLedger = replayLedger
    }

    public mutating func process(
        _ event: CommerceLifecycleEvent,
        applySnapshot: (EntitlementSnapshot) -> Void
    ) -> CommerceProcessResult {
        guard !event.eventId.isEmpty else { return .rejected }
        guard !replayLedger.contains(event.eventId) else { return .duplicate }
        switch verifier.verify(event) {
        case .unavailable:
            return .deferred
        case .unverified:
            return reject(event, applySnapshot: applySnapshot)
        case .verified(let productIds):
            return applyVerified(event, productIds: productIds, applySnapshot: applySnapshot)
        }
    }

    private mutating func reject(
        _ event: CommerceLifecycleEvent,
        applySnapshot: (EntitlementSnapshot) -> Void
    ) -> CommerceProcessResult {
        replayLedger.record(event.eventId)
        applySnapshot(.free)
        return .rejected
    }

    private mutating func applyVerified(
        _ event: CommerceLifecycleEvent,
        productIds: Set<String>,
        applySnapshot: (EntitlementSnapshot) -> Void
    ) -> CommerceProcessResult {
        replayLedger.record(event.eventId)
        let snapshot = resolvedSnapshot(event.state, productIds: productIds)
        applySnapshot(snapshot)
        if snapshot == .free, !productIds.isEmpty { return .rejected }
        return .applied
    }

    private func resolvedSnapshot(
        _ state: CommerceLifecycleState,
        productIds: Set<String>
    ) -> EntitlementSnapshot {
        switch state {
        case .active, .gracePeriod:
            return CommercialEntitlementResolver.resolve(productIds: productIds)
        case .onHold, .expired, .revoked, .refunded, .unverified:
            return .free
        }
    }
}
