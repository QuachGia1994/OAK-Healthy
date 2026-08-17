import Foundation
import SwiftData

enum PendingImportApplyResult: Equatable {
    case applied
    case previewChanged(OAKBackupPreview)
    case notificationsPending
}

enum PendingImportRecoveryError: LocalizedError {
    case clientLimitReached

    var errorDescription: String? {
        "plan_client_limit_reached".localized
    }
}

@MainActor
struct PendingImportRecoveryCoordinator {
    let modelContext: ModelContext
    let activeClientManager: ActiveClientManager
    let entitlementManager: EntitlementManager
    let notificationService: NotificationService

    func apply(
        data: Data,
        approvedPreview: OAKBackupPreview,
        clientName: String,
        linkedBinId: String,
        notificationsEnabled: Bool
    ) async throws -> PendingImportApplyResult {
        let current = try SupplementExportCodec.previewBackup(data)
        guard current == approvedPreview else { return .previewChanged(current) }
        let resolution = try resolveClient(name: clientName)
        do {
            try SupplementExportCodec.importBackup(
                data: data, client: resolution.client, context: modelContext
            )
            activeClientManager.setCurrentClientId(resolution.client.id)
            applyLink(linkedBinId, clientId: resolution.client.id)
            if notificationsEnabled {
                guard await reschedule(clientId: resolution.client.id) else {
                    return .notificationsPending
                }
            }
            return .applied
        } catch {
            rollbackCreatedClient(resolution)
            throw error
        }
    }

    private func resolveClient(name: String) throws -> (client: ClientProfile, created: Bool) {
        let storedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalized = storedName.lowercased()
        let existing = try modelContext.fetch(FetchDescriptor<ClientProfile>())
        if !normalized.isEmpty,
           let matched = existing.first(where: { normalizedName($0.name) == normalized }) {
            return (matched, false)
        }
        if let limit = entitlementManager.maxClients, existing.count >= limit {
            throw PendingImportRecoveryError.clientLimitReached
        }
        let resolvedName = storedName.isEmpty ? "imported_client_default_name".localized : storedName
        let client = ClientProfile(id: UUID(), name: resolvedName)
        modelContext.insert(client)
        try modelContext.save()
        return (client, true)
    }

    private func applyLink(_ rawLink: String, clientId: UUID) {
        let linked = rawLink.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !linked.isEmpty else { return }
        CloudSyncProfileStore().setLinkedBinId(linked, clientId: clientId)
    }

    private func reschedule(clientId: UUID) async -> Bool {
        do {
            try await notificationService.requestAuthorization()
            let supplements = try ClientScopedStore.activeSupplements(
                modelContext: modelContext, clientId: clientId
            )
            await notificationService.replaceAllSchedules(supplements: supplements)
            return true
        } catch {
            return false
        }
    }

    private func rollbackCreatedClient(_ resolution: (client: ClientProfile, created: Bool)) {
        guard resolution.created else { return }
        modelContext.delete(resolution.client)
        try? modelContext.save()
    }

    private func normalizedName(_ value: String) -> String {
        value.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
