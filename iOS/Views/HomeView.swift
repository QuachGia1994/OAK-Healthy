import SwiftUI
import SwiftData

/// Màn hình chính Dashboard trên iOS.
public struct HomeView: View {
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \ClientProfile.createdAt) private var clients: [ClientProfile]
    @Query(sort: \UserSupplement.name) private var supplements: [UserSupplement]
    
    @State private var viewModel = HomeViewModel()
    @State private var updateService = UpdateService()
    @State private var isShowingAddSheet = false
    @State private var editingSupplement: UserSupplement?
    @State private var isShowingAddClientSheet = false
    @State private var isShowingSettingsSheet = false
    @State private var doseFilter: HomeDoseFilter = .all
    @State private var renderNow: Date = .now
    @State private var cachedOverdue: [OverdueItem] = []
    @State private var cachedTimeSections: [TimeSection] = []
    @AppStorage("oakHomeOverdueCount") private var homeOverdueCount: Int = 0
    
    public let activeClientManager: ActiveClientManager
    public let notificationService: NotificationService
    
    private let refreshTimer = Timer.publish(every: 30, on: .main, in: .common).autoconnect()
    
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
                Color.clear.oakBackground()
                
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
                    .oakCardStyle(.glass, cornerRadius: 16)
                    .padding(.horizontal, 24)
                } else {
                    let now = renderNow
                    let overdue = cachedOverdue
                    List {
                        Section {
                            TodayHeaderView(
                                title: "today_intake_title".localized,
                                streakDays: viewModel.cachedStreakDays
                            )
                            .listRowBackground(Color.clear)
                            .listRowSeparator(.hidden)
                            .listRowInsets(EdgeInsets(top: 14, leading: 16, bottom: 8, trailing: 16))

                            HomeDoseFilterBar(filter: $doseFilter, counts: viewModel.cachedTodayCounts)
                                .listRowBackground(Color.clear)
                                .listRowSeparator(.hidden)
                                .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 6, trailing: 16))
                            
                            if viewModel.activeSupplements.isEmpty {
                                Text("no_intake_today".localized)
                                    .foregroundStyle(.secondary)
                                    .listRowBackground(Color.clear)
                                    .listRowSeparator(.hidden)
                                    .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                            }
                        }
                        
                        if doseFilter == .overdue {
                            Section {
                                if overdue.isEmpty {
                                    Text("home_no_overdue".localized)
                                        .foregroundStyle(.secondary)
                                        .listRowBackground(Color.clear)
                                        .listRowSeparator(.hidden)
                                        .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                                } else {
                                    ForEach(overdue) { item in
                                        activeRow(supplement: item.supplement, timeString: item.timeString, now: now)
                                    }
                                }
                            } header: {
                                Text("\("dose_status_missed".localized) (\(overdue.count))")
                                    .textCase(nil)
                            }
                        } else if !overdue.isEmpty {
                            Section {
                                ForEach(overdue) { item in
                                    activeRow(supplement: item.supplement, timeString: item.timeString, now: now)
                                }
                            } header: {
                                Text("\("dose_status_missed".localized) (\(overdue.count))")
                                    .textCase(nil)
                            }
                        }
                        
                        if doseFilter != .overdue {
                            ForEach(cachedTimeSections) { section in
                                Section {
                                    ForEach(section.supplements) { supplement in
                                        activeRow(supplement: supplement, timeString: section.time, now: now)
                                    }
                                } header: {
                                    HStack {
                                        Text(section.time)
                                            .font(.subheadline)
                                            .fontWeight(.semibold)
                                            .foregroundStyle(.secondary)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                            .background(.ultraThinMaterial)
                                            .clipShape(Capsule())
                                        Spacer()
                                    }
                                    .textCase(nil)
                                }
                            }
                        }
                        
                        if !viewModel.restingSupplements.isEmpty, doseFilter == .all {
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
                    .scrollIndicators(.hidden)
                    .scrollDismissesKeyboard(.interactively)
                    .safeAreaPadding(.bottom, 128)
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
                                    .accessibilityLabel("add_supplement".localized)
                            }
                        }

                        ToolbarItem(placement: .topBarTrailing) {
                            Button {
                                isShowingSettingsSheet = true
                            } label: {
                                Image(systemName: "gearshape.fill")
                                    .accessibilityLabel("settings_title".localized)
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
                    .sheet(isPresented: $isShowingSettingsSheet) {
                        SettingsView(activeClientManager: activeClientManager)
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
                    .onChange(of: supplements) {
                        pruneExpiredSupplementsIfNeeded()
                        viewModel.processSupplements(supplementsForActiveClient)
                        homeOverdueCount = viewModel.cachedTodayCounts.missed
                        rebuildVisible(now: .now)
                    }
                    .task(id: activeClientManager.currentClientId) {
                        pruneExpiredSupplementsIfNeeded()
                        viewModel.processSupplements(supplementsForActiveClient)
                        homeOverdueCount = viewModel.cachedTodayCounts.missed
                        rebuildVisible(now: .now)
                    }
                    .onChange(of: doseFilter) {
                        rebuildVisible(now: renderNow)
                    }
                    .onReceive(refreshTimer) { _ in
                        let now = Date.now
                        renderNow = now
                        rebuildVisible(now: now)
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
    
    private var activeClient: ClientProfile? {
        guard let id = activeClientManager.currentClientId else { return nil }
        return clients.first { $0.id == id }
    }

    private var supplementsForActiveClient: [UserSupplement] {
        guard let id = activeClientManager.currentClientId else { return [] }
        return supplements.filter { $0.deletedAtEpochMs == nil && $0.client?.id == id }
    }
    
    private var navigationTitle: String {
        activeClient?.name ?? "dashboard_title".localized
    }
    
    private var clientTitle: String {
        activeClient?.name ?? "dashboard_title".localized
    }

    private func pruneExpiredSupplementsIfNeeded(today: Date = .now) {
        let expired = supplementsForActiveClient.filter { isExpired($0, today: today) }
        guard !expired.isEmpty else { return }
        let now = Int64(today.timeIntervalSince1970 * 1000)
        for supplement in expired {
            supplement.deletedAtEpochMs = now
            supplement.updatedAtEpochMs = now
        }
        do {
            try modelContext.save()
        } catch {
            return
        }
    }

    private func isExpired(_ supplement: UserSupplement, today: Date) -> Bool {
        guard let days = supplement.cycleConfig.durationMonths, days > 0 else { return false }
        let calendar = Calendar.current
        guard let endDate = calendar.date(byAdding: .day, value: days, to: supplement.startDate) else { return false }
        return calendar.startOfDay(for: today) >= calendar.startOfDay(for: endDate)
    }
    
    private func activeRow(supplement: UserSupplement, timeString: String, now: Date) -> some View {
        ActiveSupplementRow(
            supplement: supplement,
            timeString: timeString,
            status: viewModel.doseStatus(supplement, timeString: timeString, now: now),
            urgency: viewModel.doseUrgency(supplement, timeString: timeString, now: now),
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
    
    private func overdueItems(now: Date) -> [OverdueItem] {
        var items: [OverdueItem] = []
        for time in viewModel.activeSupplementTimes {
            for supplement in viewModel.activeSupplements[time] ?? [] {
                if viewModel.doseStatus(supplement, timeString: time, now: now) == .missed {
                    items.append(OverdueItem(supplement: supplement, timeString: time))
                }
            }
        }
        return items
    }
    
    private func rebuildVisible(now: Date) {
        renderNow = now
        cachedOverdue = overdueItems(now: now)
        guard doseFilter != .overdue else {
            cachedTimeSections = []
            return
        }
        
        var sections: [TimeSection] = []
        for time in viewModel.activeSupplementTimes {
            let items = viewModel.activeSupplements[time] ?? []
            let filtered = items.filter { supplement in
                let status = viewModel.doseStatus(supplement, timeString: time, now: now)
                switch doseFilter {
                case .all: return status != .missed
                case .overdue: return status == .missed
                case .due: return viewModel.isDueNow(supplement, timeString: time, now: now)
                case .taken: return status == .taken
                case .skipped: return status == .skipped
                }
            }
            if !filtered.isEmpty {
                sections.append(TimeSection(time: time, supplements: filtered))
            }
        }
        cachedTimeSections = sections
    }
}

private enum HomeDoseFilter: String, CaseIterable, Identifiable {
    case all
    case overdue
    case due
    case taken
    case skipped
    
    var id: String { rawValue }
    
    var title: String {
        switch self {
        case .all: return "filter_all".localized
        case .overdue: return "dose_status_missed".localized
        case .due: return "dose_status_due".localized
        case .taken: return "notif_action_taken".localized
        case .skipped: return "dose_status_skipped".localized
        }
    }
}

private struct HomeDoseFilterBar: View {
    @Binding var filter: HomeDoseFilter
    let counts: HomeViewModel.TodayCounts
    
    var body: some View {
        let columns = [
            GridItem(.flexible(), spacing: 12),
            GridItem(.flexible(), spacing: 12)
        ]
        VStack(alignment: .leading, spacing: 8) {
            LazyVGrid(columns: columns, alignment: .leading, spacing: 12) {
                TodayStripButton(
                    title: "dose_status_due".localized,
                    count: counts.due,
                    tint: .blue,
                    isSelected: filter == .due
                ) {
                    filter = filter == .due ? .all : .due
                }
                TodayStripButton(
                    title: "dose_status_missed".localized,
                    count: counts.missed,
                    tint: .red,
                    isSelected: filter == .overdue
                ) {
                    filter = filter == .overdue ? .all : .overdue
                }
                TodayStripButton(
                    title: "notif_action_taken".localized,
                    count: counts.taken,
                    tint: .green,
                    isSelected: filter == .taken
                ) {
                    filter = filter == .taken ? .all : .taken
                }
                TodayStripButton(
                    title: "dose_status_skipped".localized,
                    count: counts.skipped,
                    tint: .orange,
                    isSelected: filter == .skipped
                ) {
                    filter = filter == .skipped ? .all : .skipped
                }
            }
            let total = counts.due + counts.missed + counts.taken + counts.skipped
            let current = switch filter {
            case .all: total
            case .due: counts.due
            case .overdue: counts.missed
            case .taken: counts.taken
            case .skipped: counts.skipped
            }
            let other = max(0, total - current)
            if filter != .all, other > 0 {
                Text(String.localizedStringWithFormat("home_filter_hint_format".localized, other))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

private struct TodayStripButton: View {
    let title: String
    let count: Int
    let tint: Color
    let isSelected: Bool
    let onTap: () -> Void
    
    var body: some View {
        Button {
            onTap()
        } label: {
            VStack(alignment: .leading, spacing: 6) {
                Text(title)
                    .font(.caption)
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                Text("\(count)")
                    .font(.title3)
                    .fontWeight(.bold)
                    .foregroundStyle(tint)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 12)
            .padding(.vertical, 12)
            .background(.ultraThinMaterial)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(isSelected ? tint.opacity(0.55) : Color.clear, lineWidth: 1)
            )
        }
        .accessibilityLabel("\(title), \(count)")
    }
}

private struct OverdueItem: Identifiable, Hashable {
    let supplement: UserSupplement
    let timeString: String
    var id: String { "\(supplement.id.uuidString)-\(timeString)" }
}

private struct TimeSection: Identifiable {
    let time: String
    let supplements: [UserSupplement]
    var id: String { time }
}

private struct TodayHeaderView: View {
    let title: String
    let streakDays: Int
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.title3)
                .fontWeight(.bold)
            StreakChip(streakDays: streakDays)
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
            .accessibilityLabel(symbolAccessibilityLabel(for: status))
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
                onAction(supplement, timeString, .taken, modelContext)
            }
            Button("notif_action_skip".localized) {
                pulseIcon()
                onAction(supplement, timeString, .skipped, modelContext)
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

    private func symbolAccessibilityLabel(for status: HomeViewModel.DoseStatus) -> String {
        switch status {
        case .planned:
            "mark_as_taken".localized
        case .taken:
            "already_taken".localized
        case .skipped:
            "already_skipped".localized
        case .missed:
            "mark_as_taken".localized
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
