import Foundation
import Observation

public enum CommercialPlan: String, CaseIterable, Equatable, Hashable, Sendable {
    case free
    case pro
    case coach
}

public enum CommercialFeature: CaseIterable, Hashable, Sendable {
    case basicTracking
    case reminders
    case recentHistory
    case advancedCycles
    case unlimitedHistory
    case adherenceAnalytics
    case encryptedCloudSync
    case dataExport
    case multiClient
    case coachReports
}

public enum BillingPeriod: Equatable, Sendable {
    case monthly
    case annual
}

public struct CommercialProduct: Equatable, Sendable {
    public let productId: String
    public let plan: CommercialPlan
    public let billingPeriod: BillingPeriod
}

public struct EntitlementSnapshot: Equatable, Sendable {
    public let plan: CommercialPlan
    public let activeProductId: String?

    public init(plan: CommercialPlan, activeProductId: String? = nil) {
        self.plan = plan
        self.activeProductId = activeProductId
    }

    public static let free = EntitlementSnapshot(plan: .free)
}

public enum CommercialProductCatalog {
    public static let proMonthly = "oak_pro_monthly"
    public static let proAnnual = "oak_pro_annual"
    public static let coachMonthly = "oak_coach_monthly"
    public static let coachAnnual = "oak_coach_annual"

    public static let products = [
        CommercialProduct(productId: proMonthly, plan: .pro, billingPeriod: .monthly),
        CommercialProduct(productId: proAnnual, plan: .pro, billingPeriod: .annual),
        CommercialProduct(productId: coachMonthly, plan: .coach, billingPeriod: .monthly),
        CommercialProduct(productId: coachAnnual, plan: .coach, billingPeriod: .annual)
    ]
}

public enum CommercialEntitlementResolver {
    public static func resolve(productIds: some Sequence<String>) -> EntitlementSnapshot {
        let ids = Set(productIds)
        let matches = CommercialProductCatalog.products.filter { ids.contains($0.productId) }
        guard let highest = matches.max(by: { planRank($0.plan) < planRank($1.plan) }) else {
            return .free
        }
        return EntitlementSnapshot(plan: highest.plan, activeProductId: highest.productId)
    }

    private static func planRank(_ plan: CommercialPlan) -> Int {
        switch plan {
        case .free: return 0
        case .pro: return 1
        case .coach: return 2
        }
    }
}

public enum EntitlementPolicy {
    private static let freeFeatures: Set<CommercialFeature> = [
        .basicTracking,
        .reminders,
        .recentHistory
    ]
    private static let proFeatures: Set<CommercialFeature> = freeFeatures.union([
        .advancedCycles,
        .unlimitedHistory,
        .adherenceAnalytics,
        .encryptedCloudSync,
        .dataExport
    ])

    public static func allows(plan: CommercialPlan, feature: CommercialFeature) -> Bool {
        switch plan {
        case .free: return freeFeatures.contains(feature)
        case .pro: return proFeatures.contains(feature)
        case .coach: return true
        }
    }

    public static func maxClients(plan: CommercialPlan) -> Int? {
        switch plan {
        case .free, .pro: return 1
        case .coach: return nil
        }
    }
}

@MainActor
@Observable
public final class EntitlementManager {
    public private(set) var snapshot: EntitlementSnapshot

    public init(initialSnapshot: EntitlementSnapshot = .free) {
        snapshot = initialSnapshot
    }

    public func replaceFromStore(_ snapshot: EntitlementSnapshot) {
        self.snapshot = snapshot
    }

    public func resetToFree() {
        snapshot = .free
    }
}
