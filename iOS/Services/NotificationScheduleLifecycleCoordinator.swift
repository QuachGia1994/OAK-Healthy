import Foundation
import SwiftData

@MainActor
struct NotificationScheduleLifecycleCoordinator {
    let modelContext: ModelContext
    let activeClientManager: ActiveClientManager
    let notificationService: NotificationService

    func rescheduleIfEnabled(_ enabled: Bool) async {
        guard enabled else { return }
        guard let clientId = activeClientManager.currentClientId else {
            await notificationService.clearAllPendingNotifications()
            return
        }
        do {
            let supplements = try activeSupplements(clientId: clientId)
            await notificationService.replaceAllSchedules(supplements: supplements)
        } catch {
            DebugReporter.report("auto_reschedule_fetch_failed", fields: ["error_type": "supplement_fetch"])
        }
    }

    func reconcileIfEnabled(_ enabled: Bool, environmentChanged: Bool = false) async {
        guard enabled, let clientId = activeClientManager.currentClientId else { return }
        do {
            let supplements = try activeSupplements(clientId: clientId)
            _ = await notificationService.reconcileSchedulesIfNeeded(
                supplements: supplements,
                forceEnvironmentChanged: environmentChanged
            )
        } catch {
            DebugReporter.report("auto_reconcile_fetch_failed", fields: ["error_type": "supplement_fetch"])
        }
    }

    private func activeSupplements(clientId: UUID) throws -> [UserSupplement] {
        try ClientScopedStore.activeSupplements(modelContext: modelContext, clientId: clientId)
    }
}
