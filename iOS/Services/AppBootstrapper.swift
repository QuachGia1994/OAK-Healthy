import Foundation
import SwiftData
@preconcurrency import UserNotifications

struct AppDependencyContainer {
    let modelContainer: ModelContainer
    let activeClientManager: ActiveClientManager
    let notificationService: NotificationService
    let entitlementManager: EntitlementManager
    let storeKitBillingService: StoreKitBillingService
}

@MainActor
struct AppBootstrapper {
    func makeDependencies(onContainerReady: () -> Void = {}) throws -> AppDependencyContainer {
        let schema = Schema(versionedSchema: OAKSchemaV1.self)
        let container = try makeModelContainer(schema: schema)
        onContainerReady()
        let manager = ActiveClientManager()
        manager.loadFromStorage()
        validateActiveClient(manager: manager, container: container)
        let notificationService = NotificationService.shared
        UNUserNotificationCenter.current().delegate = NotificationDelegate.shared
        notificationService.registerNotificationActions()
        let entitlementManager = EntitlementManager()
        return AppDependencyContainer(
            modelContainer: container,
            activeClientManager: manager,
            notificationService: notificationService,
            entitlementManager: entitlementManager,
            storeKitBillingService: StoreKitBillingService(entitlementManager: entitlementManager)
        )
    }

    static func persistentStoreURL() -> URL? {
        do {
            let base = try FileManager.default.url(
                for: .applicationSupportDirectory,
                in: .userDomainMask,
                appropriateFor: nil,
                create: true
            )
            return base.appendingPathComponent("OAKHealthy.store")
        } catch {
            DebugReporter.report("appsupport_dir_failed", fields: ["error_type": "directory"])
            return nil
        }
    }

    static func resetPersistentStore() {
        guard let url = persistentStoreURL() else { return }
        let fileManager = FileManager.default
        let candidates = [url, URL(fileURLWithPath: url.path + "-shm"), URL(fileURLWithPath: url.path + "-wal")]
        for file in candidates where fileManager.fileExists(atPath: file.path) {
            try? fileManager.removeItem(at: file)
        }
    }

    private func makeModelContainer(schema: Schema) throws -> ModelContainer {
        guard let storeURL = Self.persistentStoreURL() else {
            return try ModelContainer(for: schema, migrationPlan: OAKSchemaMigrationPlan.self)
        }
        let configuration = ModelConfiguration(schema: schema, url: storeURL)
        do {
            return try ModelContainer(
                for: schema,
                migrationPlan: OAKSchemaMigrationPlan.self,
                configurations: [configuration]
            )
        } catch {
            DebugReporter.report("swiftdata_init_failed", fields: ["error_type": "model_container"])
            throw error
        }
    }

    private func validateActiveClient(manager: ActiveClientManager, container: ModelContainer) {
        guard let stored = manager.currentClientId else { return }
        let context = ModelContext(container)
        let clients = (try? context.fetch(FetchDescriptor<ClientProfile>())) ?? []
        guard clients.contains(where: { $0.id == stored }) else {
            manager.setCurrentClientId(clients.first?.id)
            return
        }
    }
}
