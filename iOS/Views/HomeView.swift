import SwiftUI
import SwiftData
import UIKit

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
    public let notificationService: NotificationService
    
    public init(
        activeClientManager: ActiveClientManager,
        notificationService: NotificationService
    ) {
        self.activeClientManager = activeClientManager
        self.notificationService = notificationService
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
                        Text("add_client_to_start".localized)
                            .foregroundStyle(.secondary)
                        Button("add_client".localized) {
                            isShowingAddClientSheet = true
                        }
                        .buttonStyle(.borderedProminent)
                    }
                } else {
                    List {
                    Section {
                        if viewModel.activeSupplements.isEmpty {
                            Text("no_intake_today".localized)
                                .foregroundStyle(.secondary)
                        }
                    } header: {
                        Text("today_intake_title".localized)
                    }
                    
                    let sortedTimes = viewModel.activeSupplements.keys.sorted()
                    ForEach(sortedTimes, id: \.self) { time in
                        if let items = viewModel.activeSupplements[time] {
                            Section {
                                ForEach(items) { supplement in
                                    ActiveSupplementRow(
                                        supplement: supplement,
                                        timeString: time,
                                        onToggle: { supplement, timeString, context in
                                            viewModel.toggleIntake(
                                                for: supplement,
                                                timeString: timeString,
                                                context: context,
                                                notificationService: notificationService
                                            )
                                        },
                                        isTaken: viewModel.isTakenToday(supplement, timeString: time)
                                    )
                                    .listRowBackground(Color.clear)
                                    .listRowSeparator(.hidden)
                                    .swipeActions(edge: .leading) {
                                        Button {
                                            editingSupplement = supplement
                                        } label: {
                                            Label("edit".localized, systemImage: "pencil")
                                        }
                                        .tint(.orange)
                                    }
                                    .swipeActions(edge: .trailing) {
                                        Button(role: .destructive) {
                                            viewModel.deleteSupplement(supplement, context: modelContext)
                                        } label: {
                                            Label("delete".localized, systemImage: "trash")
                                        }
                                    }
                                    .contextMenu {
                                        Button {
                                            editingSupplement = supplement
                                        } label: {
                                            Label("edit".localized, systemImage: "pencil")
                                        }
                                        
                                        Button(role: .destructive) {
                                            viewModel.deleteSupplement(supplement, context: modelContext)
                                        } label: {
                                            Label("delete".localized, systemImage: "trash")
                                        }
                                    }
                                }
                            } header: {
                                Text(time)
                                    .font(.caption)
                                    .fontWeight(.bold)
                                    .foregroundStyle(.blue)
                            }
                        }
                    }
                    
                    if !viewModel.restingSupplements.isEmpty {
                        Section {
                            ForEach(viewModel.restingSupplements) { info in
                                RestingSupplementRow(info: info, onEdit: { editingSupplement = $0 })
                                    .listRowBackground(Color.clear)
                                    .listRowSeparator(.hidden)
                                    .swipeActions(edge: .trailing) {
                                        Button(role: .destructive) {
                                            viewModel.deleteSupplement(info.supplement, context: modelContext)
                                        } label: {
                                            Label("delete".localized, systemImage: "trash")
                                        }
                                    }
                                    .swipeActions(edge: .leading) {
                                        Button {
                                            editingSupplement = info.supplement
                                        } label: {
                                            Label("edit".localized, systemImage: "pencil")
                                        }
                                        .tint(.orange)
                                    }
                                    .contextMenu {
                                        Button {
                                            editingSupplement = info.supplement
                                        } label: {
                                            Label("edit".localized, systemImage: "pencil")
                                        }
                                        
                                        Button(role: .destructive) {
                                            viewModel.deleteSupplement(info.supplement, context: modelContext)
                                        } label: {
                                            Label("delete".localized, systemImage: "trash")
                                        }
                                    }
                            }
                        } header: {
                            Text("resting_title".localized)
                        }
                    }
                    }
                    .scrollContentBackground(.hidden)
                    .listStyle(.plain)
                    .navigationTitle("today_intake_title".localized)
                    .toolbar {
                        ToolbarItem(placement: .topBarLeading) {
                            Menu {
                                ForEach(clients) { client in
                                    Button(client.name) {
                                        activeClientManager.setCurrentClientId(client.id)
                                    }
                                }
                                Button("add_client".localized) {
                                    isShowingAddClientSheet = true
                                }
                            } label: {
                                Text(clientTitle)
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
                    .alert("update_available_title".localized, isPresented: $updateService.isUpdateAvailable) {
                        if let url = URL(string: updateService.updateInfo?.updateUrl ?? "") {
                            Link("update_now".localized, destination: url)
                        }
                        if updateService.updateInfo?.forceUpdate != true {
                            Button("later".localized, role: .cancel) {
                                updateService.skipUpdate(version: updateService.updateInfo?.version ?? "")
                            }
                        }
                    } message: {
                        let version = updateService.updateInfo?.version ?? ""
                        let notes = updateService.updateInfo?.releaseNotes ?? ""
                        if notes.isEmpty {
                            Text("update_description".localized)
                            Text(String(format: "update_available_message_format".localized, version))
                        } else {
                            Text(notes)
                        }
                    }
                    .task {
                        try? await Task.sleep(for: .seconds(1))
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
        activeClient?.name ?? "dashboard_title".localized
    }
    
    private var clientTitle: String {
        activeClient?.name ?? "dashboard_title".localized
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
            .navigationTitle("add_client".localized)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("cancel".localized) { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("save".localized) {
                        onSave(name.trimmingCharacters(in: .whitespacesAndNewlines))
                        dismiss()
                    }
                    .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }
}

/// Thành phần hiển thị chất đang hoạt động.
private struct ActiveSupplementRow: View {
    @Environment(\.modelContext) private var modelContext
    let supplement: UserSupplement
    let timeString: String
    let onToggle: (UserSupplement, String, ModelContext) -> Void
    let isTaken: Bool
    @State private var showConfirm = false
    
    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(supplement.name)
                    .font(.headline)
                HStack {
                    Image(systemName: "clock")
                        .font(.caption2)
                    Text(timeString)
                        .font(.caption2)
                    Text("•")
                    Text(String(format: "dose_format".localized, supplement.dailyDose))
                        .font(.caption)
                }
                .foregroundStyle(.secondary)
                
                if let instruction = supplement.instruction, !instruction.isEmpty {
                    Text(instruction.localized)
                        .font(.caption)
                        .italic()
                        .foregroundStyle(.secondary)
                        .padding(.top, 2)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            Spacer()
            Button {
                guard !isTaken else { return }
                showConfirm = true
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
        .alert("home_confirm_intake_title".localized, isPresented: $showConfirm) {
            Button("cancel".localized, role: .cancel) {}
            Button("home_confirm_intake_action".localized) {
                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                onToggle(supplement, timeString, modelContext)
            }
        } message: {
            Text("home_confirm_intake_message".localized)
        }
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
                Text("resting_title".localized)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Text(String(format: "days_remaining_format".localized, info.daysRemaining))
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
    HomeView(activeClientManager: ActiveClientManager(), notificationService: NotificationService())
        .modelContainer(for: [ClientProfile.self, UserSupplement.self, IntakeRecord.self], inMemory: true)
}
