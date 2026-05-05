import SwiftUI
import SwiftData
@preconcurrency import UserNotifications

@main
struct SupplementTrackerApp: App {
    @State private var selectedTab = 0
    @State private var delegate = NotificationDelegate()
    @AppStorage("appTheme") private var appTheme: String = "system"
    @State private var activeClientManager = ActiveClientManager()
    
    var body: some Scene {
        WindowGroup {
            MainTabView(selectedTab: $selectedTab, activeClientManager: activeClientManager)
                .preferredColorScheme(preferredColorScheme)
                .onAppear {
                    UNUserNotificationCenter.current().delegate = delegate
                }
                .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("OpenDashboard"))) { _ in
                    selectedTab = 0
                }
        }
        .modelContainer(for: [ClientProfile.self, UserSupplement.self, IntakeRecord.self])
    }
    
    private var preferredColorScheme: ColorScheme? {
        switch appTheme {
        case "light": return .light
        case "dark": return .dark
        default: return nil
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

struct MainTabView: View {
    @Binding var selectedTab: Int
    let activeClientManager: ActiveClientManager
    
    var body: some View {
        TabView(selection: $selectedTab) {
            HomeView(activeClientManager: activeClientManager)
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
