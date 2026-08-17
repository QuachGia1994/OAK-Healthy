import Foundation

public enum ActivationMilestone: String, CaseIterable, Hashable, Sendable {
    case clientReady = "client_ready"
    case routineReady = "routine_ready"
    case firstAction = "first_action"
    case reminderReady = "reminder_ready"
}

public struct ActivationProgress: Equatable, Sendable {
    public let completed: Set<ActivationMilestone>

    public var coreCompletedCount: Int {
        Self.coreMilestones.filter(completed.contains).count
    }

    public var firstValueReached: Bool {
        Self.coreMilestones.allSatisfy(completed.contains)
    }

    public var nextCoreMilestone: ActivationMilestone? {
        Self.coreMilestones.first { !completed.contains($0) }
    }

    public static let coreMilestones: [ActivationMilestone] = [
        .clientReady,
        .routineReady,
        .firstAction
    ]
}

public enum ActivationRetentionStore {
    public static func progress(defaults: UserDefaults = .standard) -> ActivationProgress {
        let completed = Set(ActivationMilestone.allCases.filter { defaults.bool(forKey: key($0)) })
        return ActivationProgress(completed: completed)
    }

    @discardableResult
    public static func mark(
        _ milestone: ActivationMilestone,
        defaults: UserDefaults = .standard
    ) -> Bool {
        guard !defaults.bool(forKey: key(milestone)) else { return false }
        defaults.set(true, forKey: key(milestone))
        DiagnosticsReporter.event(
            "activation_milestone",
            fields: ["milestone": milestone.rawValue, "state": "reached"]
        )
        return true
    }

    public static func reconcile(
        clientReady: Bool,
        routineReady: Bool,
        firstAction: Bool,
        reminderReady: Bool,
        defaults: UserDefaults = .standard
    ) -> ActivationProgress {
        if clientReady { mark(.clientReady, defaults: defaults) }
        if routineReady { mark(.routineReady, defaults: defaults) }
        if firstAction { mark(.firstAction, defaults: defaults) }
        if reminderReady { mark(.reminderReady, defaults: defaults) }
        return progress(defaults: defaults)
    }

    private static func key(_ milestone: ActivationMilestone) -> String {
        "oakActivation_\(milestone.rawValue)"
    }
}
