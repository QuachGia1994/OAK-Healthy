import SwiftUI
import SwiftData
@preconcurrency import UserNotifications

private enum BootKeys {
    static let stage = "oakBootStage"
    static let timestampEpoch = "oakBootTimestampEpoch"
    static let uiReady = "ui_ready"
    static let bootStarted = "boot_started"
    static let containerReady = "container_ready"
}

@main
struct SupplementTrackerApp: App {
    @AppStorage("appTheme") private var appTheme: String = "system"
    @State private var selectedTab: Int = 0
    @State private var isAppLaunched: Bool = false
    @State private var dependencies: AppDependencyContainer?
    
    var body: some Scene {
        WindowGroup {
            RootLaunchView(
                selectedTab: $selectedTab,
                preferredColorScheme: preferredColorScheme,
                isAppLaunched: $isAppLaunched,
                dependencies: $dependencies
            )
        }
    }
    
    private var preferredColorScheme: ColorScheme? {
        switch appTheme {
        case "light": return .light
        case "dark": return .dark
        default: return nil
        }
    }
}

private struct RootLaunchView: View {
    @Binding var selectedTab: Int
    let preferredColorScheme: ColorScheme?
    @Binding var isAppLaunched: Bool
    @Binding var dependencies: AppDependencyContainer?
    
    var body: some View {
        if isAppLaunched, let dependencies {
            MainTabView(
                selectedTab: $selectedTab,
                activeClientManager: dependencies.activeClientManager,
                notificationService: dependencies.notificationService
            )
            .preferredColorScheme(preferredColorScheme)
            .modelContainer(dependencies.modelContainer)
            .task {
                UserDefaults.standard.set(BootKeys.uiReady, forKey: BootKeys.stage)
                UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: BootKeys.timestampEpoch)
            }
        } else {
            SafeBootView { container in
                dependencies = container
                isAppLaunched = true
            }
        }
    }
}

@preconcurrency
class NotificationDelegate: NSObject, UNUserNotificationCenterDelegate {
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse, withCompletionHandler completionHandler: @escaping () -> Void) {
        NotificationCenter.default.post(name: NSNotification.Name("OpenDashboard"), object: nil)
        completionHandler()
    }
    
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .sound, .badge])
    }
}

struct AppDependencyContainer {
    let modelContainer: ModelContainer
    let activeClientManager: ActiveClientManager
    let notificationService: NotificationService
    let notificationDelegate: NotificationDelegate
}

private struct SafeBootView: View {
    let onReady: (AppDependencyContainer) -> Void
    @State private var errorMessage: String?
    @State private var hasBootstrapped: Bool = false
    
    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color(.systemGroupedBackground), Color(.systemBackground)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            
            VStack(spacing: 16) {
                OAKLogoView()
                    .frame(width: 140, height: 140)
                if let errorMessage {
                    Text(errorMessage)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                } else {
                    ProgressView()
                }
            }
        }
        .task {
            guard !hasBootstrapped else { return }
            hasBootstrapped = true
            await bootstrap()
        }
    }
    
    @MainActor
    private func bootstrap() async {
        try? await Task.sleep(for: .seconds(2))
        attemptCrashRecoveryIfNeeded()
        UserDefaults.standard.set(BootKeys.bootStarted, forKey: BootKeys.stage)
        UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: BootKeys.timestampEpoch)
        let schema = Schema([ClientProfile.self, UserSupplement.self, IntakeRecord.self])
        guard let container = makeModelContainer(schema: schema) else {
            errorMessage = "Không thể khởi tạo dữ liệu. Dữ liệu cũ có thể đã bị lỗi."
            return
        }
        UserDefaults.standard.set(BootKeys.containerReady, forKey: BootKeys.stage)
        UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: BootKeys.timestampEpoch)
        
        let manager = ActiveClientManager()
        manager.loadFromStorage()
        validateActiveClient(manager: manager, container: container)
        
        let delegate = NotificationDelegate()
        UNUserNotificationCenter.current().delegate = delegate
        
        let notificationService = NotificationService()
        onReady(
            AppDependencyContainer(
                modelContainer: container,
                activeClientManager: manager,
                notificationService: notificationService,
                notificationDelegate: delegate
            )
        )
    }
    
    @MainActor
    private func makeModelContainer(schema: Schema) -> ModelContainer? {
        guard let storeURL = persistentStoreURL() else { return try? ModelContainer(for: schema) }
        let configuration = ModelConfiguration(schema: schema, url: storeURL)
        
        do {
            return try ModelContainer(for: schema, configurations: configuration)
        } catch {
            print("SwiftData init failed: \(error.localizedDescription)")
            resetPersistentStore(at: storeURL)
            return try? ModelContainer(for: schema, configurations: configuration)
        }
    }
    
    @MainActor
    private func persistentStoreURL() -> URL? {
        do {
            let base = try FileManager.default.url(
                for: .applicationSupportDirectory,
                in: .userDomainMask,
                appropriateFor: nil,
                create: true
            )
            return base.appendingPathComponent("OAKHealthy.store")
        } catch {
            print("AppSupport directory error: \(error.localizedDescription)")
            return nil
        }
    }
    
    @MainActor
    private func resetPersistentStore(at url: URL) {
        let fileManager = FileManager.default
        let candidates = [url, URL(fileURLWithPath: url.path + "-shm"), URL(fileURLWithPath: url.path + "-wal")]
        for file in candidates {
            guard fileManager.fileExists(atPath: file.path) else { continue }
            try? fileManager.removeItem(at: file)
        }
    }
    
    @MainActor
    private func validateActiveClient(manager: ActiveClientManager, container: ModelContainer) {
        guard let stored = manager.currentClientId else { return }
        let context = ModelContext(container)
        let clients = (try? context.fetch(FetchDescriptor<ClientProfile>())) ?? []
        guard clients.contains(where: { $0.id == stored }) else {
            manager.setCurrentClientId(clients.first?.id)
            return
        }
    }
    
    @MainActor
    private func attemptCrashRecoveryIfNeeded() {
        let lastStage = UserDefaults.standard.string(forKey: BootKeys.stage) ?? ""
        guard !lastStage.isEmpty, lastStage != BootKeys.uiReady else { return }
        let lastEpoch = UserDefaults.standard.double(forKey: BootKeys.timestampEpoch)
        guard lastEpoch > 0 else { return }
        let elapsed = Date().timeIntervalSince1970 - lastEpoch
        guard elapsed < 600 else { return }
        UserDefaults.standard.removeObject(forKey: "activeClientId")
        guard let url = persistentStoreURL() else { return }
        resetPersistentStore(at: url)
    }
}

struct MainTabView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.scenePhase) private var scenePhase
    @Binding var selectedTab: Int
    let activeClientManager: ActiveClientManager
    let notificationService: NotificationService
    
    var body: some View {
        TabView(selection: $selectedTab) {
            HomeView(
                activeClientManager: activeClientManager,
                notificationService: notificationService
            )
                .id(activeClientManager.currentClientId)
                .tabItem {
                    Label("tab_home".localized, systemImage: "house.fill")
                }
                .tag(0)
            
            HistoryView(activeClientManager: activeClientManager)
                .id(activeClientManager.currentClientId)
                .tabItem {
                    Label("tab_history".localized, systemImage: "clock.fill")
                }
                .tag(1)
            
            SettingsView(activeClientManager: activeClientManager)
                .id(activeClientManager.currentClientId)
                .tabItem {
                    Label("tab_settings".localized, systemImage: "gearshape.fill")
                }
                .tag(2)
        }
        .toolbarBackground(.ultraThinMaterial, for: .tabBar)
        .toolbarBackground(.visible, for: .tabBar)
        .onChange(of: scenePhase) { _, newPhase in
            guard UserDefaults.standard.bool(forKey: "isAutoSyncEnabled") else {
                CloudSyncAutoSync.stopRealtimeSync()
                return
            }
            if newPhase == .active {
                CloudSyncAutoSync.startRealtimeSync(
                    modelContext: modelContext,
                    activeClientManager: activeClientManager
                )
                Task {
                    try? await Task.sleep(for: .seconds(1))
                    await CloudSyncAutoSync.downloadAndMergeIfEnabled(
                        modelContext: modelContext,
                        clientId: activeClientManager.currentClientId
                    )
                }
                return
            }
            CloudSyncAutoSync.stopRealtimeSync()
        }
    }
}
