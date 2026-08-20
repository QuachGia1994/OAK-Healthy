import Foundation
import SwiftData

enum PendingImportApplyResult: Equatable {
    case applied
    case previewChanged(OAKBackupPreview)
    case notificationsPending
}

enum PendingImportRecoveryError: LocalizedError, Equatable {
    case clientLimitReached
    case invalidClientIdentity
    case ambiguousLegacyClient
    case rollbackFailed

    var errorDescription: String? {
        switch self {
        case .clientLimitReached: "plan_client_limit_reached".localized
        case .invalidClientIdentity: "safe_mode_invalid_client_identity".localized
        case .ambiguousLegacyClient: "safe_mode_ambiguous_client".localized
        case .rollbackFailed: "safe_mode_rollback_failed".localized
        }
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
        clientId: String,
        clientName: String,
        linkedBinId: String,
        notificationsEnabled: Bool
    ) async throws -> PendingImportApplyResult {
        let current = try SupplementExportCodec.previewBackup(data)
        guard current == approvedPreview else { return .previewChanged(current) }
        let resolution = try resolveClient(id: clientId, name: clientName)
        do {
            return try await applyValidatedBackup(
                data: data,
                resolution: resolution,
                linkedBinId: linkedBinId,
                notificationsEnabled: notificationsEnabled
            )
        } catch {
            try rollbackOrThrow(resolution)
            throw error
        }
    }

    private func applyValidatedBackup(
        data: Data,
        resolution: (client: ClientProfile, created: Bool),
        linkedBinId: String,
        notificationsEnabled: Bool
    ) async throws -> PendingImportApplyResult {
        try SupplementExportCodec.importBackup(
            data: data, client: resolution.client, context: modelContext
        )
        activeClientManager.setCurrentClientId(resolution.client.id)
        applyLink(linkedBinId, clientId: resolution.client.id)
        guard notificationsEnabled else { return .applied }
        return await reschedule(clientId: resolution.client.id) ? .applied : .notificationsPending
    }

    private func rollbackOrThrow(_ resolution: (client: ClientProfile, created: Bool)) throws {
        do {
            try rollbackCreatedClient(resolution)
        } catch {
            throw PendingImportRecoveryError.rollbackFailed
        }
    }

    private func resolveClient(id rawId: String, name: String) throws -> (client: ClientProfile, created: Bool) {
        let existing = try modelContext.fetch(FetchDescriptor<ClientProfile>())
        let trimmedId = rawId.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmedId.isEmpty {
            guard let id = UUID(uuidString: trimmedId) else { throw PendingImportRecoveryError.invalidClientIdentity }
            if let matched = existing.first(where: { $0.id == id }) { return (matched, false) }
            return try createClient(id: id, name: name, existingCount: existing.count)
        }
        let normalized = ClientNamePolicy.canonical(name)
        guard !normalized.isEmpty else { throw PendingImportRecoveryError.invalidClientIdentity }
        let matches = existing.filter { ClientNamePolicy.canonical($0.name) == normalized }
        if matches.count == 1, let matched = matches.first { return (matched, false) }
        if matches.count > 1 { throw PendingImportRecoveryError.ambiguousLegacyClient }
        return try createClient(id: UUID(), name: name, existingCount: existing.count)
    }

    private func createClient(
        id: UUID,
        name: String,
        existingCount: Int
    ) throws -> (client: ClientProfile, created: Bool) {
        if let limit = entitlementManager.maxClients, existingCount >= limit {
            throw PendingImportRecoveryError.clientLimitReached
        }
        let storedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedName = storedName.isEmpty ? "imported_client_default_name".localized : storedName
        let client = try ClientProfileMutationStore.create(id: id, name: resolvedName, in: modelContext)
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

    private func rollbackCreatedClient(_ resolution: (client: ClientProfile, created: Bool)) throws {
        guard resolution.created else { return }
        try ClientProfileMutationStore.delete(resolution.client, in: modelContext)
    }

}
