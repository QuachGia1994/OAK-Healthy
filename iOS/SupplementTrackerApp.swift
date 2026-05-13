import SwiftUI
import SwiftData
@preconcurrency import UserNotifications

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
    
    private func bootstrap() async {
        try? await Task.sleep(for: .seconds(2))
        let schema = Schema([ClientProfile.self, UserSupplement.self, IntakeRecord.self])
        guard let container = try? ModelContainer(for: schema) else {
            errorMessage = "Không thể khởi tạo dữ liệu. Vui lòng cài lại app."
            return
        }
        
        let manager = ActiveClientManager()
        manager.loadFromStorage()
        
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
            guard newPhase == .active else { return }
            Task {
                try? await Task.sleep(for: .seconds(1))
                await CloudSyncAutoSync.downloadAndMergeIfEnabled(
                    modelContext: modelContext,
                    clientId: activeClientManager.currentClientId
                )
            }
        }
    }
}
