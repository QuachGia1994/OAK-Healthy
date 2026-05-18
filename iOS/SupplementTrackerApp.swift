import SwiftUI
import SwiftData
@preconcurrency import UserNotifications

private enum BootKeys {
    static let stage = "oakBootStage"
    static let timestampEpoch = "oakBootTimestampEpoch"
    static let uiReady = "ui_ready"
    static let uiStable = "ui_stable"
    static let exitingSafeMode = "exiting_safe_mode"
    static let bootStarted = "boot_started"
    static let containerReady = "container_ready"
}

private enum PendingImportKeys {
    static let filePath = "oakPendingImportFilePath"
    static let clientId = "oakPendingImportClientId"
    static let clientName = "oakPendingImportClientName"
    static let linkedBinId = "oakPendingImportLinkedBinId"
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
    @AppStorage("oakSafeModeEnabled") private var isSafeModeEnabled: Bool = false
    @AppStorage("oakPendingImportFilePath") private var pendingImportFilePath: String = ""
    
    var body: some View {
        if isAppLaunched, let dependencies {
            Group {
                if shouldShowSafeMode {
                    SafeModeView(activeClientManager: dependencies.activeClientManager)
                } else {
                    MainTabView(
                        selectedTab: $selectedTab,
                        activeClientManager: dependencies.activeClientManager,
                        notificationService: dependencies.notificationService
                    )
                }
            }
            .preferredColorScheme(preferredColorScheme)
            .modelContainer(dependencies.modelContainer)
            .task {
                UserDefaults.standard.set(BootKeys.uiReady, forKey: BootKeys.stage)
                UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: BootKeys.timestampEpoch)
                try? await Task.sleep(for: .seconds(3))
                UserDefaults.standard.set(BootKeys.uiStable, forKey: BootKeys.stage)
                UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: BootKeys.timestampEpoch)
            }
        } else {
            SafeBootView { container in
                dependencies = container
                isAppLaunched = true
            }
        }
    }
    
    private var shouldShowSafeMode: Bool {
        isSafeModeEnabled || !pendingImportFilePath.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
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
}

private struct SafeBootView: View {
    let onReady: (AppDependencyContainer) -> Void
    @State private var errorMessage: String?
    @State private var hasBootstrapped: Bool = false
    @State private var bootAttempt: Int = 0
    @AppStorage("oakSafeModeEnabled") private var isSafeModeEnabled: Bool = false
    
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
                if let message = errorMessage {
                    Text(message)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                    Button("Thử lại") {
                        errorMessage = nil
                        hasBootstrapped = false
                        bootAttempt += 1
                    }
                    .buttonStyle(.borderedProminent)
                    Button("Khôi phục dữ liệu (xóa)") {
                        recoverByWipingStore()
                        errorMessage = nil
                        hasBootstrapped = false
                        bootAttempt += 1
                    }
                    .buttonStyle(.bordered)
                } else {
                    ProgressView()
                }
            }
        }
        .task(id: bootAttempt) {
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
        
        let notificationService = NotificationService()
        onReady(
            AppDependencyContainer(
                modelContainer: container,
                activeClientManager: manager,
                notificationService: notificationService
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
            return nil
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
        guard !lastStage.isEmpty, lastStage != BootKeys.uiStable else { return }
        let lastEpoch = UserDefaults.standard.double(forKey: BootKeys.timestampEpoch)
        guard lastEpoch > 0 else { return }
        let elapsed = Date().timeIntervalSince1970 - lastEpoch
        guard elapsed < 600 else { return }
        isSafeModeEnabled = true
        UserDefaults.standard.removeObject(forKey: "activeClientId")
        UserDefaults.standard.removeObject(forKey: BootKeys.stage)
        UserDefaults.standard.removeObject(forKey: BootKeys.timestampEpoch)
    }
    
    @MainActor
    private func recoverByWipingStore() {
        guard let url = persistentStoreURL() else { return }
        resetPersistentStore(at: url)
        UserDefaults.standard.removeObject(forKey: "activeClientId")
        UserDefaults.standard.removeObject(forKey: BootKeys.stage)
        UserDefaults.standard.removeObject(forKey: BootKeys.timestampEpoch)
    }
    
}

private struct SafeModeView: View {
    @Environment(\.modelContext) private var modelContext
    @AppStorage("oakSafeModeEnabled") private var isSafeModeEnabled: Bool = false
    @AppStorage("isAutoSyncEnabled") private var isAutoSyncEnabled: Bool = false
    @AppStorage("oakPendingImportFilePath") private var pendingImportFilePath: String = ""
    @AppStorage("oakPendingImportClientId") private var pendingImportClientId: String = ""
    @AppStorage("oakPendingImportClientName") private var pendingImportClientName: String = ""
    @AppStorage("oakPendingImportLinkedBinId") private var pendingImportLinkedBinId: String = ""
    @State private var pendingImportMessage: String?
    @State private var isApplyingImport: Bool = false
    let activeClientManager: ActiveClientManager
    
    var body: some View {
        NavigationStack {
            List {
                Section {
                    if hasPendingImport {
                        if let pendingImportMessage {
                            Text(pendingImportMessage)
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                        if isApplyingImport {
                            ProgressView()
                        }
                        Button("Áp dụng dữ liệu đã tải") {
                            applyPendingImport()
                        }
                        .disabled(isApplyingImport)
                        .buttonStyle(.borderedProminent)
                        Button("Hủy dữ liệu đã tải") {
                            discardPendingImport()
                        }
                        .disabled(isApplyingImport)
                        .buttonStyle(.bordered)
                    }
                    Toggle("Tự động đồng bộ", isOn: $isAutoSyncEnabled)
                    Button("Thoát chế độ an toàn") {
                        UserDefaults.standard.removeObject(forKey: BootKeys.stage)
                        UserDefaults.standard.removeObject(forKey: BootKeys.timestampEpoch)
                        isSafeModeEnabled = false
                    }
                } header: {
                    Text("Chế độ an toàn")
                }
            }
            .navigationTitle("OAK Healthy")
        }
        .task {
            isAutoSyncEnabled = false
            CloudSyncAutoSync.stopRealtimeSync()
            if hasPendingImport {
                pendingImportMessage = "Đã phát hiện dữ liệu đã tải. Hãy áp dụng từ đây để tránh văng app lúc khởi động."
            }
        }
    }
    
    private var hasPendingImport: Bool {
        !pendingImportFilePath.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
    
    @MainActor
    private func applyPendingImport() {
        guard !isApplyingImport else { return }
        guard let url = pendingImportURL() else { return }
        do {
            isApplyingImport = true
            defer { isApplyingImport = false }
            let data = try Data(contentsOf: url)
            let client = try createImportClient()
            try SupplementExportCodec.importBackup(data: data, client: client, context: modelContext)
            activeClientManager.setCurrentClientId(client.id)
            let linked = pendingImportLinkedBinId.trimmingCharacters(in: .whitespacesAndNewlines)
            if !linked.isEmpty {
                UserDefaults.standard.set(linked, forKey: "cloudSyncLinkedBinId")
            }
            clearPendingImport(at: url)
            pendingImportMessage = "Áp dụng dữ liệu thành công. Hãy thoát chế độ an toàn để sử dụng app."
        } catch {
            isApplyingImport = false
            pendingImportMessage = "Áp dụng dữ liệu thất bại: \(error.localizedDescription)"
        }
    }
    
    @MainActor
    private func createImportClient() throws -> ClientProfile {
        let storedName = pendingImportClientName.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalized = storedName.lowercased()
            .trimmingCharacters(in: .whitespacesAndNewlines)
        if !normalized.isEmpty {
            let existing = try modelContext.fetch(FetchDescriptor<ClientProfile>())
            if let matched = existing.first(where: { $0.name.lowercased().trimmingCharacters(in: .whitespacesAndNewlines) == normalized }) {
                return matched
            }
        }
        
        let name = storedName.isEmpty ? "Imported Client" : storedName
        let client = ClientProfile(id: UUID(), name: name)
        modelContext.insert(client)
        try modelContext.save()
        return client
    }
    
    @MainActor
    private func discardPendingImport() {
        guard let url = pendingImportURL() else {
            clearPendingImport(at: nil)
            pendingImportMessage = nil
            return
        }
        clearPendingImport(at: url)
        pendingImportMessage = nil
    }
    
    private func pendingImportURL() -> URL? {
        let path = pendingImportFilePath.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !path.isEmpty else { return nil }
        return URL(fileURLWithPath: path)
    }
    
    private func clearPendingImport(at url: URL?) {
        if let url { try? FileManager.default.removeItem(at: url) }
        pendingImportFilePath = ""
        pendingImportClientId = ""
        pendingImportClientName = ""
        pendingImportLinkedBinId = ""
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
