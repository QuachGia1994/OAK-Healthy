import SwiftUI
import SwiftData

/// Màn hình Cài đặt và Thông tin ứng dụng (iOS).
public struct SettingsView: View {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \ClientProfile.createdAt) private var clients: [ClientProfile]
    @Query private var supplements: [UserSupplement]
    @AppStorage("appTheme") private var appTheme: String = "system"
    @State private var isShowingAddClientSheet = false
    @State private var editingClient: ClientProfile?
    
    public let activeClientManager: ActiveClientManager
    
    public init(activeClientManager: ActiveClientManager) {
        self.activeClientManager = activeClientManager
        if let id = activeClientManager.currentClientId {
            _supplements = Query(
                filter: #Predicate<UserSupplement> { $0.client?.id == id },
                sort: [SortDescriptor(\UserSupplement.name)]
            )
        } else {
            _supplements = Query(
                filter: #Predicate<UserSupplement> { _ in false },
                sort: [SortDescriptor(\UserSupplement.name)]
            )
        }
    }
    
    public var body: some View {
        NavigationStack {
            ZStack {
                backgroundGradient
                    .ignoresSafeArea()
                
                List {
                    Section("Client Management") {
                        if clients.isEmpty {
                            Text("Add a Client to start.")
                                .foregroundStyle(.secondary)
                        } else {
                            ForEach(clients) { client in
                                HStack {
                                    Text(client.name)
                                    Spacer()
                                    if client.id == activeClientManager.currentClientId {
                                        Image(systemName: "checkmark.circle.fill")
                                            .foregroundStyle(.green)
                                    }
                                }
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    activeClientManager.setCurrentClientId(client.id)
                                }
                                .swipeActions(edge: .trailing) {
                                    Button(role: .destructive) {
                                        deleteClient(client)
                                    } label: {
                                        Label("delete", systemImage: "trash")
                                    }
                                }
                                .swipeActions(edge: .leading) {
                                    Button {
                                        editingClient = client
                                    } label: {
                                        Label("edit", systemImage: "pencil")
                                    }
                                    .tint(.orange)
                                }
                            }
                        }
                        
                        Button("Add a Client") {
                            isShowingAddClientSheet = true
                        }
                    }
                    .listRowBackground(.ultraThinMaterial)
                    
                    Section {
                        VStack(spacing: 12) {
                            OAKLogoView()
                                .padding(.top, 12)
                            
                            Text("dedication_text")
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
                    
                    Section("appearance_title") {
                        Picker("appearance_title", selection: $appTheme) {
                            Text("appearance_light").tag("light")
                            Text("appearance_dark").tag("dark")
                            Text("appearance_system").tag("system")
                        }
                        .pickerStyle(.segmented)
                    }
                    .listRowBackground(.ultraThinMaterial)
                    
                    Section("my_list_title") {
                        if supplements.isEmpty {
                            Text("no_supplements_yet")
                                .foregroundStyle(.secondary)
                        } else {
                            ForEach(supplements) { supplement in
                                VStack(alignment: .leading) {
                                    Text(supplement.name)
                                        .font(.headline)
                                    Text(getCycleSummary(for: supplement))
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                    .listRowBackground(.ultraThinMaterial)
                    
                    Section("user_guide_title") {
                        VStack(alignment: .leading, spacing: 8) {
                            GuideRow(number: "1", text: String(localized: "settings_guide_1"))
                            GuideRow(number: "2", text: String(localized: "settings_guide_2"))
                            GuideRow(number: "3", text: String(localized: "settings_guide_3"))
                            GuideRow(number: "4", text: String(localized: "settings_guide_4"))
                        }
                        .padding(.vertical, 4)
                    }
                    .listRowBackground(.ultraThinMaterial)
                    
                    Section("about_title") {
                        Text(String(localized: "settings_about_body"))
                            .font(.body)
                            .foregroundStyle(.secondary)
                    }
                    .listRowBackground(.ultraThinMaterial)
                    
                    Section("copyright_title") {
                        VStack(alignment: .leading, spacing: 4) {
                            LabeledContent(String(localized: "settings_app_name_label"), value: "OAK Healthy v1.0")
                            LabeledContent(String(localized: "settings_author_label"), value: "Mr. Phong (Personal Trader)")
                            Text(String(localized: "settings_copyright_body"))
                                .font(.caption)
                                .foregroundStyle(.tertiary)
                                .padding(.top, 4)
                        }
                    }
                    .listRowBackground(.ultraThinMaterial)
                }
                .scrollContentBackground(.hidden)
                .navigationTitle("settings_title")
            }
        }
        .task {
            guard activeClientManager.currentClientId == nil else { return }
            guard let first = clients.first else { return }
            activeClientManager.setCurrentClientId(first.id)
        }
        .sheet(isPresented: $isShowingAddClientSheet) {
            ClientEditorSheet(title: "Add a Client", initialName: "") { name in
                guard !name.isEmpty else { return }
                let created = ClientProfile(name: name)
                modelContext.insert(created)
                try? modelContext.save()
                activeClientManager.setCurrentClientId(created.id)
            }
        }
        .sheet(item: $editingClient) { client in
            ClientEditorSheet(title: "Edit Client", initialName: client.name) { name in
                client.name = name
                try? modelContext.save()
            }
        }
    }
    
    private var backgroundGradient: LinearGradient {
        let colors: [Color] = colorScheme == .dark
            ? [Color(red: 0.08, green: 0.0, blue: 0.15), .black]
            : [Color(.systemGroupedBackground), Color(.systemBackground)]
        return LinearGradient(colors: colors, startPoint: .topLeading, endPoint: .bottomTrailing)
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
        let statusText = status == .on ? String(localized: "cycle_status_on") : String(localized: "cycle_status_off")
        
        if config.isContinuous {
            return String(localized: "cycle_continuous")
        }
        return String(format: String(localized: "cycle_summary_format"), statusText, config.daysOn, config.daysOff)
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
