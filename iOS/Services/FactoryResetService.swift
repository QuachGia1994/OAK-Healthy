import Foundation
import SwiftData

@MainActor
enum FactoryResetService {
    static func perform(
        modelContext: ModelContext,
        activeClientManager: ActiveClientManager
    ) async throws {
        CloudSyncAutoSync.stopRealtimeSync()
        await NotificationService.shared.clearAllPendingNotifications()
        try clearStoredModels(modelContext: modelContext)
        clearPreferences(
            defaults: .standard,
            domainName: Bundle.main.bundleIdentifier
        )
        try CloudSyncKeyManager.clearLocalKeyMaterial()
        activeClientManager.setCurrentClientId(nil)
    }

    static func clearStoredModels(modelContext: ModelContext) throws {
        try deleteAll(IntakeRecord.self, from: modelContext)
        try deleteAll(UserSupplement.self, from: modelContext)
        try deleteAll(ClientProfile.self, from: modelContext)
        try modelContext.save()
    }

    static func clearPreferences(
        defaults: UserDefaults,
        domainName: String?
    ) {
        guard let domainName, !domainName.isEmpty else {
            defaults.dictionaryRepresentation().keys.forEach(defaults.removeObject)
            return
        }
        defaults.removePersistentDomain(forName: domainName)
    }

    private static func deleteAll<T: PersistentModel>(
        _ type: T.Type,
        from modelContext: ModelContext
    ) throws {
        let items = try modelContext.fetch(FetchDescriptor<T>())
        for item in items {
            modelContext.delete(item)
        }
    }
}
