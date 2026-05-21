import SwiftUI
import SwiftData
@preconcurrency import UserNotifications

public struct OnboardingView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase
    @Query(sort: \ClientProfile.createdAt) private var clients: [ClientProfile]
    
    public let activeClientManager: ActiveClientManager
    public let notificationService: NotificationService
    
    @AppStorage("isNotificationEnabledByUser") private var isNotificationEnabledByUser: Bool = false
    @AppStorage("hasCompletedOnboarding") private var hasCompletedOnboarding: Bool = false
    @AppStorage("oakLastTestNotificationSentEpochMs") private var lastTestSentEpochMs: Int = 0
    @AppStorage("oakLastTestNotificationAckEpochMs") private var lastTestAckEpochMs: Int = 0
    
    @State private var step: OnboardingStep = .client
    @State private var isShowingAddClient: Bool = false
    @State private var permissionMessage: String?
    @State private var clientMessage: String?
    @State private var isRequestingPermission: Bool = false
    @State private var authorizationStatus: UNAuthorizationStatus = .notDetermined
    
    public init(activeClientManager: ActiveClientManager, notificationService: NotificationService) {
        self.activeClientManager = activeClientManager
        self.notificationService = notificationService
    }
    
    public var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                switch step {
                case .client:
                    clientStep
                case .notifications:
                    notificationsStep
                case .done:
                    doneStep
                }
                Spacer(minLength: 0)
                footer
            }
            .padding(16)
            .navigationTitle("onboarding_title".localized)
            .navigationBarTitleDisplayMode(.inline)
            .sheet(isPresented: $isShowingAddClient) {
                AddClientSheet { name in
                    let created = ClientProfile(name: name)
                    modelContext.insert(created)
                    do {
                        try modelContext.save()
                        clientMessage = nil
                        activeClientManager.setCurrentClientId(created.id)
                    } catch {
                        modelContext.delete(created)
                        clientMessage = error.localizedDescription
                    }
                }
            }
        }
        .interactiveDismissDisabled()
        .onChange(of: scenePhase) { _, newValue in
            guard newValue == .active else { return }
            Task { await refreshAuthorizationState() }
        }
        .task {
            guard activeClientManager.currentClientId == nil else { return }
            guard let first = clients.first else { return }
            activeClientManager.setCurrentClientId(first.id)
        }
        .task {
            await refreshAuthorizationState()
        }
    }
    
    private var clientStep: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("onboarding_step_client_title".localized)
                .font(.title2)
                .fontWeight(.semibold)
            
            Text("onboarding_step_client_body".localized)
                .foregroundStyle(.secondary)
            
            if clients.isEmpty {
                Button("add_client".localized) {
                    isShowingAddClient = true
                }
                .buttonStyle(.borderedProminent)
            } else {
                List {
                    ForEach(clients) { client in
                        Button {
                            activeClientManager.setCurrentClientId(client.id)
                        } label: {
                            HStack {
                                Text(client.name)
                                Spacer()
                                if client.id == activeClientManager.currentClientId {
                                    Image(systemName: "checkmark.circle.fill")
                                        .foregroundStyle(.green)
                                }
                            }
                        }
                    }
                }
                .listStyle(.plain)
                .frame(maxHeight: 260)
                
                Button("add_client".localized) {
                    isShowingAddClient = true
                }
                .buttonStyle(.bordered)
            }
            
            if let clientMessage {
                Text(clientMessage)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
    }
    
    private var notificationsStep: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("onboarding_step_notifications_title".localized)
                .font(.title2)
                .fontWeight(.semibold)
            
            Text("onboarding_step_notifications_body".localized)
                .foregroundStyle(.secondary)
            
            HStack {
                Text("onboarding_permission_status".localized)
                    .foregroundStyle(.secondary)
                Spacer()
                Text(permissionLabel)
                    .foregroundStyle(.secondary)
            }
            
            Toggle(
                "notification_permission_toggle".localized,
                isOn: Binding(
                    get: { isNotificationEnabledByUser },
                    set: { newValue in
                        guard authorizationStatus != .denied else {
                            isNotificationEnabledByUser = false
                            permissionMessage = "onboarding_notifications_denied".localized
                            return
                        }
                        isNotificationEnabledByUser = newValue
                    }
                )
            )
                .onChange(of: isNotificationEnabledByUser) { _, newValue in
                    guard newValue else {
                        permissionMessage = nil
                        Task { await notificationService.clearAllPendingNotifications() }
                        return
                    }
                    Task { await requestNotificationsIfNeeded() }
                }
            
            if isSystemNotificationGranted, isNotificationEnabledByUser {
                Button("onboarding_send_test_notification".localized) {
                    Task { await sendTestNotification() }
                }
                .buttonStyle(.borderedProminent)
                
                if lastTestSentEpochMs > 0 {
                    Text(String(format: "onboarding_test_sent_format".localized, formatEpochMs(lastTestSentEpochMs)))
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                if lastTestAckEpochMs > 0 {
                    Text(String(format: "onboarding_test_ack_format".localized, formatEpochMs(lastTestAckEpochMs)))
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                
                Button("onboarding_reschedule_now".localized) {
                    Task { await scheduleNotificationsForActiveClient() }
                }
                .buttonStyle(.bordered)
            }
            
            if let permissionMessage {
                Text(permissionMessage)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Button("onboarding_open_settings".localized) {
                    openAppSettings()
                }
                .buttonStyle(.bordered)
            }
        }
    }
    
    private var doneStep: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("onboarding_step_done_title".localized)
                .font(.title2)
                .fontWeight(.semibold)
            Text("onboarding_step_done_body".localized)
                .foregroundStyle(.secondary)
        }
    }
    
    private var footer: some View {
        HStack {
            if step != .client {
                Button("back".localized) {
                    step = step.previous
                }
                .buttonStyle(.bordered)
            }
            Spacer()
            Button(step.primaryButtonTitle) {
                handlePrimary()
            }
            .buttonStyle(.borderedProminent)
            .disabled(step == .client && activeClientManager.currentClientId == nil)
        }
    }
    
    @MainActor
    private func handlePrimary() {
        switch step {
        case .client:
            step = .notifications
        case .notifications:
            step = .done
        case .done:
            hasCompletedOnboarding = true
            dismiss()
        }
    }
    
    @MainActor
    private func requestNotificationsIfNeeded() async {
        guard !isRequestingPermission else { return }
        guard activeClientManager.currentClientId != nil else { return }
        isRequestingPermission = true
        defer { isRequestingPermission = false }
        
        do {
            try await notificationService.requestAuthorization()
        } catch {
            isNotificationEnabledByUser = false
            permissionMessage = "onboarding_notifications_denied".localized
            await refreshAuthorizationState()
            return
        }
        permissionMessage = nil
        await refreshAuthorizationState()
        await scheduleNotificationsForActiveClient()
    }
    
    @MainActor
    private func scheduleNotificationsForActiveClient() async {
        guard let clientId = activeClientManager.currentClientId else { return }
        do {
            let descriptor = FetchDescriptor<UserSupplement>(
                predicate: #Predicate { $0.deletedAtEpochMs == nil && $0.client?.id == clientId },
                sortBy: [SortDescriptor(\UserSupplement.name)]
            )
            let supplements = try modelContext.fetch(descriptor)
            await notificationService.replaceAllSchedules(supplements: supplements)
        } catch {
            return
        }
    }
    
    private func openAppSettings() {
        guard let url = URL(string: "app-settings:") else { return }
        openURL(url)
    }
    
    private func refreshAuthorizationState() async {
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()
        await MainActor.run {
            authorizationStatus = settings.authorizationStatus
            if authorizationStatus == .denied { isNotificationEnabledByUser = false }
            let ack = readEpochMs(forKey: "oakLastTestNotificationAckEpochMs")
            if ack != lastTestAckEpochMs { lastTestAckEpochMs = ack }
        }
    }
    
    private func sendTestNotification() async {
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()
        let authorized = settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional
        guard authorized else {
            await MainActor.run {
                isNotificationEnabledByUser = false
                permissionMessage = "onboarding_notifications_denied".localized
            }
            await refreshAuthorizationState()
            return
        }
        
        let now = Int(Date().timeIntervalSince1970 * 1000)
        await MainActor.run { lastTestSentEpochMs = now }
        
        let content = UNMutableNotificationContent()
        content.title = "OAK Healthy"
        content.body = "onboarding_test_notification_body".localized
        content.sound = .default
        content.userInfo = ["oakTestNotification": true]
        
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 3, repeats: false)
        let request = UNNotificationRequest(identifier: "OAK_TEST_\(now)", content: content, trigger: trigger)
        do {
            try await center.add(request)
        } catch {
            await MainActor.run { permissionMessage = error.localizedDescription }
        }
    }
    
    private func formatEpochMs(_ epochMs: Int) -> String {
        guard epochMs > 0 else { return "" }
        let date = Date(timeIntervalSince1970: TimeInterval(epochMs) / 1000)
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm"
        return formatter.string(from: date)
    }

    private func readEpochMs(forKey key: String) -> Int {
        guard let value = UserDefaults.standard.object(forKey: key) else { return 0 }
        if let number = value as? NSNumber { return number.intValue }
        if let int = value as? Int { return int }
        if let int64 = value as? Int64 { return Int(int64) }
        if let double = value as? Double { return Int(double) }
        return 0
    }
    
    private var isSystemNotificationGranted: Bool {
        authorizationStatus == .authorized || authorizationStatus == .provisional
    }
    
    private var permissionLabel: String {
        if isSystemNotificationGranted { return "onboarding_permission_granted".localized }
        if authorizationStatus == .denied { return "onboarding_permission_denied".localized }
        return "onboarding_permission_not_determined".localized
    }
}

private enum OnboardingStep: String, Sendable {
    case client
    case notifications
    case done
    
    var previous: OnboardingStep {
        switch self {
        case .client: return .client
        case .notifications: return .client
        case .done: return .notifications
        }
    }
    
    var primaryButtonTitle: String {
        switch self {
        case .done: return "onboarding_done".localized
        default: return "onboarding_next".localized
        }
    }
}

private struct AddClientSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var name: String = ""
    let onSave: (String) -> Void
    
    var body: some View {
        NavigationStack {
            Form {
                TextField("client_name_label".localized, text: $name)
            }
            .navigationTitle("add_client".localized)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("cancel".localized) { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("save".localized) {
                        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
                        guard !trimmed.isEmpty else { return }
                        onSave(trimmed)
                        dismiss()
                    }
                    .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }
}
