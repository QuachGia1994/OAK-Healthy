import SwiftUI
import SwiftData

/// Màn hình chính Dashboard trên iOS.
public struct HomeView: View {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \ClientProfile.createdAt) private var clients: [ClientProfile]
    @Query(sort: \UserSupplement.name) private var supplements: [UserSupplement]
    
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
    }
    
    public var body: some View {
        NavigationStack {
            ZStack {
                backgroundGradient
                    .ignoresSafeArea()
                
                if clients.isEmpty {
                    VStack(spacing: 12) {
                        Text("add_client_to_start".localized)
                            .font(.headline)
                        Text("settings_guide_1".localized)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                        Button("add_client".localized) {
                            isShowingAddClientSheet = true
                        }
                        .buttonStyle(.borderedProminent)
                    }
                    .padding(20)
                    .background(.ultraThinMaterial)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .shadow(color: .black.opacity(0.10), radius: 12, x: 0, y: 6)
                    .padding(.horizontal, 24)
                } else {
                    List {
                    Section {
                        if viewModel.activeSupplements.isEmpty {
                            Text("no_intake_today".localized)
                                .foregroundStyle(.secondary)
                                .listRowBackground(Color.clear)
                                .listRowSeparator(.hidden)
                                .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                        }
                    } header: {
                        TodayHeaderView(
                            title: "today_intake_title".localized,
                            streakDays: viewModel.cachedStreakDays,
                            counts: viewModel.cachedTodayCounts
                        )
                    }
                    
                    
                    ForEach(viewModel.activeSupplementTimes, id: \.self) { time in
                        if let items = viewModel.activeSupplements[time] {
                            Section {
                                ForEach(items) { supplement in
                                    ActiveSupplementRow(
                                        supplement: supplement,
                                        timeString: time,
                                        status: viewModel.doseStatus(supplement, timeString: time),
                                        urgency: viewModel.doseUrgency(supplement, timeString: time),
                                        onAction: { supplement, timeString, action, context in
                                            viewModel.markDose(
                                                for: supplement,
                                                timeString: timeString,
                                                action: action,
                                                context: context,
                                                notificationService: notificationService
                                            )
                                        }
                                    )
                                    .listRowBackground(Color.clear)
                                    .listRowSeparator(.hidden)
                                    .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                                    .swipeActions(edge: .leading, allowsFullSwipe: false) {
                                        Button {
                                            editingSupplement = supplement
                                        } label: {
                                            Label("edit".localized, systemImage: "pencil")
                                        }
                                        .tint(.orange)
                                    }
                                    .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                                        Button(role: .destructive) {
                                            viewModel.deleteSupplement(supplement, context: modelContext, notificationService: notificationService)
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
                                            viewModel.deleteSupplement(supplement, context: modelContext, notificationService: notificationService)
                                        } label: {
                                            Label("delete".localized, systemImage: "trash")
                                        }
                                    }
                                }
                            } header: {
                                HStack {
                                    Text(time)
                                        .font(.subheadline)
                                        .fontWeight(.semibold)
                                        .foregroundStyle(.secondary)
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 6)
                                        .background(.ultraThinMaterial)
                                        .clipShape(Capsule())
                                    Spacer()
                                }
                                .textCase(nil)
                            }
                        }
                    }
                    
                    if !viewModel.restingSupplements.isEmpty {
                        Section {
                            ForEach(viewModel.restingSupplements) { info in
                                RestingSupplementRow(info: info, onEdit: { editingSupplement = $0 })
                                    .listRowBackground(Color.clear)
                                    .listRowSeparator(.hidden)
                                    .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                                    .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                                        Button(role: .destructive) {
                                            viewModel.deleteSupplement(info.supplement, context: modelContext, notificationService: notificationService)
                                        } label: {
                                            Label("delete".localized, systemImage: "trash")
                                        }
                                    }
                                    .swipeActions(edge: .leading, allowsFullSwipe: false) {
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
                                            viewModel.deleteSupplement(info.supplement, context: modelContext, notificationService: notificationService)
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
                    .navigationTitle("dashboard_title".localized)
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbarBackground(.ultraThinMaterial, for: .navigationBar)
                    .toolbarBackground(.visible, for: .navigationBar)
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
                            Text(String.localizedStringWithFormat("update_available_message_format".localized, version))
                        } else {
                            Text(notes)
                        }
                    }
                    .task {
                        try? await Task.sleep(for: .seconds(1))
                        await updateService.checkForUpdates()
                    }
                    .onAppear {
                        viewModel.processSupplements(supplementsForActiveClient)
                    }
                    .onChange(of: supplements) {
                        viewModel.processSupplements(supplementsForActiveClient)
                    }
                    .task(id: activeClientManager.currentClientId) {
                        viewModel.processSupplements(supplementsForActiveClient)
                    }
                    .alert(
                        "error_title".localized,
                        isPresented: Binding(
                            get: { viewModel.errorMessage != nil },
                            set: { newValue in
                                if !newValue { viewModel.errorMessage = nil }
                            }
                        )
                    ) {
                        Button("ok".localized) { viewModel.errorMessage = nil }
                    } message: {
                        Text(viewModel.errorMessage ?? "")
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
                do {
                    try modelContext.save()
                } catch {
                    viewModel.errorMessage = error.localizedDescription
                    return
                }
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

    private var supplementsForActiveClient: [UserSupplement] {
        guard let id = activeClientManager.currentClientId else { return [] }
        return supplements.filter { $0.client?.id == id }
    }
    
    private var navigationTitle: String {
        activeClient?.name ?? "dashboard_title".localized
    }
    
    private var clientTitle: String {
        activeClient?.name ?? "dashboard_title".localized
    }
}

private struct TodayHeaderView: View {
    let title: String
    let streakDays: Int
    let counts: HomeViewModel.TodayCounts
    
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline) {
                Text(title)
                    .font(.title3)
                    .fontWeight(.bold)
                if streakDays > 0 {
                    StreakChip(streakDays: streakDays)
                }
                Spacer()
            }
            
            let columns = [
                GridItem(.flexible(), spacing: 8),
                GridItem(.flexible(), spacing: 8)
            ]
            LazyVGrid(columns: columns, alignment: .leading, spacing: 8) {
                CountChip(title: "notif_action_taken".localized, value: counts.taken, tint: .green)
                CountChip(title: "dose_status_planned".localized, value: counts.planned, tint: .gray)
                CountChip(title: "dose_status_skipped".localized, value: counts.skipped, tint: .orange)
                CountChip(title: "dose_status_missed".localized, value: counts.missed, tint: .red)
            }
        }
    }
}

private struct StreakChip: View {
    let streakDays: Int
    
    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: "flame.fill")
                .font(.caption)
                .foregroundStyle(.orange)
            Text(String.localizedStringWithFormat("home_streak_format".localized, streakDays))
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(.ultraThinMaterial)
        .clipShape(Capsule())
    }
}

private struct CountChip: View {
    let title: String
    let value: Int
    let tint: Color
    
    var body: some View {
        HStack(spacing: 6) {
            Circle()
                .fill(tint)
                .frame(width: 8, height: 8)
            Text("\(title) \(value)")
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(1)
                .minimumScaleFactor(0.85)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(.ultraThinMaterial)
        .clipShape(Capsule())
        .frame(maxWidth: .infinity, alignment: .leading)
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
    let status: HomeViewModel.DoseStatus
    let urgency: HomeViewModel.DoseUrgency
    let onAction: (UserSupplement, String, HomeViewModel.DoseAction, ModelContext) -> Void
    @State private var isShowingActions = false
    @State private var iconScale: CGFloat = 1
    
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
                    Text(String.localizedStringWithFormat("dose_format".localized, supplement.dailyDose))
                        .font(.caption)
                }
                .foregroundStyle(.secondary)
                
                if status == .missed {
                    Text("dose_status_missed".localized)
                        .font(.caption)
                        .foregroundStyle(.red)
                } else if status == .skipped {
                    Text("dose_status_skipped".localized)
                        .font(.caption)
                        .foregroundStyle(.orange)
                } else if urgency == .dueSoon {
                    UrgencyChip(title: "home_due_soon".localized, tint: .blue)
                } else if urgency == .missedSoon {
                    UrgencyChip(title: "home_almost_missed".localized, tint: .red)
                }
                
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
                guard status != .taken, status != .skipped else { return }
                withAnimation(.snappy) {
                    isShowingActions = true
                }
            } label: {
                Image(systemName: symbolName(for: status))
                    .foregroundStyle(symbolColor(for: status))
                    .font(.title2)
                    .scaleEffect(iconScale)
                    .animation(.snappy, value: status)
            }
        }
        .padding()
        .background(.ultraThinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(borderColor, lineWidth: borderWidth)
        )
        .shadow(color: shadowColor, radius: 10, x: 0, y: 6)
        .animation(.snappy, value: urgency)
        .confirmationDialog(
            "home_confirm_intake_title".localized,
            isPresented: $isShowingActions,
            titleVisibility: .visible
        ) {
            Button("home_confirm_intake_action".localized) {
                pulseIcon()
                withAnimation(.snappy) {
                    onAction(supplement, timeString, .taken, modelContext)
                }
            }
            Button("notif_action_skip".localized) {
                pulseIcon()
                withAnimation(.snappy) {
                    onAction(supplement, timeString, .skipped, modelContext)
                }
            }
            Button("cancel".localized, role: .cancel) {}
        } message: {
            Text("home_confirm_intake_message".localized)
        }
    }
    
    private func symbolName(for status: HomeViewModel.DoseStatus) -> String {
        switch status {
        case .planned:
            "circle"
        case .taken:
            "checkmark.circle.fill"
        case .skipped:
            "xmark.circle.fill"
        case .missed:
            "exclamationmark.circle.fill"
        }
    }
    
    private func symbolColor(for status: HomeViewModel.DoseStatus) -> Color {
        switch status {
        case .planned:
            .gray
        case .taken:
            .green
        case .skipped:
            .orange
        case .missed:
            .red
        }
    }
    
    private var borderColor: Color {
        switch urgency {
        case .none: .clear
        case .dueSoon: .blue.opacity(0.35)
        case .missedSoon: .red.opacity(0.35)
        }
    }
    
    private var borderWidth: CGFloat {
        urgency == .none ? 0 : 1
    }
    
    private var shadowColor: Color {
        switch urgency {
        case .none: .black.opacity(0.12)
        case .dueSoon: .blue.opacity(0.16)
        case .missedSoon: .red.opacity(0.16)
        }
    }
    
    @MainActor
    private func pulseIcon() {
        withAnimation(.spring(response: 0.22, dampingFraction: 0.55)) {
            iconScale = 1.25
        }
        Task {
            try? await Task.sleep(for: .milliseconds(160))
            await MainActor.run {
                withAnimation(.spring(response: 0.22, dampingFraction: 0.75)) {
                    iconScale = 1
                }
            }
        }
    }
}

private struct UrgencyChip: View {
    let title: String
    let tint: Color
    
    var body: some View {
        HStack(spacing: 6) {
            Circle()
                .fill(tint)
                .frame(width: 8, height: 8)
            Text(title)
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(.ultraThinMaterial)
        .clipShape(Capsule())
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
            Text(String.localizedStringWithFormat("days_remaining_format".localized, info.daysRemaining))
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
