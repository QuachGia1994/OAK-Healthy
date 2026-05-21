import SwiftUI
import SwiftData
@preconcurrency import UserNotifications

/// Màn hình Cài đặt và Thông tin ứng dụng (iOS).
public struct SettingsView: View {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.modelContext) private var modelContext
    @Environment(\.scenePhase) private var scenePhase
    @Query(sort: \ClientProfile.createdAt) private var clients: [ClientProfile]
    private let cycleEngine = CycleCalculator()
    @AppStorage("appTheme") private var appTheme: String = "system"
    @AppStorage("isNotificationEnabledByUser") private var isNotificationEnabledByUser: Bool = false
    @State private var isShowingAddClientSheet = false
    @State private var editingClient: ClientProfile?
    @State private var isShowingFactoryResetConfirm = false
    @State private var shareStackPNGURL: URL?
    @State private var isPreparingShareStack: Bool = false
    @State private var errorMessage: String?
    @State private var isShowingError = false
    @State private var importErrorMessage: String = ""
    @State private var showImportErrorAlert: Bool = false
    @State private var cachedActiveSupplements: [UserSupplement] = []
    
    public let activeClientManager: ActiveClientManager
    
    public init(activeClientManager: ActiveClientManager) {
        self.activeClientManager = activeClientManager
    }
    
    public var body: some View {
        NavigationStack {
            ZStack {
                backgroundGradient.ignoresSafeArea()
                settingsList
            }
        }
        .onChange(of: scenePhase) { _, newValue in
            guard newValue == .active else { return }
            Task { @MainActor in await syncNotificationPermissionState() }
        }
        .task {
            guard activeClientManager.currentClientId == nil else { return }
            guard let first = clients.first else { return }
            activeClientManager.setCurrentClientId(first.id)
        }
        .task(id: activeClientManager.currentClientId) {
            await reloadClientCaches()
        }
        .task {
            await syncNotificationPermissionState()
        }
        .sheet(isPresented: $isShowingAddClientSheet) {
            ClientEditorSheet(title: "add_client".localized, initialName: "") { name in
                guard !name.isEmpty else { return }
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
            ClientEditorSheet(title: "edit_client".localized, initialName: client.name) { name in
                client.name = name
                do {
                    try modelContext.save()
                } catch {
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
            clientManagementSection
            appHeaderSection
            themeSelectionSection
            dataTransferSection
            syncCenterSection
            supplementListSection
            userGuideSection
            aboutSection
            copyrightSection
            factoryResetSection
        }
        .scrollContentBackground(.hidden)
        .navigationTitle("settings_title".localized)
    }
    
    @ViewBuilder
    private var dataTransferSection: some View {
        Section {
            Toggle("notification_permission_toggle".localized, isOn: $isNotificationEnabledByUser)
                .onChange(of: isNotificationEnabledByUser) {
                    if isNotificationEnabledByUser {
                        Task { @MainActor in
                            do {
                                try await NotificationService.shared.requestAuthorization()
                            } catch {
                                isNotificationEnabledByUser = false
                                showError(message: error.localizedDescription)
                                return
                            }
                            await NotificationService.shared.replaceAllSchedules(supplements: supplementsForActiveClient)
                        }
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
                Task { @MainActor in
                    do {
                        try await NotificationService.shared.requestAuthorization()
                    } catch {
                        showError(message: error.localizedDescription)
                        return
                    }
                    await NotificationService.shared.replaceAllSchedules(supplements: supplementsForActiveClient)
                }
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
        } header: {
            Text("data_tools".localized)
        }
        .listRowBackground(glassRowBackground)
    }

    @ViewBuilder
    private var syncCenterSection: some View {
        Section {
            NavigationLink("sync_center_title".localized) {
                SyncCenterView(activeClientManager: activeClientManager)
            }
        } header: {
            Text("multi_device_sync_header".localized)
        }
        .listRowBackground(glassRowBackground)
    }
    
    @ViewBuilder
    private var clientManagementSection: some View {
        Section {
            if clients.isEmpty {
                Text("add_client_to_start".localized)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(clients) { client in
                    ClientRow(
                        client: client,
                        isActive: client.id == activeClientManager.currentClientId,
                        onSelect: { activeClientManager.setCurrentClientId(client.id) },
                        onEdit: { editingClient = client },
                        onDelete: { deleteClient(client) }
                    )
                }
            }
            
            Button("add_client".localized) {
                isShowingAddClientSheet = true
            }
        } header: {
            Text("client_management".localized)
        }
        .listRowBackground(glassRowBackground)
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
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.bottom, 12)
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 12)
            .background(.ultraThinMaterial)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .shadow(color: .black.opacity(0.12), radius: 12, x: 0, y: 6)
        }
        .listRowBackground(Color.clear)
        .listRowSeparator(.hidden)
    }
    
    @ViewBuilder
    private var themeSelectionSection: some View {
        Section {
            Picker(selection: $appTheme) {
                Text("appearance_light".localized).tag("light")
                Text("appearance_dark".localized).tag("dark")
                Text("appearance_system".localized).tag("system")
            } label: {
                Text("appearance_title".localized)
            }
            .pickerStyle(.segmented)
        } header: {
            Text("appearance_title".localized)
        }
        .listRowBackground(glassRowBackground)
    }
    
    @ViewBuilder
    private var supplementListSection: some View {
        Section {
            NavigationLink {
                MyStackListView(
                    title: "my_list_title".localized,
                    supplements: supplementsForActiveClient,
                    cycleSummary: getCycleSummary
                )
            } label: {
                HStack {
                    Text("manage_stack".localized)
                    Spacer()
                    Text("\(supplementsForActiveClient.count)")
                        .foregroundStyle(.secondary)
                }
            }
        } header: {
            Text("my_list_title".localized)
        }
        .listRowBackground(glassRowBackground)
    }
    
    @ViewBuilder
    private var userGuideSection: some View {
        Section {
            NavigationLink("user_guide_title".localized) {
                UserGuideView()
            }
        }
        .listRowBackground(glassRowBackground)
    }
    
    @ViewBuilder
    private var aboutSection: some View {
        Section {
            Text("settings_about_body".localized)
                .font(.body)
                .foregroundStyle(.secondary)
        } header: {
            Text("about_title".localized)
        }
        .listRowBackground(glassRowBackground)
    }
    
    @ViewBuilder
    private var copyrightSection: some View {
        Section {
            VStack(alignment: .leading, spacing: 4) {
                Text("settings_app_name_label".localized)
                    .font(.body)
                    .foregroundStyle(.secondary)
                Text("settings_author_label".localized)
                    .font(.body)
                    .foregroundStyle(.secondary)
                Text("settings_copyright_body".localized)
                    .font(.caption)
                    .foregroundStyle(.tertiary)
                    .padding(.top, 4)
            }
        } header: {
            Text("copyright_title".localized)
        }
        .listRowBackground(glassRowBackground)
    }

    @ViewBuilder
    private var factoryResetSection: some View {
        Section {
            Button("factory_reset".localized, role: .destructive) {
                isShowingFactoryResetConfirm = true
            }
        }
        .listRowBackground(glassRowBackground)
        .confirmationDialog(
            "wipe_data_warning".localized,
            isPresented: $isShowingFactoryResetConfirm,
            titleVisibility: .visible
        ) {
            Button("delete".localized, role: .destructive) {
                performFactoryReset()
            }
            Button("cancel".localized, role: .cancel) {}
        }
    }
    
    private var backgroundGradient: LinearGradient {
        let colors: [Color] = colorScheme == .dark
            ? [Color(red: 0.08, green: 0.0, blue: 0.15), .black]
            : [Color(.systemGroupedBackground), Color(.systemBackground)]
        return LinearGradient(colors: colors, startPoint: .topLeading, endPoint: .bottomTrailing)
    }

    private var glassRowBackground: some View {
        Color.clear.background(.ultraThinMaterial)
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

    private func performFactoryReset() {
        do {
            let records = try modelContext.fetch(FetchDescriptor<IntakeRecord>())
            for record in records {
                modelContext.delete(record)
            }
            
            let supplements = try modelContext.fetch(FetchDescriptor<UserSupplement>())
            for supplement in supplements {
                modelContext.delete(supplement)
            }
            
            let clients = try modelContext.fetch(FetchDescriptor<ClientProfile>())
            for client in clients {
                modelContext.delete(client)
            }
            
            try modelContext.save()
        } catch {
            return
        }
        
        UserDefaults.standard.removeObject(forKey: "SkippedUpdateVersion")
        appTheme = "system"
        activeClientManager.setCurrentClientId(nil)
    }
    
    @MainActor
    private func refreshSharePayloads() {
        guard activeClientManager.currentClientId != nil else {
            shareStackPNGURL = nil
            return
        }
        
        do {
            let png = try SupplementExportCodec.renderShareImageData(supplements: cachedActiveSupplements, colorScheme: colorScheme)
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
    
    private func showError(message: String) {
        errorMessage = message
        isShowingError = true
    }
    
    private var supplementsForActiveClient: [UserSupplement] {
        cachedActiveSupplements
    }
    
    @MainActor
    private func reloadClientCaches() async {
        guard let clientId = activeClientManager.currentClientId else {
            cachedActiveSupplements = []
            return
        }
        do {
            let supplementsDescriptor = FetchDescriptor<UserSupplement>(
                predicate: #Predicate { $0.client?.id == clientId },
                sortBy: [SortDescriptor(\UserSupplement.name)]
            )
            let supplements = try modelContext.fetch(supplementsDescriptor)
            cachedActiveSupplements = supplements.filter { $0.deletedAtEpochMs == nil }
        } catch {
            cachedActiveSupplements = []
        }
    }
    
    @MainActor
    private func syncNotificationPermissionState() async {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        let authorized = settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional
        if !authorized, isNotificationEnabledByUser {
            isNotificationEnabledByUser = false
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
        HStack {
            Text(client.name)
            Spacer()
            if isActive {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(.green)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture {
            onSelect()
        }
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
                .foregroundStyle(.secondary)
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
                    .foregroundStyle(.secondary)
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

private struct GuideRow: View {
    let number: String
    let text: String
    
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Text(number)
                .font(.caption)
                .fontWeight(.bold)
                .foregroundStyle(.white)
                .frame(width: 20, height: 20)
                .background(Color.blue)
                .clipShape(Circle())
            
            Text(text)
                .font(.subheadline)
        }
    }
}

#Preview {
    SettingsView(activeClientManager: ActiveClientManager())
}

private struct ClientEditorSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var name: String
    let title: String
    let onSave: (String) -> Void
    
    init(title: String, initialName: String, onSave: @escaping (String) -> Void) {
        self.title = title
        self._name = State(initialValue: initialName)
        self.onSave = onSave
    }
    
    var body: some View {
        NavigationStack {
            Form {
                TextField("client_name_label".localized, text: $name)
            }
            .navigationTitle(title)
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
