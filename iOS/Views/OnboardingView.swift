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
            VStack(spacing: 14) {
                OnboardingHero(step: step)
                ScrollView {
                    VStack(spacing: 14) {
                        switch step {
                        case .client:
                            clientStep
                        case .notifications:
                            notificationsStep
                        case .done:
                            doneStep
                        }
                    }
                    .padding(.vertical, 2)
                }
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
                VStack(spacing: 0) {
                    ForEach(clients) { client in
                        Button {
                            activeClientManager.setCurrentClientId(client.id)
                        } label: {
                            HStack(spacing: 10) {
                                Text(client.name)
                                    .font(.headline)
                                Spacer()
                                if client.id == activeClientManager.currentClientId {
                                    Image(systemName: "checkmark.circle.fill")
                                        .foregroundStyle(.green)
                                } else {
                                    Image(systemName: "circle")
                                        .foregroundStyle(.secondary)
                                }
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 10)
                        }
                        .buttonStyle(.plain)
                        if client.id != clients.last?.id {
                            Divider()
                                .opacity(0.25)
                                .padding(.leading, 12)
                        }
                    }
                }
                .onboardingCard()
                
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
            
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("onboarding_permission_status".localized)
                        .foregroundStyle(.secondary)
                    Spacer()
                    Text(permissionLabel)
                        .fontWeight(.semibold)
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
            }
            .onboardingCard()
            
            if isSystemNotificationGranted, isNotificationEnabledByUser {
                HStack(spacing: 12) {
                    Button {
                        Task { await sendTestNotification() }
                    } label: {
                        Label("onboarding_send_test_notification".localized, systemImage: "paperplane.fill")
                    }
                    .buttonStyle(.borderedProminent)
                    
                    Button {
                        Task { await scheduleNotificationsForActiveClient() }
                    } label: {
                        Label("onboarding_reschedule_now".localized, systemImage: "arrow.triangle.2.circlepath")
                    }
                    .buttonStyle(.bordered)
                }
                
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
            }
            
            if let permissionMessage {
                VStack(alignment: .leading, spacing: 8) {
                    Text(permissionMessage)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    Button {
                        openAppSettings()
                    } label: {
                        Label("onboarding_open_settings".localized, systemImage: "gearshape")
                    }
                    .buttonStyle(.bordered)
                }
                .onboardingCard()
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
                Button {
                    step = step.previous
                } label: {
                    Label("back".localized, systemImage: "chevron.left")
                }
                .buttonStyle(.bordered)
            }
            Spacer()
            Button {
                handlePrimary()
            } label: {
                Label(step.primaryButtonTitle, systemImage: step == .done ? "checkmark" : "chevron.right")
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
        return OnboardingFormatters.timestamp.string(from: date)
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

private enum OnboardingFormatters {
    static let timestamp: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm"
        return formatter
    }()
}

private struct OnboardingHero: View {
    let step: OnboardingStep
    
    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: "sparkles")
                .font(.title2)
                .foregroundStyle(.tint)
            OnboardingProgress(step: step)
        }
        .frame(maxWidth: .infinity)
    }
}

private struct OnboardingProgress: View {
    let step: OnboardingStep
    
    var body: some View {
        HStack(spacing: 8) {
            Dot(isActive: true)
            Dot(isActive: step != .client)
            Dot(isActive: step == .done)
        }
    }
    
    private struct Dot: View {
        let isActive: Bool
        
        var body: some View {
            Circle()
                .fill(isActive ? Color.accentColor : Color.secondary.opacity(0.25))
                .frame(width: 8, height: 8)
        }
    }
}

private extension View {
    func onboardingCard() -> some View {
        padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(.ultraThinMaterial)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .shadow(color: .black.opacity(0.10), radius: 12, x: 0, y: 6)
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
