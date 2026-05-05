import SwiftUI
import SwiftData

/// Màn hình chính Dashboard trên iOS.
public struct HomeView: View {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \ClientProfile.createdAt) private var clients: [ClientProfile]
    @Query private var supplements: [UserSupplement]
    
    @State private var viewModel = HomeViewModel()
    @State private var updateService = UpdateService()
    @State private var isShowingAddSheet = false
    @State private var editingSupplement: UserSupplement?
    @State private var isShowingAddClientSheet = false
    
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
                
                if clients.isEmpty {
                    VStack(spacing: 12) {
                        Text("Add a Client to start.")
                            .foregroundStyle(.secondary)
                        Button("Add a Client") {
                            isShowingAddClientSheet = true
                        }
                        .buttonStyle(.borderedProminent)
                    }
                } else {
                    List {
                    Section("today_intake_title") {
                        if viewModel.activeSupplements.isEmpty {
                            Text("no_intake_today")
                                .foregroundStyle(.secondary)
                        } else {
                            let sortedTimes = viewModel.activeSupplements.keys.sorted()
                            ForEach(sortedTimes, id: \.self) { time in
                                if let items = viewModel.activeSupplements[time] {
                                    TimeGroupSection(
                                        time: time,
                                        supplements: items,
                                        viewModel: viewModel,
                                        onEdit: { editingSupplement = $0 }
                                    )
                                }
                            }
                        }
                    }
                    
                    if !viewModel.restingSupplements.isEmpty {
                        Section("resting_title") {
                            ForEach(viewModel.restingSupplements) { info in
                                RestingSupplementRow(info: info, onEdit: { editingSupplement = $0 })
                                    .swipeActions(edge: .trailing) {
                                        Button(role: .destructive) {
                                            viewModel.deleteSupplement(info.supplement, context: modelContext)
                                        } label: {
                                            Label("delete", systemImage: "trash")
                                        }
                                    }
                                    .swipeActions(edge: .leading) {
                                        Button {
                                            editingSupplement = info.supplement
                                        } label: {
                                            Label("edit", systemImage: "pencil")
                                        }
                                        .tint(.orange)
                                    }
                                    .contextMenu {
                                        Button {
                                            editingSupplement = info.supplement
                                        } label: {
                                            Label("edit", systemImage: "pencil")
                                        }
                                        
                                        Button(role: .destructive) {
                                            viewModel.deleteSupplement(info.supplement, context: modelContext)
                                        } label: {
                                            Label("delete", systemImage: "trash")
                                        }
                                    }
                            }
                        }
                    }
                    }
                    .scrollContentBackground(.hidden)
                    .navigationTitle(navigationTitle)
                    .toolbar {
                        ToolbarItem(placement: .topBarLeading) {
                            Menu {
                                ForEach(clients) { client in
                                    Button(client.name) {
                                        activeClientManager.setCurrentClientId(client.id)
                                    }
                                }
                                Button("Add a Client") {
                                    isShowingAddClientSheet = true
                                }
                            } label: {
                                Text(navigationTitle)
                                    .font(.headline)
                            }
                        }
                        
                        ToolbarItem(placement: .topBarTrailing) {
                            Button {
                                isShowingAddSheet = true
                            } label: {
                                Image(systemName: "plus")
                            }
                        }
                    }
                    .sheet(isPresented: $isShowingAddSheet) {
                        AddSupplementView(modelContext: modelContext, activeClient: activeClient) { _ in
                        }
                    }
                    .sheet(item: $editingSupplement) { supplement in
                        AddSupplementView(modelContext: modelContext, editingSupplement: supplement, activeClient: activeClient) { _ in
                        }
                    }
                    .alert("update_available_title", isPresented: $updateService.isUpdateAvailable) {
                        if let url = URL(string: updateService.updateInfo?.updateUrl ?? "") {
                            Link("update_now", destination: url)
                        }
                        if updateService.updateInfo?.forceUpdate != true {
                            Button("later", role: .cancel) { }
                        }
                    } message: {
                        let version = updateService.updateInfo?.version ?? ""
                        let notes = updateService.updateInfo?.releaseNotes ?? ""
                        if notes.isEmpty {
                            Text(String(format: String(localized: "update_available_message"), version))
                        } else {
                            Text(notes)
                        }
                    }
                    .task {
                        await updateService.checkForUpdates()
                    }
                    .onAppear {
                        viewModel.processSupplements(supplements)
                    }
                    .onChange(of: supplements) {
                        viewModel.processSupplements(supplements)
                    }
                }
            }
        }
        .task {
            guard activeClientManager.currentClientId == nil else { return }
            guard let first = clients.first else { return }
            activeClientManager.setCurrentClientId(first.id)
        }
        .sheet(isPresented: $isShowingAddClientSheet) {
            AddClientSheet { name in
                guard !name.isEmpty else { return }
                let created = ClientProfile(name: name)
                modelContext.insert(created)
                try? modelContext.save()
                activeClientManager.setCurrentClientId(created.id)
            }
        }
    }
    
    private var backgroundGradient: LinearGradient {
        let colors: [Color] = colorScheme == .dark
            ? [Color(red: 0.08, green: 0.0, blue: 0.15), .black]
            : [Color(.systemGroupedBackground), Color(.systemBackground)]
        return LinearGradient(colors: colors, startPoint: .topLeading, endPoint: .bottomTrailing)
    }
    
    private var activeClient: ClientProfile? {
        guard let id = activeClientManager.currentClientId else { return nil }
        return clients.first { $0.id == id }
    }
    
    private var navigationTitle: String {
        activeClient?.name ?? String(localized: "dashboard_title")
    }
}

private struct AddClientSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var name: String = ""
    let onSave: (String) -> Void
    
    var body: some View {
        NavigationStack {
            Form {
                TextField("Name", text: $name)
            }
            .navigationTitle("Add a Client")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        onSave(name.trimmingCharacters(in: .whitespacesAndNewlines))
                        dismiss()
                    }
                    .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }
}

/// Thành phần hiển thị nhóm theo thời gian.
private struct TimeGroupSection: View {
    @Environment(\.modelContext) private var modelContext
    let time: String
    let supplements: [UserSupplement]
    let viewModel: HomeViewModel
    let onEdit: (UserSupplement) -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(time)
                .font(.caption)
                .fontWeight(.bold)
                .foregroundStyle(.blue)
            
            ForEach(supplements) { supplement in
                ActiveSupplementRow(
                    supplement: supplement, 
                    onToggle: viewModel.toggleIntake,
                    isTaken: viewModel.isTakenToday(supplement)
                )
                .swipeActions(edge: .leading) {
                    Button {
                        onEdit(supplement)
                    } label: {
                        Label("edit", systemImage: "pencil")
                    }
                    .tint(.orange)
                }
                .swipeActions(edge: .trailing) {
                    Button(role: .destructive) {
                        viewModel.deleteSupplement(supplement, context: modelContext)
                    } label: {
                        Label("delete", systemImage: "trash")
                    }
                }
                .contextMenu {
                    Button {
                        onEdit(supplement)
                    } label: {
                        Label("edit", systemImage: "pencil")
                    }
                    
                    Button(role: .destructive) {
                        viewModel.deleteSupplement(supplement, context: modelContext)
                    } label: {
                        Label("delete", systemImage: "trash")
                    }
                }
            }
        }
        .padding(.vertical, 4)
    }
}

/// Thành phần hiển thị chất đang hoạt động.
private struct ActiveSupplementRow: View {
    @Environment(\.modelContext) private var modelContext
    let supplement: UserSupplement
    let onToggle: (UserSupplement, ModelContext) -> Void
    let isTaken: Bool
    
    var body: some View {
        HStack {
            VStack(alignment: .leading) {
                Text(supplement.name)
                    .font(.headline)
                HStack {
                    Image(systemName: "clock")
                        .font(.caption2)
                    Text(supplement.intakeTime)
                        .font(.caption2)
                    Text("•")
                    Text(String(format: String(localized: "dose_format"), supplement.dailyDose))
                        .font(.caption)
                }
                .foregroundStyle(.secondary)
            }
            Spacer()
            Button {
                onToggle(supplement, modelContext)
            } label: {
                Image(systemName: isTaken ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(isTaken ? .green : .gray)
                    .font(.title2)
            }
        }
        .padding()
        .background(.ultraThinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .shadow(color: .black.opacity(0.12), radius: 10, x: 0, y: 6)
    }
}

/// Thành phần hiển thị chất đang nghỉ.
private struct RestingSupplementRow: View {
    let info: RestingSupplementInfo
    let onEdit: (UserSupplement) -> Void
    
    var body: some View {
        HStack {
            VStack(alignment: .leading) {
                Text(info.supplement.name)
                    .font(.headline)
                    .foregroundStyle(.secondary)
                Text("resting_title")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Text(String(format: String(localized: "days_remaining_format"), info.daysRemaining))
                .font(.caption)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(.secondary.opacity(0.2))
                .clipShape(Capsule())
        }
        .opacity(0.6)
        .padding()
        .background(.ultraThinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .shadow(color: .black.opacity(0.10), radius: 8, x: 0, y: 4)
    }
}

#Preview {
    HomeView(activeClientManager: ActiveClientManager())
        .modelContainer(for: [ClientProfile.self, UserSupplement.self, IntakeRecord.self], inMemory: true)
}
