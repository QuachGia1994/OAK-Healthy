import SwiftUI
import SwiftData
import UniformTypeIdentifiers

/// Màn hình Cài đặt và Thông tin ứng dụng (iOS).
public struct SettingsView: View {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \ClientProfile.createdAt) private var clients: [ClientProfile]
    @Query(sort: \UserSupplement.name) private var allSupplements: [UserSupplement]
    @Query(sort: [SortDescriptor(\IntakeRecord.date, order: .reverse)]) private var allRecords: [IntakeRecord]
    @AppStorage("appTheme") private var appTheme: String = "system"
    @State private var isShowingAddClientSheet = false
    @State private var editingClient: ClientProfile?
    @State private var isShowingFactoryResetConfirm = false
    @State private var isShowingImportPicker = false
    @State private var exportJSONURL: URL?
    @State private var shareStackPNGURL: URL?
    @State private var errorMessage: String?
    @State private var isShowingError = false
    @State private var importErrorMessage: String = ""
    @State private var showImportErrorAlert: Bool = false
    
    public let activeClientManager: ActiveClientManager

    private static var allowedBackupContentTypes: [UTType] {
        [.item]
    }
    
    public init(activeClientManager: ActiveClientManager) {
        self.activeClientManager = activeClientManager
    }
    
    public var body: some View {
        let refreshId = "\(activeClientManager.currentClientId?.uuidString ?? "nil")-\(supplementsForActiveClient.count)-\(recordsForActiveClient.count)-\(colorScheme == .dark ? "d" : "l")"
        NavigationStack {
            ZStack {
                backgroundGradient.ignoresSafeArea()
                settingsList
            }
        }
        .task {
            guard activeClientManager.currentClientId == nil else { return }
            guard let first = clients.first else { return }
            activeClientManager.setCurrentClientId(first.id)
        }
        .task(id: refreshId) {
            refreshSharePayloads()
        }
        .sheet(isPresented: $isShowingAddClientSheet) {
            ClientEditorSheet(title: "add_client".localized, initialName: "") { name in
                guard !name.isEmpty else { return }
                let created = ClientProfile(name: name)
                modelContext.insert(created)
                try? modelContext.save()
                activeClientManager.setCurrentClientId(created.id)
            }
        }
        .sheet(item: $editingClient) { client in
            ClientEditorSheet(title: "edit_client".localized, initialName: client.name) { name in
                client.name = name
                try? modelContext.save()
            }
        }
        .alert("error_title".localized, isPresented: $isShowingError) {
            Button("ok".localized) {}
        } message: {
            Text(errorMessage ?? "")
        }
        .alert("Thông báo Nhập dữ liệu", isPresented: $showImportErrorAlert) {
            Button("OK") {}
        } message: {
            Text(importErrorMessage)
        }
        .fileImporter(
            isPresented: $isShowingImportPicker,
            allowedContentTypes: Self.allowedBackupContentTypes,
            allowsMultipleSelection: false
        ) { result in
            switch result {
            case .success(let urls):
                guard let clientId = activeClientManager.currentClientId else {
                    showError(message: "missing_active_client".localized)
                    return
                }
                guard let url = urls.first else { return }

                print("SUCCESS: File selected at \(url.absoluteString)")
                let didAccess = url.startAccessingSecurityScopedResource()
                print("SECURITY SCOPE: \(didAccess ? "granted" : "not_granted")")
                defer {
                    if didAccess { url.stopAccessingSecurityScopedResource() }
                }

                guard url.pathExtension.lowercased() == "json" else {
                    importErrorMessage = "Invalid file type selected. Must be a .json file."
                    showImportErrorAlert = true
                    return
                }

                do {
                    let data = try Data(contentsOf: url)
                    guard let client = clients.first(where: { $0.id == clientId }) else {
                        showError(message: "missing_active_client".localized)
                        return
                    }

                    try SupplementExportCodec.importBackup(data: data, client: client, context: modelContext)
                    refreshSharePayloads()
                    importErrorMessage = "Nhập dữ liệu thành công!"
                    showImportErrorAlert = true
                } catch SupplementExportError.invalidJSON {
                    importErrorMessage = "Dữ liệu JSON không hợp lệ."
                    showImportErrorAlert = true
                } catch let DecodingError.dataCorrupted(context) {
                    importErrorMessage = "Dữ liệu hỏng: \(context.debugDescription)"
                    showImportErrorAlert = true
                } catch let DecodingError.keyNotFound(key, context) {
                    importErrorMessage = "Thiếu trường dữ liệu '\(key.stringValue)': \(context.debugDescription)"
                    showImportErrorAlert = true
                } catch let DecodingError.typeMismatch(type, context) {
                    importErrorMessage = "Sai kiểu dữ liệu '\(type)': \(context.debugDescription)"
                    showImportErrorAlert = true
                } catch let DecodingError.valueNotFound(type, context) {
                    importErrorMessage = "Thiếu giá trị '\(type)': \(context.debugDescription)"
                    showImportErrorAlert = true
                } catch {
                    let accessNote = didAccess ? "securityScope=ok" : "securityScope=not_granted"
                    importErrorMessage = "Lỗi đọc/nhập file (\(accessNote)): \(error.localizedDescription)"
                    showImportErrorAlert = true
                }
            case .failure(let error):
                print("FILE PICKER ERROR: \(error.localizedDescription)")
                importErrorMessage = "Lỗi chọn file: \(error.localizedDescription)"
                showImportErrorAlert = true
            }
        }
    }
    
    private var settingsList: some View {
        List {
            clientManagementSection
            appHeaderSection
            themeSelectionSection
            dataTransferSection
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
            if let exportJSONURL {
                ShareLink(item: exportJSONURL) {
                    Label("export_data".localized, systemImage: "square.and.arrow.up")
                }
            } else {
                Button("export_data".localized) {
                    refreshSharePayloads()
                }
            }
            
            Button("import_data".localized) {
                isShowingImportPicker = true
            }
            
            if let shareStackPNGURL {
                ShareLink(item: shareStackPNGURL) {
                    Label("share_stack".localized, systemImage: "square.and.arrow.up")
                }
            }
        } header: {
            Text("data_tools".localized)
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
            DisclosureGroup("user_guide_title".localized) {
                VStack(alignment: .leading, spacing: 8) {
                    GuideRow(number: "1", text: "settings_guide_1".localized)
                    GuideRow(number: "2", text: "settings_guide_2".localized)
                    GuideRow(number: "3", text: "settings_guide_3".localized)
                    GuideRow(number: "4", text: "settings_guide_4".localized)
                }
                .padding(.vertical, 8)
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
        try? modelContext.save()
        
        guard deletingActive else { return }
        let fallback = clients.first { $0.id != client.id }?.id
        activeClientManager.setCurrentClientId(fallback)
    }
    
    private func getCycleSummary(for supplement: UserSupplement) -> String {
        let config = supplement.cycleConfig
        let cycleEngine = CycleCalculator()
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
    
    private func refreshSharePayloads() {
        guard activeClientManager.currentClientId != nil else {
            exportJSONURL = nil
            shareStackPNGURL = nil
            return
        }
        do {
            let json = try SupplementExportCodec.encodeBackup(
                supplements: supplementsForActiveClient,
                records: recordsForActiveClient
            )
            exportJSONURL = try writeTempFile(named: "OAKHealthy_Backup.json", data: json)
        } catch {
            exportJSONURL = nil
            showError(message: "export_failed".localized)
        }
        
        do {
            let png = try SupplementExportCodec.renderShareImageData(supplements: supplementsForActiveClient, colorScheme: colorScheme)
            shareStackPNGURL = try writeTempFile(named: "OAKHealthy_Stack.png", data: png)
        } catch {
            shareStackPNGURL = nil
        }
    }
    
    private func showError(message: String) {
        errorMessage = message
        isShowingError = true
    }
    
    private var supplementsForActiveClient: [UserSupplement] {
        guard let currentClientId = activeClientManager.currentClientId else { return [] }
        return allSupplements.filter { $0.client?.id == currentClientId }
    }
    
    private var recordsForActiveClient: [IntakeRecord] {
        guard let currentClientId = activeClientManager.currentClientId else { return [] }
        return allRecords.filter { $0.supplement?.client?.id == currentClientId }
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

private struct SupplementRow: View {
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
                    SupplementRow(
                        name: supplement.name,
                        cycleSummary: cycleSummary(supplement)
                    )
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
                TextField("Name", text: $name)
            }
            .navigationTitle(title)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
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
