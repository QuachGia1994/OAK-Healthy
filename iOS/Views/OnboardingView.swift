import SwiftUI
import SwiftData
@preconcurrency import UserNotifications

public struct OnboardingView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @Query(sort: \ClientProfile.createdAt) private var clients: [ClientProfile]
    
    public let activeClientManager: ActiveClientManager
    public let notificationService: NotificationService
    
    @AppStorage("isNotificationEnabledByUser") private var isNotificationEnabledByUser: Bool = false
    @AppStorage("hasCompletedOnboarding") private var hasCompletedOnboarding: Bool = false
    
    @State private var step: OnboardingStep = .client
    @State private var isShowingAddClient: Bool = false
    @State private var permissionMessage: String?
    @State private var isRequestingPermission: Bool = false
    
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
                    try? modelContext.save()
                    activeClientManager.setCurrentClientId(created.id)
                }
            }
        }
        .interactiveDismissDisabled()
        .task {
            guard activeClientManager.currentClientId == nil else { return }
            guard let first = clients.first else { return }
            activeClientManager.setCurrentClientId(first.id)
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
        }
    }
    
    private var notificationsStep: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("onboarding_step_notifications_title".localized)
                .font(.title2)
                .fontWeight(.semibold)
            
            Text("onboarding_step_notifications_body".localized)
                .foregroundStyle(.secondary)
            
            Toggle("notification_permission_toggle".localized, isOn: $isNotificationEnabledByUser)
                .onChange(of: isNotificationEnabledByUser) { _, newValue in
                    guard newValue else {
                        permissionMessage = nil
                        Task { await NotificationService.shared.clearAllPendingNotifications() }
                        return
                    }
                    Task { await requestNotificationsIfNeeded() }
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
        
        let center = UNUserNotificationCenter.current()
        let granted = (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
        guard granted else {
            isNotificationEnabledByUser = false
            permissionMessage = "onboarding_notifications_denied".localized
            return
        }
        permissionMessage = nil
        await scheduleNotificationsForActiveClient()
    }
    
    @MainActor
    private func scheduleNotificationsForActiveClient() async {
        guard let clientId = activeClientManager.currentClientId else { return }
        let descriptor = FetchDescriptor<UserSupplement>(predicate: #Predicate { $0.deletedAtEpochMs == nil })
        let all: [UserSupplement]
        do {
            all = try modelContext.fetch(descriptor)
        } catch {
            return
        }
        let supplements = all.filter { $0.client?.id == clientId }
        await notificationService.scheduleAll(supplements: supplements)
    }
    
    private func openAppSettings() {
        guard let url = URL(string: "app-settings:") else { return }
        openURL(url)
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

