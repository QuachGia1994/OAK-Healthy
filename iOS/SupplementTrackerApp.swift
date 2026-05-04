import SwiftUI
import SwiftData

@main
struct SupplementTrackerApp: App {
    var body: some Scene {
        WindowGroup {
            MainTabView()
        }
        .modelContainer(for: [UserSupplement.self, IntakeRecord.self])
    }
}

struct MainTabView: View {
    var body: some View {
        TabView {
            HomeView()
                .tabItem {
                    Label("Trang chủ", systemImage: "house.fill")
                }
            
            HistoryView()
                .tabItem {
                    Label("Lịch sử", systemImage: "clock.fill")
                }
        }
    }
}
