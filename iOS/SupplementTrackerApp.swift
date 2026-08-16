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

// #region debug-point ios-tab-crash-reporter
enum DebugReporter {
    private static let urlKey = "debugServerUrl"
    private static let runIdKey = "debugRunId"
    private static let allowedHosts: Set<String> = ["localhost", "127.0.0.1"]
    private static let allowedFieldKeys: Set<String> = [
        "safeMode", "pendingImport", "hasPendingImport", "lastStage", "elapsed",
        "count", "status_code", "error_type", "phase"
    ]

    static func report(_ name: String, fields: [String: String] = [:]) {
        let rawUrl = (UserDefaults.standard.string(forKey: urlKey) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !rawUrl.isEmpty, let url = URL(string: rawUrl),
              let host = url.host, allowedHosts.contains(host) else { return }
        let runId = (UserDefaults.standard.string(forKey: runIdKey) ?? "pre").trimmingCharacters(in: .whitespacesAndNewlines)
        
        var payload: [String: Any] = [
            "ts": Int64(Date().timeIntervalSince1970 * 1000),
            "sessionId": "ios-tab-crash",
            "runId": runId,
            "name": name
        ]
        let safeFields = fields.filter { allowedFieldKeys.contains($0.key) }
        if !safeFields.isEmpty { payload["fields"] = safeFields }
        guard let data = try? JSONSerialization.data(withJSONObject: payload, options: []) else { return }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = data
        
        Task {
            _ = try? await URLSession.shared.data(for: request)
        }
    }
}
// #endregion debug-point ios-tab-crash-reporter

@main
struct SupplementTrackerApp: App {
    @AppStorage("appTheme") private var appTheme: String = "system"
    @State private var selectedTab: Int = 0
    @State private var isAppLaunched: Bool = false
    @State private var dependencies: AppDependencyContainer?
    
    init() {
        FirebaseBootstrap.configureIfNeeded()
        DiagnosticsReporter.applyStoredConsent()
        UNUserNotificationCenter.current().delegate = NotificationDelegate.shared
        Task { @MainActor in
            NotificationService.shared.registerNotificationActions()
        }
    }
    
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
    @Environment(\.scenePhase) private var scenePhase
    @Binding var selectedTab: Int
    let preferredColorScheme: ColorScheme?
    @Binding var isAppLaunched: Bool
    @Binding var dependencies: AppDependencyContainer?
    @AppStorage("oakSafeModeEnabled") private var isSafeModeEnabled: Bool = false
    @AppStorage("oakPendingImportFilePath") private var pendingImportFilePath: String = ""
    @State private var integrity: AppIntegrityVerdict = AppIntegrity.evaluate()
    
    var body: some View {
        Group {
            if isAppLaunched, let dependencies {
                Group {
                    if !integrity.ok {
                        IntegrityBlockedView()
                    } else if shouldShowSafeMode {
                        SafeModeView(activeClientManager: dependencies.activeClientManager)
                    } else {
                        MainTabView(
                            selectedTab: $selectedTab,
                            activeClientManager: dependencies.activeClientManager,
                            notificationService: dependencies.notificationService
                        )
                    }
                }
                .modelContainer(dependencies.modelContainer)
                .environment(dependencies.entitlementManager)
                .environment(dependencies.storeKitBillingService)
                .task {
                    await dependencies.storeKitBillingService.start()
                }
                .onChange(of: scenePhase) { _, phase in
                    guard phase == .active else { return }
                    Task { await dependencies.storeKitBillingService.refresh() }
                }
                .onDisappear {
                    dependencies.storeKitBillingService.stop()
                }
                .task {
                    DebugReporter.report("ui_task_start", fields: [
                        "safeMode": String(isSafeModeEnabled),
                        "pendingImport": String(!pendingImportFilePath.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    ])
                    UserDefaults.standard.set(BootKeys.uiReady, forKey: BootKeys.stage)
                    UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: BootKeys.timestampEpoch)
                    do {
                        try await Task.sleep(for: .seconds(3))
                    } catch {
                        return
                    }
                    UserDefaults.standard.set(BootKeys.uiStable, forKey: BootKeys.stage)
                    UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: BootKeys.timestampEpoch)
                    DebugReporter.report("ui_task_stable")
                }
            } else {
                SafeBootView { container in
                    dependencies = container
                    isAppLaunched = true
                }
            }
        }
        .preferredColorScheme(preferredColorScheme)
    }
    
    private var shouldShowSafeMode: Bool {
        isSafeModeEnabled || !pendingImportFilePath.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}

@preconcurrency
final class NotificationDelegate: NSObject, UNUserNotificationCenterDelegate, @unchecked Sendable {
    static let shared = NotificationDelegate()
    
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse, withCompletionHandler completionHandler: @escaping () -> Void) {
        let info = response.notification.request.content.userInfo
        if (info["oakTestNotification"] as? Bool) == true {
            UserDefaults.standard.set(Int(Date().timeIntervalSince1970 * 1000), forKey: "oakLastTestNotificationAckEpochMs")
        }
        if let action = NotificationDoseAction(response: response) {
            action.savePending()
            Task { @MainActor in
                NotificationCenter.default.post(name: NSNotification.Name("OAKDoseAction"), object: action)
            }
            completionHandler()
            return
        }
        Task { @MainActor in
            NotificationCenter.default.post(name: NSNotification.Name("OpenDashboard"), object: nil)
        }
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
    let entitlementManager: EntitlementManager
    let storeKitBillingService: StoreKitBillingService
}

private struct IntegrityBlockedView: View {
    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text("integrity_blocked_body".localized)
                        .font(.body)
                        .foregroundStyle(.secondary)
                } header: {
                    Text("integrity_blocked_title".localized)
                }
            }
            .navigationTitle("integrity_blocked_title".localized)
        }
    }
}

private struct SafeBootView: View {
    let onReady: (AppDependencyContainer) -> Void
    @Environment(\.colorScheme) private var colorScheme
    @State private var errorMessage: String?
    @State private var hasBootstrapped: Bool = false
    @State private var bootAttempt: Int = 0
    @AppStorage("oakSafeModeEnabled") private var isSafeModeEnabled: Bool = false
    
    var body: some View {
        ZStack {
            LinearGradient(
                colors: colorScheme == .dark
                    ? [Color(red: 0.03, green: 0.12, blue: 0.14), Color(red: 0.05, green: 0.27, blue: 0.26)]
                    : [Color(red: 0.94, green: 0.99, blue: 0.97), Color(red: 0.86, green: 0.95, blue: 0.92)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            
            VStack(spacing: 18) {
                OAKLoadingLogoView()
                    .frame(height: 260)
                if let message = errorMessage {
                    Text(message)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                    Button("safe_boot_retry".localized) {
                        errorMessage = nil
                        hasBootstrapped = false
                        bootAttempt += 1
                    }
                    .buttonStyle(.borderedProminent)
                    Button("safe_boot_wipe_data".localized) {
                        recoverByWipingStore()
                        errorMessage = nil
                        hasBootstrapped = false
                        bootAttempt += 1
                    }
                    .buttonStyle(.bordered)
                } else {
                    ProgressView()
                        .tint(colorScheme == .dark ? .white : .black)
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
        let minSplashSeconds = 1.35
        let splashStartedAt = Date()
        attemptCrashRecoveryIfNeeded()
        DebugReporter.report("bootstrap_start")
        UserDefaults.standard.set(BootKeys.bootStarted, forKey: BootKeys.stage)
        UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: BootKeys.timestampEpoch)
        let schema = Schema(versionedSchema: OAKSchemaV1.self)
        guard let container = makeModelContainer(schema: schema) else {
            errorMessage = "bootstrap_init_failed_message".localized
            DebugReporter.report("bootstrap_container_failed")
            return
        }
        UserDefaults.standard.set(BootKeys.containerReady, forKey: BootKeys.stage)
        UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: BootKeys.timestampEpoch)
        DebugReporter.report("bootstrap_container_ready")
        
        let manager = ActiveClientManager()
        manager.loadFromStorage()
        validateActiveClient(manager: manager, container: container)
        DebugReporter.report("bootstrap_active_client_loaded", fields: [
            "currentClientId": manager.currentClientId?.uuidString ?? ""
        ])
        
        let notificationService = NotificationService.shared
        UNUserNotificationCenter.current().delegate = NotificationDelegate.shared
        notificationService.registerNotificationActions()
        let elapsed = Date().timeIntervalSince(splashStartedAt)
        if elapsed < minSplashSeconds {
            let remainingNs = UInt64((minSplashSeconds - elapsed) * 1_000_000_000)
            try? await Task.sleep(nanoseconds: remainingNs)
        }
        let entitlementManager = EntitlementManager()
        let storeKitBillingService = StoreKitBillingService(entitlementManager: entitlementManager)
        onReady(
            AppDependencyContainer(
                modelContainer: container,
                activeClientManager: manager,
                notificationService: notificationService,
                entitlementManager: entitlementManager,
                storeKitBillingService: storeKitBillingService
            )
        )
        Task { @MainActor in
            guard UserDefaults.standard.bool(forKey: "isNotificationEnabledByUser") else { return }
            await notificationService.rebuildShadowFromPendingRequests()
        }
        DebugReporter.report("bootstrap_ready")
    }
    
    @MainActor
    private func makeModelContainer(schema: Schema) -> ModelContainer? {
        guard let storeURL = persistentStoreURL() else {
            return try? ModelContainer(for: schema, migrationPlan: OAKSchemaMigrationPlan.self)
        }
        let configuration = ModelConfiguration(schema: schema, url: storeURL)
        
        do {
            return try ModelContainer(
                for: schema,
                migrationPlan: OAKSchemaMigrationPlan.self,
                configurations: [configuration]
            )
        } catch {
            DebugReporter.report("swiftdata_init_failed", fields: [
                "storeURL": storeURL.path,
                "error": String(describing: error)
            ])
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
            DebugReporter.report("appsupport_dir_failed", fields: [
                "error": String(describing: error)
            ])
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
        guard lastStage == BootKeys.bootStarted || lastStage == BootKeys.containerReady || lastStage == BootKeys.uiReady else { return }
        let lastEpoch = UserDefaults.standard.double(forKey: BootKeys.timestampEpoch)
        guard lastEpoch > 0 else { return }
        let elapsed = Date().timeIntervalSince1970 - lastEpoch
        guard elapsed < 600 else { return }
        DebugReporter.report("crash_recovery_triggered", fields: [
            "lastStage": lastStage,
            "elapsed": String(elapsed)
        ])
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
    @Environment(EntitlementManager.self) private var entitlementManager
    @AppStorage("oakSafeModeEnabled") private var isSafeModeEnabled: Bool = false
    @AppStorage("isAutoSyncEnabled") private var isAutoSyncEnabled: Bool = false
    @AppStorage("oakPendingImportFilePath") private var pendingImportFilePath: String = ""
    @AppStorage("oakPendingImportClientId") private var pendingImportClientId: String = ""
    @AppStorage("oakPendingImportClientName") private var pendingImportClientName: String = ""
    @AppStorage("oakPendingImportLinkedBinId") private var pendingImportLinkedBinId: String = ""
    @AppStorage("debugServerUrl") private var debugServerUrl: String = ""
    @State private var pendingImportMessage: String?
    @State private var pendingImportPreview: OAKBackupPreview?
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
                        Button(
                            pendingImportPreview == nil
                                ? "safe_mode_preview_button".localized
                                : "safe_mode_confirm_apply_button".localized
                        ) {
                            Task {
                                if pendingImportPreview == nil {
                                    await previewPendingImport()
                                } else {
                                    await applyPendingImport()
                                }
                            }
                        }
                        .disabled(isApplyingImport || pendingImportPreview?.canRestore == false)
                        .buttonStyle(.borderedProminent)
                        Button("safe_mode_discard_button".localized) {
                            discardPendingImport()
                        }
                        .disabled(isApplyingImport)
                        .buttonStyle(.bordered)
                    }
                    Toggle("safe_mode_auto_sync_toggle".localized, isOn: $isAutoSyncEnabled)
                    Button("safe_mode_exit_button".localized) {
                        DebugReporter.report("safe_mode_exit_tap", fields: [
                            "pendingImport": String(hasPendingImport)
                        ])
                        UserDefaults.standard.removeObject(forKey: BootKeys.stage)
                        UserDefaults.standard.removeObject(forKey: BootKeys.timestampEpoch)
                        isSafeModeEnabled = false
                    }
                } header: {
                    Text("safe_mode_header".localized)
                }
                
                Section {
                    TextField("debug_server_url_placeholder".localized, text: $debugServerUrl)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .textFieldStyle(.roundedBorder)
                    Button("debug_send_test_event".localized) {
                        DebugReporter.report("debug_test_event")
                    }
                } header: {
                    Text("debug_section_title".localized)
                }
            }
            .navigationTitle("safe_mode_title".localized)
        }
        .task {
            isAutoSyncEnabled = false
            CloudSyncAutoSync.stopRealtimeSync()
            if hasPendingImport {
                pendingImportMessage = "safe_mode_detected_message".localized
            }
            DebugReporter.report("safe_mode_view_task", fields: [
                "hasPendingImport": String(hasPendingImport)
            ])
        }
    }
    
    private var hasPendingImport: Bool {
        !pendingImportFilePath.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
    
    @MainActor
    private func previewPendingImport() async {
        guard !isApplyingImport, let url = pendingImportURL() else { return }
        do {
            isApplyingImport = true
            defer { isApplyingImport = false }
            let data = try await Task.detached(priority: .userInitiated) { try Data(contentsOf: url) }.value
            let preview = try SupplementExportCodec.previewBackup(data)
            pendingImportPreview = preview
            pendingImportMessage = previewMessage(preview)
            DebugReporter.report("safe_mode_preview_complete", fields: [
                "canRestore": String(preview.canRestore),
                "supplementCount": String(preview.supplementCount),
                "historyCount": String(preview.historyCount)
            ])
        } catch {
            pendingImportPreview = nil
            pendingImportMessage = String(format: "safe_mode_apply_failed_format".localized, error.localizedDescription)
        }
    }

    @MainActor
    private func applyPendingImport() async {
        guard !isApplyingImport, let approved = pendingImportPreview, approved.canRestore else { return }
        guard let url = pendingImportURL() else { return }
        DebugReporter.report("safe_mode_apply_start")
        var createdClient: ClientProfile?
        do {
            isApplyingImport = true
            defer { isApplyingImport = false }
            let data = try await Task.detached(priority: .userInitiated) { try Data(contentsOf: url) }.value
            let current = try SupplementExportCodec.previewBackup(data)
            guard current == approved else {
                pendingImportPreview = current
                pendingImportMessage = "safe_mode_preview_changed".localized
                return
            }
            let resolution = try createImportClient()
            let client = resolution.client
            createdClient = resolution.created ? client : nil
            try SupplementExportCodec.importBackup(data: data, client: client, context: modelContext)
            activeClientManager.setCurrentClientId(client.id)
            applyPendingImportLink(clientId: client.id)
            if UserDefaults.standard.bool(forKey: "isNotificationEnabledByUser"),
               !(await rescheduleImportedNotifications(clientId: client.id)) {
                return
            }
            clearPendingImport(at: url)
            pendingImportPreview = nil
            pendingImportMessage = "safe_mode_apply_success_message".localized
            DebugReporter.report("safe_mode_apply_success")
        } catch {
            if let createdClient {
                modelContext.delete(createdClient)
                try? modelContext.save()
            }
            pendingImportMessage = String(format: "safe_mode_apply_failed_format".localized, error.localizedDescription)
            DebugReporter.report("safe_mode_apply_failed")
        }
    }

    private func previewMessage(_ preview: OAKBackupPreview) -> String {
        String(
            format: "safe_mode_preview_format".localized,
            preview.sourceSchema,
            preview.supplementCount,
            preview.historyCount,
            preview.duplicateSupplementIdCount,
            preview.duplicateHistoryCount,
            preview.orphanHistoryCount
        )
    }
    
    private func applyPendingImportLink(clientId: UUID) {
        let linked = pendingImportLinkedBinId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !linked.isEmpty else { return }
        CloudSyncProfileStore().setLinkedBinId(linked, clientId: clientId)
    }

    @MainActor
    private func rescheduleImportedNotifications(clientId: UUID) async -> Bool {
        do {
            try await NotificationService.shared.requestAuthorization()
            let supplements = try ClientScopedStore.activeSupplements(
                modelContext: modelContext,
                clientId: clientId
            )
            await NotificationService.shared.replaceAllSchedules(supplements: supplements)
            return true
        } catch {
            return false
        }
    }

    @MainActor
    private func createImportClient() throws -> (client: ClientProfile, created: Bool) {
        let storedName = pendingImportClientName.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalized = storedName.lowercased()
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let existing = try modelContext.fetch(FetchDescriptor<ClientProfile>())
        if !normalized.isEmpty,
           let matched = existing.first(where: {
               $0.name.lowercased().trimmingCharacters(in: .whitespacesAndNewlines) == normalized
           }) {
            return (matched, false)
        }
        if let limit = entitlementManager.maxClients, existing.count >= limit {
            throw SafeModeAccessError.clientLimitReached
        }
        let name = storedName.isEmpty ? "imported_client_default_name".localized : storedName
        let client = ClientProfile(id: UUID(), name: name)
        modelContext.insert(client)
        try modelContext.save()
        return (client, true)
    }
    
    @MainActor
    private func discardPendingImport() {
        guard let url = pendingImportURL() else {
            clearPendingImport(at: nil)
            pendingImportMessage = nil
            pendingImportPreview = nil
            return
        }
        clearPendingImport(at: url)
        pendingImportMessage = nil
        pendingImportPreview = nil
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

private enum SafeModeAccessError: LocalizedError {
    case clientLimitReached

    var errorDescription: String? {
        "plan_client_limit_reached".localized
    }
}

struct MainTabView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.scenePhase) private var scenePhase
    @Environment(EntitlementManager.self) private var entitlementManager
    @Query(sort: \ClientProfile.createdAt) private var clients: [ClientProfile]
    @Binding var selectedTab: Int
    let activeClientManager: ActiveClientManager
    let notificationService: NotificationService

    @AppStorage("isNotificationEnabledByUser") private var isNotificationEnabledByUser: Bool = false
    @AppStorage("hasCompletedOnboarding") private var hasCompletedOnboarding: Bool = false
    @AppStorage("oakHomeOverdueCount") private var homeOverdueCount: Int = 0
    @AppStorage("oakLastSyncEpochMs") private var lastSyncEpochMs: Double = 0
    @State private var badgeViewModel = HomeViewModel()

    var body: some View {
        TabView(selection: $selectedTab) {
            HomeView(
                activeClientManager: activeClientManager,
                notificationService: notificationService
            )
            .id("home-\(activeClientManager.currentClientId?.uuidString ?? "none")")
            .tabItem {
                Label("tab_home".localized, systemImage: "house.fill")
            }
            .tag(0)
            .badge(homeOverdueCount > 0 ? homeOverdueCount : 0)

            StackView(
                activeClientManager: activeClientManager,
                notificationService: notificationService
            )
            .id("stack-\(activeClientManager.currentClientId?.uuidString ?? "none")")
            .tabItem {
                Label("tab_stack".localized, systemImage: "square.stack.3d.up.fill")
            }
            .tag(1)

            HistoryView(activeClientManager: activeClientManager)
                .id("history-\(activeClientManager.currentClientId?.uuidString ?? "none")")
                .tabItem {
                    Label("tab_history".localized, systemImage: "clock.fill")
                }
                .tag(2)
        }
        .tint(OAKPalette.accent)
        .toolbarBackground(.ultraThinMaterial, for: .tabBar)
        .toolbarBackground(.visible, for: .tabBar)
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("OpenDashboard"))) { _ in
            selectedTab = 0
        }
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("OAKDoseAction"))) { notification in
            guard let payload = notification.object as? NotificationDoseAction else { return }
            selectedTab = 0
            handleDoseAction(payload)
        }
        .task { await replayPendingDoseAction() }
        .onChange(of: scenePhase, initial: true) { _, newPhase in
            handleAutoSync(phase: newPhase)
            guard newPhase == .active else { return }
            Task { @MainActor in
                await replayPendingDoseAction()
                await refreshHomeBadgeCount()
            }
        }
        .onChange(of: activeClientManager.currentClientId, initial: false) { _, _ in
            guard !enforceClientAccess() else { return }
            handleAutoSync(phase: scenePhase)
            Task { @MainActor in
                await rescheduleNotificationsIfEnabled()
                await refreshHomeBadgeCount()
            }
        }
        .onChange(of: entitlementManager.snapshot.plan, initial: true) { _, _ in
            _ = enforceClientAccess()
            handleAutoSync(phase: scenePhase)
        }
        .onChange(of: clients.count, initial: false) { _, _ in
            _ = enforceClientAccess()
        }
        .onChange(of: lastSyncEpochMs, initial: false) { _, _ in
            Task { @MainActor in
                await refreshHomeBadgeCount()
            }
        }
        .sheet(
            isPresented: Binding(
                get: { !hasCompletedOnboarding },
                set: { _ in }
            )
        ) {
            OnboardingView(activeClientManager: activeClientManager, notificationService: notificationService)
        }
    }
    
    private func handleAutoSync(phase: ScenePhase) {
        guard entitlementManager.canUse(.encryptedCloudSync) else {
            UserDefaults.standard.set(false, forKey: "isAutoSyncEnabled")
            CloudSyncAutoSync.stopRealtimeSync()
            return
        }
        guard UserDefaults.standard.bool(forKey: "isAutoSyncEnabled") else {
            CloudSyncAutoSync.stopRealtimeSync()
            return
        }
        guard phase == .active else {
            CloudSyncAutoSync.stopRealtimeSync()
            return
        }
        CloudSyncAutoSync.startRealtimeSync(modelContext: modelContext, activeClientManager: activeClientManager)
    }

    @discardableResult
    private func enforceClientAccess() -> Bool {
        let allowed = entitlementManager.maxClients.map { Array(clients.prefix($0)) } ?? clients
        guard let currentId = activeClientManager.currentClientId else { return false }
        guard !allowed.contains(where: { $0.id == currentId }) else { return false }
        activeClientManager.setCurrentClientId(allowed.first?.id)
        return true
    }
    
    @MainActor
    private func rescheduleNotificationsIfEnabled() async {
        guard isNotificationEnabledByUser else { return }
        guard let clientId = activeClientManager.currentClientId else {
            await notificationService.clearAllPendingNotifications()
            return
        }
        do {
            let supplements = try ClientScopedStore.activeSupplements(
                modelContext: modelContext,
                clientId: clientId
            )
            await notificationService.replaceAllSchedules(supplements: supplements)
        } catch {
            DebugReporter.report("auto_reschedule_fetch_failed", fields: ["error": error.localizedDescription])
            return
        }
    }

    @MainActor
    private func refreshHomeBadgeCount() async {
        guard let clientId = activeClientManager.currentClientId else {
            homeOverdueCount = 0
            return
        }
        do {
            let supplements = try ClientScopedStore.activeSupplements(
                modelContext: modelContext,
                clientId: clientId
            )
            badgeViewModel.processSupplements(supplements)
            homeOverdueCount = badgeViewModel.cachedTodayCounts.missed
        } catch {
            homeOverdueCount = 0
            return
        }
    }
    
    private func handleDoseAction(_ payload: NotificationDoseAction) {
        Task { @MainActor in
            guard await applyDoseAction(payload) else { return }
            payload.clearPending()
            await refreshHomeBadgeCount()
        }
    }

    @MainActor
    private func replayPendingDoseAction() async {
        guard let payload = NotificationDoseAction.pending() else { return }
        selectedTab = 0
        guard await applyDoseAction(payload) else { return }
        payload.clearPending()
    }

    @MainActor
    private func applyDoseAction(_ payload: NotificationDoseAction) async -> Bool {
        guard let context = doseActionContext(payload) else { return false }
        if !hasPersistedDose(context) {
            guard persistDoseRecord(context) else { return false }
        }
        await finalizeDoseAction(
            supplement: context.supplement,
            scheduledAt: context.scheduledAt,
            intakeTime: context.intakeTime,
            requestIdentifier: payload.requestIdentifier
        )
        return true
    }

    private struct NotificationDoseContext {
        let supplement: UserSupplement
        let scheduledAt: Date
        let intakeTime: String
        let scheduledAtEpochMs: Int64
        let recordId: UUID
        let status: String
    }

    @MainActor
    private func doseActionContext(_ payload: NotificationDoseAction) -> NotificationDoseContext? {
        guard let supplement = fetchSupplement(id: payload.supplementId) else { return nil }
        guard ActiveProfileNotificationPolicy.allows(
            activeClientId: activeClientManager.currentClientId,
            supplementClientId: supplement.client?.id
        ) else {
            DebugReporter.report("dose_action_client_mismatch")
            return nil
        }
        let scheduledAt = Date(timeIntervalSince1970: TimeInterval(payload.scheduledAtEpochMs) / 1000)
        let intakeTime = TimeStrings.normalizeList(payload.intakeTime).first ?? payload.intakeTime
        let key = DoseEventKey.make(supplementId: supplement.id, scheduledAtEpochMs: payload.scheduledAtEpochMs)
        let status = payload.actionIdentifier == NotificationService.Action.skipped.rawValue
            ? IntakeStatus.skipped.rawValue
            : IntakeStatus.taken.rawValue
        return NotificationDoseContext(
            supplement: supplement,
            scheduledAt: scheduledAt,
            intakeTime: intakeTime,
            scheduledAtEpochMs: payload.scheduledAtEpochMs,
            recordId: DoseEventKey.stableUUID(from: key),
            status: status
        )
    }

    @MainActor
    private func hasPersistedDose(_ context: NotificationDoseContext) -> Bool {
        if fetchDoseRecord(id: context.recordId) != nil { return true }
        return hasRecord(
            supplement: context.supplement,
            scheduledAt: context.scheduledAt,
            intakeTime: context.intakeTime
        )
    }

    @MainActor
    private func fetchSupplement(id: UUID) -> UserSupplement? {
        let descriptor = FetchDescriptor<UserSupplement>(predicate: #Predicate { $0.id == id })
        do {
            return try modelContext.fetch(descriptor).first
        } catch {
            DebugReporter.report("dose_action_fetch_failed", fields: ["error": error.localizedDescription])
            return nil
        }
    }

    @MainActor
    private func persistDoseRecord(_ context: NotificationDoseContext) -> Bool {
        let nowEpochMs = Int64(Date().timeIntervalSince1970 * 1000)
        modelContext.insert(IntakeRecord(
            id: context.recordId,
            date: context.scheduledAt,
            status: context.status,
            intakeTime: context.intakeTime,
            updatedAtEpochMs: nowEpochMs,
            supplement: context.supplement
        ))
        updateSupplementAfterDoseAction(
            context.supplement,
            status: context.status,
            scheduledAt: context.scheduledAt,
            updatedAtEpochMs: nowEpochMs
        )
        do {
            try modelContext.save()
            return true
        } catch {
            modelContext.rollback()
            DebugReporter.report("dose_action_save_failed", fields: ["error": error.localizedDescription])
            return false
        }
    }

    private func updateSupplementAfterDoseAction(
        _ supplement: UserSupplement,
        status: String,
        scheduledAt: Date,
        updatedAtEpochMs: Int64
    ) {
        guard status == IntakeStatus.taken.rawValue else { return }
        supplement.lastTakenLocalDate = notificationDayString(scheduledAt)
        supplement.updatedAtEpochMs = updatedAtEpochMs
    }

    private func notificationDayString(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.calendar = .current
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = .current
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: date)
    }
    
    @MainActor
    private func fetchDoseRecord(id: UUID) -> IntakeRecord? {
        let descriptor = FetchDescriptor<IntakeRecord>(predicate: #Predicate { $0.id == id })
        return (try? modelContext.fetch(descriptor))?.first
    }

    @MainActor
    private func finalizeDoseAction(
        supplement: UserSupplement,
        scheduledAt: Date,
        intakeTime: String,
        requestIdentifier: String
    ) async {
        await notificationService.cancelReminder(for: supplement, timeString: intakeTime, day: scheduledAt)
        if !requestIdentifier.isEmpty {
            UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: [requestIdentifier])
        }
        CloudSyncAutoSync.requestSyncSoon(modelContext: modelContext, clientId: supplement.client?.id)
    }
    
    private func hasRecord(supplement: UserSupplement, scheduledAt: Date, intakeTime: String) -> Bool {
        let calendar = Calendar.current
        return supplement.intakeRecords.contains { record in
            guard calendar.isDate(record.date, inSameDayAs: scheduledAt) else { return false }
            if record.intakeTime.isEmpty { return true }
            let recordTime = record.intakeTime.trimmingCharacters(in: .whitespacesAndNewlines)
            let scheduledTime = intakeTime.trimmingCharacters(in: .whitespacesAndNewlines)
            if let recordMinutes = TimeStrings.parseLenientTime(recordTime), let scheduledMinutes = TimeStrings.parseLenientTime(scheduledTime) {
                return TimeStrings.formatTime(recordMinutes) == TimeStrings.formatTime(scheduledMinutes)
            }
            return recordTime == scheduledTime
        }
    }
}
