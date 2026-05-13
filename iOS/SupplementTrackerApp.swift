import SwiftUI
import SwiftData
@preconcurrency import UserNotifications

@main
struct SupplementTrackerApp: App {
    @State private var selectedTab = 0
    @AppStorage("appTheme") private var appTheme: String = "system"
    @State private var activeClientManager: ActiveClientManager?
    @State private var notificationDelegate: NotificationDelegate?
    @State private var notificationService: NotificationService?
    @State private var modelContainer: ModelContainer?
    @State private var bootstrapErrorMessage: String?
    @State private var isAppReady: Bool = false
    
    var body: some Scene {
        WindowGroup {
            mainContentView
        }
    }
    
    private var preferredColorScheme: ColorScheme? {
        switch appTheme {
        case "light": return .light
        case "dark": return .dark
        default: return nil
        }
    }
    
    @ViewBuilder
    private var mainContentView: some View {
        if let modelContainer, let activeClientManager, let notificationService {
            MainTabContainerView(
                selectedTab: $selectedTab,
                preferredColorScheme: preferredColorScheme,
                modelContainer: modelContainer,
                activeClientManager: activeClientManager,
                notificationService: notificationService,
                isAppReady: isAppReady,
                handleScenePhaseChange: handleScenePhaseChange(_:)
            )
        } else if let message = bootstrapErrorMessage {
            BootstrapErrorView(message: message) {
                bootstrapErrorMessage = nil
            } bootstrap: {
                await bootstrapAppServices()
            }
        } else {
            BootstrapLoadingView {
                await bootstrapAppServices()
            }
        }
    }
    
    private func bootstrapAppServices() async {
        guard bootstrapErrorMessage == nil else { return }
        guard activeClientManager == nil || modelContainer == nil else { return }
        try? await Task.sleep(for: .seconds(1.5))
        if modelContainer == nil {
            modelContainer = await makeModelContainer()
            if modelContainer == nil {
                bootstrapErrorMessage = "Vui lòng cài lại app hoặc xoá dữ liệu app và thử lại."
                return
            }
        }
        if activeClientManager == nil {
            activeClientManager = ActiveClientManager()
        }
        if notificationDelegate == nil {
            let delegate = NotificationDelegate()
            notificationDelegate = delegate
            UNUserNotificationCenter.current().delegate = delegate
        }
        if notificationService == nil {
            notificationService = NotificationService()
        }
        isAppReady = true
    }
    
    private func makeModelContainer() async -> ModelContainer? {
        let schema = Schema([ClientProfile.self, UserSupplement.self, IntakeRecord.self])
        if let container = try? ModelContainer(for: schema) { return container }
        let config = ModelConfiguration(isStoredInMemoryOnly: true)
        return try? ModelContainer(for: schema, configurations: config)
    }
    
    private func handleScenePhaseChange(_ phase: ScenePhase) {
        guard isAppReady else { return }
        guard phase == .active else { return }
        NotificationCenter.default.post(name: .oakAppBecameActive, object: nil)
    }
}

private struct BootstrapLoadingView: View {
    let bootstrap: () async -> Void
    
    var body: some View {
        Color.clear
            .task { await bootstrap() }
    }
}

private struct BootstrapErrorView: View {
    let message: String
    let retry: () -> Void
    let bootstrap: () async -> Void
    
    var body: some View {
        VStack(spacing: 12) {
            Text("Không thể khởi tạo dữ liệu cục bộ.")
                .font(.headline)
            Text(message)
                .font(.footnote)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
            Button("Thử lại") { retry() }
        }
        .padding(24)
        .task { await bootstrap() }
    }
}

private struct MainTabContainerView: View {
    @Environment(\.scenePhase) private var scenePhase
    @Binding var selectedTab: Int
    let preferredColorScheme: ColorScheme?
    let modelContainer: ModelContainer
    let activeClientManager: ActiveClientManager
    let notificationService: NotificationService
    let isAppReady: Bool
    let handleScenePhaseChange: (ScenePhase) -> Void
    
    var body: some View {
        MainTabView(
            selectedTab: $selectedTab,
            activeClientManager: activeClientManager,
            isAppReady: isAppReady,
            notificationService: notificationService
        )
            .preferredColorScheme(preferredColorScheme)
            .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("OpenDashboard"))) { _ in
                selectedTab = 0
            }
            .onChange(of: scenePhase) { _, newPhase in
                handleScenePhaseChange(newPhase)
            }
            .modelContainer(modelContainer)
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

struct MainTabView: View {
    @Binding var selectedTab: Int
    let activeClientManager: ActiveClientManager
    let isAppReady: Bool
    let notificationService: NotificationService
    
    var body: some View {
        TabView(selection: $selectedTab) {
            HomeView(
                activeClientManager: activeClientManager,
                isAppReady: isAppReady,
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
    }
}

extension Notification.Name {
    static let oakAppBecameActive = Notification.Name("oakAppBecameActive")
}
