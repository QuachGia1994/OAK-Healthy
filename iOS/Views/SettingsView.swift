import SwiftUI
import SwiftData
@preconcurrency import UserNotifications

/// Màn hình Cài đặt và Thông tin ứng dụng (iOS).
public struct SettingsView: View {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.modelContext) private var modelContext
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(EntitlementManager.self) private var entitlementManager
    @Query(sort: \ClientProfile.createdAt) private var clients: [ClientProfile]
    private let cycleEngine = CycleCalculator()
    @AppStorage("appTheme") private var appTheme: String = "system"
    @AppStorage("shareAnonymousDiagnostics") private var shareAnonymousDiagnostics: Bool = false
    @AppStorage("isNotificationEnabledByUser") private var isNotificationEnabledByUser: Bool = false
    @AppStorage("oakLastNotificationRebuildEpochMs") private var lastNotificationRebuildEpochMs: Double = 0
    @State private var isShowingAddClientSheet = false
    @State private var editingClient: ClientProfile?
    @State private var isShowingFactoryResetConfirm = false
    @State private var shareStackPNGURL: URL?
    @State private var isPreparingShareStack: Bool = false
    @State private var errorMessage: String?
    @State private var isShowingError = false
    @State private var importErrorMessage: String = ""
    @State private var showImportErrorAlert: Bool = false
    @State private var notificationAuthorizationStatus: UNAuthorizationStatus = .notDetermined
    
    public let activeClientManager: ActiveClientManager
    
    public init(activeClientManager: ActiveClientManager) {
        self.activeClientManager = activeClientManager
    }
    
    public var body: some View {
        NavigationStack {
            ZStack {
                Color.clear.oakBackground()
                settingsList
            }
        }
        .preferredColorScheme(preferredColorScheme)
        .onChange(of: scenePhase) { _, newValue in
            guard newValue == .active else { return }
            Task { @MainActor in await syncNotificationPermissionState() }
        }
        .task {
            guard activeClientManager.currentClientId == nil else { return }
            guard let first = clients.first else { return }
            activeClientManager.setCurrentClientId(first.id)
        }
        .task {
            await syncNotificationPermissionState()
        }
        .sheet(isPresented: $isShowingAddClientSheet) {
            ClientEditorSheet(
                title: "add_client".localized,
                initialName: "",
                confirmTitle: "client_create_action".localized
            ) { name in
                guard !name.isEmpty, canCreateClient else {
                    showError(message: "plan_client_limit_reached".localized)
                    return
                }
                let created = ClientProfile(name: name)
                modelContext.insert(created)
                do {
                    try modelContext.save()
                    activeClientManager.setCurrentClientId(created.id)
                } catch {
                    modelContext.delete(created)
                    showError(message: error.localizedDescription)
                }
            }
        }
        .sheet(item: $editingClient) { client in
            ClientEditorSheet(
                title: "edit_client".localized,
                initialName: client.name
            ) { name in
                let previousName = client.name
                client.name = name
                do {
                    try modelContext.save()
                } catch {
                    client.name = previousName
                    showError(message: error.localizedDescription)
                }
            }
        }
        .alert("error_title".localized, isPresented: $isShowingError) {
            Button("ok".localized) {}
        } message: {
            Text(errorMessage ?? "")
        }
        .alert("import_notice_title".localized, isPresented: $showImportErrorAlert) {
            Button("ok".localized) {}
        } message: {
            Text(importErrorMessage)
        }
    }
    
    private var settingsList: some View {
        List {
            appHeaderSection
            clientManagementSection
            planAccessSection
            notificationsSection
            themeSelectionSection
            syncCenterSection
            dataToolsSection
            privacyDiagnosticsSection
            aboutSection
            copyrightSection
            factoryResetSection
        }
        .scrollContentBackground(.hidden)
        .listSectionSpacing(20)
        .navigationTitle("settings_title".localized)
    }
    
    @ViewBuilder
    private var privacyDiagnosticsSection: some View {
        Section {
            Toggle("diagnostics_opt_in_title".localized, isOn: $shareAnonymousDiagnostics)
                .onChange(of: shareAnonymousDiagnostics) { _, enabled in
                    DiagnosticsReporter.setConsent(enabled)
                }
            Text("diagnostics_opt_in_body".localized)
                .font(.footnote)
                .oakSecondaryText()
            Text("health_disclaimer_body".localized)
                .font(.footnote)
                .oakSecondaryText()
        } header: {
            Text("privacy_diagnostics_title".localized)
        }
        .listRowBackground(settingsRowBackground)
    }

    private var lastReminderRebuildText: String {
        guard lastNotificationRebuildEpochMs > 0 else { return "reliability_never".localized }
        let date = Date(timeIntervalSince1970: lastNotificationRebuildEpochMs)
        return date.formatted(date: .abbreviated, time: .shortened)
    }

    @ViewBuilder
    private var notificationsSection: some View {
        Section {
            SettingsValueRow(
                title: "onboarding_permission_status".localized,
                value: notificationPermissionText
            )
            SettingsValueRow(
                title: "reliability_last_rebuild".localized,
                value: lastReminderRebuildText
            )
            
            Toggle("notification_permission_toggle".localized, isOn: $isNotificationEnabledByUser)
                .onChange(of: isNotificationEnabledByUser) {
                    if isNotificationEnabledByUser {
                        Task { @MainActor in await rescheduleNotifications() }
                        return
                    }
                    Task { @MainActor in
                        await NotificationService.shared.clearAllPendingNotifications()
                    }
                }
            
            NavigationLink("notification_diagnostics_title".localized) {
                NotificationDebugScreen(activeClientManager: activeClientManager)
            }

            Button {
                Task { @MainActor in await rescheduleNotifications() }
            } label: {
                Label("onboarding_reschedule_now".localized, systemImage: "arrow.triangle.2.circlepath")
            }

            Button {
                Task { @MainActor in
                    await NotificationService.shared.clearAllPendingNotifications()
                }
            } label: {
                Label("settings_clear_pending_notifications".localized, systemImage: "trash")
            }
        } header: {
            Text("settings_notifications_title".localized)
        }
        .listRowBackground(settingsRowBackground)
    }
    
    @ViewBuilder
    private var dataToolsSection: some View {
        Section {
            if entitlementManager.canUse(.dataExport) {
                if let shareStackPNGURL {
                    ShareLink(item: shareStackPNGURL) {
                        Label("share_stack".localized, systemImage: "square.and.arrow.up")
                    }
                } else {
                    Button {
                        Task { @MainActor in await prepareShareStack() }
                    } label: {
                        Label("share_stack".localized, systemImage: "square.and.arrow.up")
                    }
                    .disabled(isPreparingShareStack || activeClientManager.currentClientId == nil)
                }
            } else {
                NavigationLink {
                    PlanAccessView()
                } label: {
                    Label("plan_unlock_export".localized, systemImage: "lock.fill")
                }
            }
        } header: {
            Text("data_tools".localized)
        }
        .listRowBackground(settingsRowBackground)
    }

    @ViewBuilder
    private var syncCenterSection: some View {
        Section {
            if entitlementManager.canUse(.encryptedCloudSync) {
                NavigationLink("sync_center_title".localized) {
                    SyncCenterView(activeClientManager: activeClientManager)
                }
            } else {
                NavigationLink {
                    PlanAccessView()
                } label: {
                    Label("plan_unlock_cloud_sync".localized, systemImage: "lock.fill")
                }
            }
        } header: {
            Text("multi_device_sync_header".localized)
        }
        .listRowBackground(settingsRowBackground)
    }
    
    @ViewBuilder
    private var clientManagementSection: some View {
        Section {
            if clients.isEmpty {
                VStack(spacing: 8) {
                    Image(systemName: "person.crop.circle.badge.plus")
                        .font(.title3)
                        .oakSecondaryText()
                    Text("add_client_to_start".localized)
                        .font(.subheadline.weight(.semibold))
                        .oakSecondaryText()
                        .multilineTextAlignment(.center)
                }
            } else {
                ForEach(permittedClients) { client in
                    ClientRow(
                        client: client,
                        isActive: client.id == activeClientManager.currentClientId,
                        onSelect: { activeClientManager.setCurrentClientId(client.id) },
                        onEdit: { editingClient = client },
                        onDelete: { deleteClient(client) }
                    )
                }
            }
            
            if canCreateClient {
                Button("add_client".localized) {
                    isShowingAddClientSheet = true
                }
            } else {
                NavigationLink {
                    PlanAccessView()
                } label: {
                    Label("plan_client_limit_reached".localized, systemImage: "lock.fill")
                }
            }
        } header: {
            Text("client_management".localized)
        }
        .listRowBackground(settingsRowBackground)
    }
    
    private var permittedClients: [ClientProfile] {
        entitlementManager.maxClients.map { Array(clients.prefix($0)) } ?? clients
    }

    private var canCreateClient: Bool {
        entitlementManager.maxClients.map { clients.count < $0 } ?? true
    }

    @ViewBuilder
    private var planAccessSection: some View {
        Section {
            NavigationLink {
                PlanAccessView()
            } label: {
                HStack {
                    Text("plan_access_manage".localized)
                    Spacer()
                    Text(planTitle(entitlementManager.snapshot.plan))
                        .oakSecondaryText()
                }
            }
            if entitlementManager.canUse(.coachReports) {
                NavigationLink("coach_overview_title".localized) {
                    CoachOverviewView()
                }
            } else {
                NavigationLink {
                    PlanAccessView()
                } label: {
                    HStack {
                        Text("coach_overview_title".localized)
                        Spacer()
                        Text("plan_coach_title".localized)
                            .oakSecondaryText()
                    }
                }
            }
#if DEBUG
            NavigationLink("demo_preview_title".localized) {
                DemoPreviewView()
            }
#endif
        } header: {
            Text("plan_access_title".localized)
        }
        .listRowBackground(settingsRowBackground)
    }

    @ViewBuilder
    private var appHeaderSection: some View {
        Section {
            VStack(spacing: 12) {
                OAKLogoView()
                    .padding(.top, 12)
                
                Text("dedication_text".localized)
                    .font(.subheadline)
                    .italic()
                    .oakSecondaryText()
                    .multilineTextAlignment(.center)
                    .padding(.bottom, 12)
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 12)
            .background(
                OAKPalette.mutedSurface(for: colorScheme),
                in: RoundedRectangle(cornerRadius: OAKRadius.md, style: .continuous)
            )
        }
        .listRowBackground(Color.clear)
        .listRowSeparator(.hidden)
    }
    
    @ViewBuilder
    private var themeSelectionSection: some View {
        Section {
            if dynamicTypeSize >= .accessibility1 {
                Picker("appearance_title".localized, selection: themeSelection) {
                    themeOptions
                }
                .pickerStyle(.menu)
            } else {
                Picker(selection: themeSelection) {
                    themeOptions
                } label: {
                    Text("appearance_title".localized)
                }
                .pickerStyle(.segmented)
            }
        } header: {
            Text("appearance_title".localized)
        }
        .listRowBackground(settingsRowBackground)
    }

    @ViewBuilder
    private var themeOptions: some View {
        Text("appearance_light".localized).tag("light")
        Text("appearance_dark".localized).tag("dark")
        Text("appearance_system".localized).tag("system")
    }
    
    @ViewBuilder
    private var aboutSection: some View {
        Section {
            Text("settings_about_body".localized)
                .font(.body)
                .oakSecondaryText()
        } header: {
            Text("about_title".localized)
        }
        .listRowBackground(settingsRowBackground)
    }
    
    @ViewBuilder
    private var copyrightSection: some View {
        Section {
            VStack(alignment: .leading, spacing: 4) {
                Text("settings_app_name_label".localized)
                    .font(.body)
                    .oakSecondaryText()
                Text("settings_author_label".localized)
                    .font(.body)
                    .oakSecondaryText()
                Text("settings_copyright_body".localized)
                    .font(.caption)
                    .oakTertiaryText()
                    .padding(.top, 4)
            }
        } header: {
            Text("copyright_title".localized)
        }
        .listRowBackground(settingsRowBackground)
    }

    @ViewBuilder
    private var factoryResetSection: some View {
        Section {
            Button("factory_reset".localized, role: .destructive) {
                isShowingFactoryResetConfirm = true
            }
        }
        .listRowBackground(settingsRowBackground)
        .confirmationDialog(
            "wipe_data_warning".localized,
            isPresented: $isShowingFactoryResetConfirm,
            titleVisibility: .visible
        ) {
            Button("delete".localized, role: .destructive) {
                Task { @MainActor in
                    await performFactoryReset()
                }
            }
            Button("cancel".localized, role: .cancel) {}
        }
    }

    private func planTitle(_ plan: CommercialPlan) -> String {
        switch plan {
        case .free: return "plan_free_title".localized
        case .pro: return "plan_pro_title".localized
        case .coach: return "plan_coach_title".localized
        }
    }

    private var settingsRowBackground: some View {
        OAKPalette.surface(for: colorScheme)
    }

    private var preferredColorScheme: ColorScheme? {
        switch appTheme {
        case "light": return .light
        case "dark": return .dark
        default: return nil
        }
    }

    private var themeSelection: Binding<String> {
        Binding(get: { appTheme }) { selection in
            var transaction = Transaction()
            transaction.disablesAnimations = true
            withTransaction(transaction) { appTheme = selection }
        }
    }
    
    private func deleteClient(_ client: ClientProfile) {
        let deletingActive = client.id == activeClientManager.currentClientId
        modelContext.delete(client)
        do {
            try modelContext.save()
        } catch {
            showError(message: error.localizedDescription)
            return
        }
        CloudSyncProfileStore().clearLinks(clientId: client.id)

        guard deletingActive else { return }
        let fallback = clients.first { $0.id != client.id }?.id
        activeClientManager.setCurrentClientId(fallback)
    }
    
    private func getCycleSummary(for supplement: UserSupplement) -> String {
        let config = supplement.cycleConfig
        let status = try? cycleEngine.determineStatus(for: supplement.startDate, config: config, at: .now)
        let statusText = status == .on ? "cycle_status_on".localized : "cycle_status_off".localized
        
        if config.isContinuous {
            return "cycle_continuous".localized
        }
        return String(format: "cycle_summary_format".localized, statusText, config.daysOn, config.daysOff)
    }

    @MainActor
    private func performFactoryReset() async {
        do {
            try await FactoryResetService.perform(
                modelContext: modelContext,
                activeClientManager: activeClientManager
            )
        } catch {
            showError(message: error.localizedDescription)
            return
        }
        appTheme = "system"
        isNotificationEnabledByUser = false
        shareStackPNGURL = nil
    }
    
    @MainActor
    private func refreshSharePayloads() {
        do {
            let png = try SupplementExportCodec.renderShareImageData(
                supplements: activeSupplements(),
                colorScheme: colorScheme
            )
            shareStackPNGURL = try writeTempFile(named: "OAKHealthy_Stack.png", data: png)
        } catch {
            shareStackPNGURL = nil
        }
    }
    
    @MainActor
    private func prepareShareStack() async {
        guard activeClientManager.currentClientId != nil else { return }
        guard !isPreparingShareStack else { return }
        isPreparingShareStack = true
        defer { isPreparingShareStack = false }
        refreshSharePayloads()
        guard shareStackPNGURL != nil else {
            showError(message: "export_failed".localized)
            return
        }
    }
    
    @MainActor
    private func rescheduleNotifications() async {
        do {
            try await NotificationService.shared.requestAuthorization()
            await NotificationService.shared.replaceAllSchedules(
                supplements: try activeSupplements()
            )
            ActivationRetentionStore.mark(.reminderReady)
        } catch {
            isNotificationEnabledByUser = false
            showError(message: error.localizedDescription)
        }
    }

    private func activeSupplements() throws -> [UserSupplement] {
        guard let clientId = activeClientManager.currentClientId else { return [] }
        return try ClientScopedStore.activeSupplements(
            modelContext: modelContext,
            clientId: clientId
        )
    }

    private func showError(message: String) {
        errorMessage = message
        isShowingError = true
    }
    
    @MainActor
    private func syncNotificationPermissionState() async {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        notificationAuthorizationStatus = settings.authorizationStatus
        let authorized = settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional
        if !authorized, isNotificationEnabledByUser {
            isNotificationEnabledByUser = false
        } else if authorized, isNotificationEnabledByUser {
            ActivationRetentionStore.mark(.reminderReady)
        }
    }
    
    private var notificationPermissionText: String {
        switch notificationAuthorizationStatus {
        case .authorized, .provisional:
            "onboarding_permission_granted".localized
        case .denied:
            "onboarding_permission_denied".localized
        default:
            "onboarding_permission_not_determined".localized
        }
    }
    
    private func writeTempFile(named fileName: String, data: Data) throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)
        try data.write(to: url, options: [.atomic])
        return url
    }
}

private struct ClientRow: View {
    let client: ClientProfile
    let isActive: Bool
    let onSelect: () -> Void
    let onEdit: () -> Void
    let onDelete: () -> Void
    
    var body: some View {
        Button {
            onSelect()
        } label: {
            HStack {
                Text(client.name)
                Spacer()
                if isActive {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(.green)
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(client.name), \(isActive ? "active".localized : "")")
        .swipeActions(edge: .trailing) {
            Button(role: .destructive) {
                onDelete()
            } label: {
                Label("delete".localized, systemImage: "trash")
            }
        }
        .swipeActions(edge: .leading) {
            Button {
                onEdit()
            } label: {
                Label("edit".localized, systemImage: "pencil")
            }
            .tint(.orange)
        }
    }
}

private struct SupplementRow: View, Equatable {
    let name: String
    let cycleSummary: String
    
    var body: some View {
        VStack(alignment: .leading) {
            Text(name)
                .font(.headline)
            Text(cycleSummary)
                .font(.caption)
                .oakSecondaryText()
        }
    }
}

private struct MyStackListView: View {
    let title: String
    let supplements: [UserSupplement]
    let cycleSummary: (UserSupplement) -> String
    
    var body: some View {
        List {
            if supplements.isEmpty {
                Text("no_supplements_yet".localized)
                    .oakSecondaryText()
            } else {
                ForEach(supplements) { supplement in
                    let time = supplement.intakeTime.trimmingCharacters(in: .whitespacesAndNewlines)
                    SupplementRow(
                        name: time.isEmpty ? supplement.name : "\(supplement.name) (\(time))",
                        cycleSummary: cycleSummary(supplement)
                    )
                    .equatable()
                }
            }
        }
        .scrollContentBackground(.hidden)
        .navigationTitle(title)
    }
}

private struct SettingsValueRow: View {
    let title: String
    let value: String

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            Text(title)
                .frame(maxWidth: .infinity, alignment: .leading)
                .layoutPriority(1)
            Text(value)
                .oakSecondaryText()
                .lineLimit(1)
                .minimumScaleFactor(0.85)
                .multilineTextAlignment(.trailing)
        }
    }
}

#Preview {
    SettingsView(activeClientManager: ActiveClientManager())
        .environment(EntitlementManager())
}
