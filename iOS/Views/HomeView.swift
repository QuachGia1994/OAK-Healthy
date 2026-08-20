import SwiftUI
import SwiftData

/// Màn hình chính Dashboard trên iOS.
public struct HomeView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.colorScheme) private var colorScheme
    @Environment(EntitlementManager.self) private var entitlementManager
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
    @State private var activationProgress = ActivationRetentionStore.progress()
    @AppStorage("oakHomeOverdueCount") private var homeOverdueCount: Int = 0
    @AppStorage("oakLastSyncEpochMs") private var lastSyncEpochMs: Double = 0
    
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
            homeContent
        }
        .task {
            guard activeClientManager.currentClientId == nil else { return }
            guard let first = clients.first else { return }
            activeClientManager.setCurrentClientId(first.id)
        }
        .sheet(isPresented: $isShowingAddClientSheet) {
            ClientEditorSheet(
                title: "add_client".localized,
                initialName: "",
                confirmTitle: "client_create_action".localized
            ) { name in
                guard !name.isEmpty, canCreateClient else {
                    isShowingSettingsSheet = true
                    return
                }
                do {
                    let created = try ClientProfileMutationStore.create(name: name, in: modelContext)
                    activeClientManager.setCurrentClientId(created.id)
                } catch {
                    viewModel.errorMessage = error.localizedDescription
                }
            }
        }
    }

    private var homeContent: some View {
        GeometryReader { proxy in
            let bottomPadding = max(96, proxy.size.height * 0.10)
            ZStack {
                Color.clear.oakBackground()
                
                if clients.isEmpty {
                    emptyStateView
                } else {
                    dashboardView(bottomPadding: bottomPadding)
                }
            }
            .navigationTitle("dashboard_title".localized)
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(OAKPalette.background(for: colorScheme), for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Menu {
                        ForEach(permittedClients) { client in
                            Button(client.name) {
                                activeClientManager.setCurrentClientId(client.id)
                            }
                        }
                        if canCreateClient {
                            Button("add_client".localized) {
                                isShowingAddClientSheet = true
                            }
                        } else {
                            Button("plan_client_limit_reached".localized) {
                                isShowingSettingsSheet = true
                            }
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
                refreshActivationProgress()
                rebuildVisible(now: .now)
            }
            .task(id: ReloadKey(clientId: activeClientManager.currentClientId, syncEpochMs: lastSyncEpochMs)) {
                pruneExpiredSupplementsIfNeeded()
                viewModel.processSupplements(supplementsForActiveClient)
                homeOverdueCount = viewModel.cachedTodayCounts.missed
                refreshActivationProgress()
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
    
    private var permittedClients: [ClientProfile] {
        entitlementManager.maxClients.map { Array(clients.prefix($0)) } ?? clients
    }

    private var canCreateClient: Bool {
        entitlementManager.maxClients.map { clients.count < $0 } ?? true
    }

    private var activeClient: ClientProfile? {
        guard let id = activeClientManager.currentClientId else { return nil }
        return permittedClients.first { $0.id == id }
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

    private var emptyStateView: some View {
        VStack(spacing: 14) {
            Image(systemName: "person.crop.circle.badge.plus")
                .font(.system(size: 30, weight: .semibold))
                .oakSecondaryText()
            Text("add_client_to_start".localized)
                .font(.title3.weight(.semibold))
            Text("settings_guide_1".localized)
                .font(.subheadline)
                .oakSecondaryText()
                .multilineTextAlignment(.center)
            Button("add_client".localized) {
                isShowingAddClientSheet = true
            }
            .buttonStyle(.borderedProminent)
        }
        .padding(20)
        .oakCardStyle(.paper, cornerRadius: OAKRadius.md)
        .padding(.horizontal, 24)
        .accessibilityElement(children: .combine)
    }

    private var firstValueCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("activation_first_value_title".localized)
                .font(.subheadline.weight(.semibold))
            Text(String(format: "activation_progress_format".localized, activationProgress.coreCompletedCount, 3))
                .font(.caption)
                .foregroundStyle(.secondary)
            activationMilestoneRow(.clientReady, key: "activation_milestone_client")
            activationMilestoneRow(.routineReady, key: "activation_milestone_routine")
            activationMilestoneRow(.firstAction, key: "activation_milestone_first_action")
            if activationProgress.nextCoreMilestone == .routineReady {
                Button("activation_add_routine_action".localized) { isShowingAddSheet = true }
                    .buttonStyle(.borderedProminent)
            } else if activationProgress.nextCoreMilestone == .firstAction {
                Button("activation_review_today_action".localized) { doseFilter = .all }
                    .buttonStyle(.bordered)
            }
            Text("activation_pressure_free_hint".localized)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .padding(14)
        .oakCardStyle(.paper, cornerRadius: OAKRadius.md)
    }

    private func activationMilestoneRow(_ milestone: ActivationMilestone, key: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: activationProgress.completed.contains(milestone) ? "checkmark.circle.fill" : "circle")
                .foregroundStyle(activationProgress.completed.contains(milestone) ? Color.accentColor : Color.secondary)
            Text(key.localized).font(.subheadline)
        }
    }

    private func actionableEmptyRow(
        title: String,
        body: String,
        action: String,
        onAction: @escaping () -> Void
    ) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.subheadline.weight(.semibold))
            Text(body).font(.caption).foregroundStyle(.secondary)
            Button(action, action: onAction).buttonStyle(.bordered)
        }
        .padding(12)
        .oakCardStyle(.paper, cornerRadius: OAKRadius.md)
        .listRowBackground(Color.clear)
        .listRowSeparator(.hidden)
        .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
    }

    private func dashboardView(bottomPadding: CGFloat) -> some View {
        let now = renderNow
        let overdue = cachedOverdue
        return VStack(spacing: 0) {
            HomeSummaryPanel(
                filter: $doseFilter,
                counts: viewModel.cachedTodayCounts,
                streakDays: viewModel.cachedStreakDays
            )
            .padding(.horizontal, OAKSpacing.lg)
            .padding(.top, OAKSpacing.md)

            if !activationProgress.firstValueReached {
                firstValueCard
                    .padding(.horizontal, 16)
                    .padding(.top, 10)
            }

            List {
                if viewModel.activeSupplements.isEmpty {
                    if supplementsForActiveClient.isEmpty {
                        actionableEmptyRow(
                            title: "activation_no_routine_title".localized,
                            body: "activation_no_routine_body".localized,
                            action: "activation_add_routine_action".localized
                        ) { isShowingAddSheet = true }
                    } else {
                        Text("activation_rest_day_body".localized)
                            .oakSecondaryText()
                            .listRowBackground(Color.clear)
                            .listRowSeparator(.hidden)
                            .listRowInsets(EdgeInsets(top: 10, leading: 16, bottom: 10, trailing: 16))
                    }
                }
                
                if doseFilter == .overdue {
                    Section {
                        if overdue.isEmpty {
                            actionableEmptyRow(
                                title: "home_no_overdue".localized,
                                body: "activation_no_overdue_body".localized,
                                action: "activation_show_all_action".localized
                            ) { doseFilter = .all }
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
                                    .oakSecondaryText()
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 8)
                                    .background(OAKPalette.mutedSurface(for: colorScheme))
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
                                        Task {
                                            await viewModel.deleteSupplement(
                                                info.supplement,
                                                context: modelContext,
                                                notificationService: notificationService
                                            )
                                        }
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
                                        Task {
                                            await viewModel.deleteSupplement(
                                                info.supplement,
                                                context: modelContext,
                                                notificationService: notificationService
                                            )
                                        }
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
            .safeAreaPadding(.bottom, bottomPadding)
            .listStyle(.plain)
        }
    }

    private func pruneExpiredSupplementsIfNeeded(today: Date = .now) {
        let expired = supplementsForActiveClient.filter { isExpired($0, today: today) }
        guard !expired.isEmpty else { return }
        let now = Int64(today.timeIntervalSince1970 * 1_000)
        do {
            try SupplementHistoryMutationStore.softDelete(expired, at: now, in: modelContext)
        } catch {
            viewModel.errorMessage = error.localizedDescription
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
                Task {
                    await viewModel.deleteDoseTime(
                        supplement,
                        timeString: timeString,
                        context: modelContext,
                        notificationService: notificationService
                    )
                }
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
                Task {
                    await viewModel.deleteDoseTime(
                        supplement,
                        timeString: timeString,
                        context: modelContext,
                        notificationService: notificationService
                    )
                }
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
    
    private func refreshActivationProgress() {
        let localSupplements = supplementsForActiveClient
        let hasAction = localSupplements.contains { supplement in
            supplement.lastTakenLocalDate != nil || !supplement.intakeRecords.isEmpty
        }
        activationProgress = ActivationRetentionStore.reconcile(
            clientReady: activeClientManager.currentClientId != nil,
            routineReady: !localSupplements.isEmpty,
            firstAction: hasAction,
            reminderReady: false
        )
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

    private struct ReloadKey: Hashable {
        let clientId: UUID?
        let syncEpochMs: Double
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

private struct HomeSummaryPanel: View {
    @Binding var filter: HomeDoseFilter
    let counts: HomeViewModel.TodayCounts
    let streakDays: Int
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        VStack(alignment: .leading, spacing: OAKSpacing.lg) {
            HStack(alignment: .center) {
                Text("today_intake_title".localized)
                    .font(.oakDisplay(size: OAKTypeScale.sectionTitle))
                Spacer()
                StreakChip(streakDays: streakDays)
            }
            HStack(alignment: .lastTextBaseline, spacing: OAKSpacing.sm) {
                Text(recordedCount, format: .number)
                    .font(.oakDisplay(size: OAKTypeScale.heroNumber))
                    .monospacedDigit()
                Text(String.localizedStringWithFormat("home_summary_recorded_format".localized, totalCount))
                    .font(.subheadline)
                    .oakSecondaryText()
            }
            ProgressView(value: progress)
                .tint(OAKPalette.taken(for: colorScheme))
            metricGrid
            recoveryContent
            filterHint
        }
        .padding(OAKSpacing.xl)
        .oakCardStyle(.paper, cornerRadius: OAKRadius.lg)
    }

    @ViewBuilder
    private var metricGrid: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: OAKSpacing.sm) {
                metric(.due, count: counts.due, tint: OAKPalette.due(for: colorScheme))
                metric(.overdue, count: counts.missed, tint: OAKPalette.missed(for: colorScheme))
                metric(.taken, count: counts.taken, tint: OAKPalette.taken(for: colorScheme))
                metric(.skipped, count: counts.skipped, tint: OAKPalette.skipped(for: colorScheme))
            }
            VStack(spacing: OAKSpacing.sm) {
                HStack(spacing: OAKSpacing.sm) {
                    metric(.due, count: counts.due, tint: OAKPalette.due(for: colorScheme))
                    metric(.overdue, count: counts.missed, tint: OAKPalette.missed(for: colorScheme))
                }
                HStack(spacing: OAKSpacing.sm) {
                    metric(.taken, count: counts.taken, tint: OAKPalette.taken(for: colorScheme))
                    metric(.skipped, count: counts.skipped, tint: OAKPalette.skipped(for: colorScheme))
                }
            }
        }
    }

    @ViewBuilder
    private var recoveryContent: some View {
        if counts.missed > 0 {
            Divider()
            HStack(spacing: OAKSpacing.md) {
                VStack(alignment: .leading, spacing: OAKSpacing.xs) {
                    Text("recovery_title".localized).font(.subheadline.weight(.semibold))
                    Text(String.localizedStringWithFormat("recovery_body_format".localized, counts.missed))
                        .font(.caption)
                        .oakSecondaryText()
                    Text("recovery_pressure_free_hint".localized)
                        .font(.caption2)
                        .oakSecondaryText()
                }
                Spacer(minLength: OAKSpacing.sm)
                Button("recovery_review_action".localized) { filter = .overdue }
                    .buttonStyle(.borderless)
            }
        }
    }

    @ViewBuilder
    private var filterHint: some View {
        let other = max(0, totalCount - selectedCount)
        if filter != .all, other > 0 {
            Text(String.localizedStringWithFormat("home_filter_hint_format".localized, other))
                .font(.caption)
                .oakSecondaryText()
        }
    }

    private var totalCount: Int { counts.due + counts.missed + counts.taken + counts.skipped }
    private var recordedCount: Int { counts.taken + counts.skipped }
    private var progress: Double { totalCount == 0 ? 0 : Double(recordedCount) / Double(totalCount) }

    private var selectedCount: Int {
        switch filter {
        case .all: totalCount
        case .due: counts.due
        case .overdue: counts.missed
        case .taken: counts.taken
        case .skipped: counts.skipped
        }
    }

    private func metric(_ item: HomeDoseFilter, count: Int, tint: Color) -> some View {
        HomeMetricButton(
            title: item.title,
            count: count,
            tint: tint,
            isSelected: filter == item,
            onTap: { filter = filter == item ? .all : item }
        )
    }
}

private struct HomeMetricButton: View {
    let title: String
    let count: Int
    let tint: Color
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: OAKSpacing.xs) {
                HStack(spacing: 6) {
                    Circle().fill(tint).frame(width: 7, height: 7)
                    Text(title)
                        .font(.caption2.weight(.medium))
                        .oakSecondaryText()
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                }
                Text(count, format: .number)
                    .font(.oakDisplay(size: OAKTypeScale.metric))
                    .foregroundStyle(tint)
                    .monospacedDigit()
            }
            .padding(.horizontal, OAKSpacing.sm)
            .padding(.vertical, OAKSpacing.md)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                isSelected ? tint.opacity(0.10) : Color.clear,
                in: RoundedRectangle(cornerRadius: OAKRadius.md, style: .continuous)
            )
            .contentShape(RoundedRectangle(cornerRadius: OAKRadius.md, style: .continuous))
        }
        .buttonStyle(.plain)
        .oakTouchTarget()
        .accessibilityLabel("\(title), \(count)")
        .accessibilityAddTraits(isSelected ? .isSelected : [])
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

private struct StreakChip: View {
    let streakDays: Int
    
    var body: some View {
        Text(streakDays > 0
             ? String.localizedStringWithFormat("home_rhythm_days_format".localized, streakDays)
             : "home_rhythm_fresh_start".localized)
            .font(.caption)
            .oakSecondaryText()
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .overlay(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .stroke(.secondary.opacity(0.20), lineWidth: 0.75)
            )
    }
}

/// Thành phần hiển thị chất đang hoạt động.
private struct ActiveSupplementRow: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let supplement: UserSupplement
    let timeString: String
    let status: HomeViewModel.DoseStatus
    let urgency: HomeViewModel.DoseUrgency
    let onAction: (UserSupplement, String, HomeViewModel.DoseAction, ModelContext) -> Void
    @State private var isShowingActions = false
    @State private var iconScale: CGFloat = 1
    
    var body: some View {
        HStack(spacing: 12) {
            Capsule()
                .fill(statusAccent)
                .frame(width: 4, height: 48)
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
                .oakSecondaryText()
                
                if status == .missed {
                    Text("dose_status_missed".localized)
                        .font(.caption)
                        .foregroundStyle(.red)
                } else if status == .skipped {
                    Text("dose_status_skipped".localized)
                        .font(.caption)
                        .foregroundStyle(.orange)
                } else if urgency == .dueSoon {
                    UrgencyChip(title: "home_due_soon".localized, tint: OAKPalette.due(for: colorScheme))
                } else if urgency == .missedSoon {
                    UrgencyChip(title: "home_almost_missed".localized, tint: OAKPalette.missed(for: colorScheme))
                }
                
                if let instruction = supplement.instruction, !instruction.isEmpty {
                    Text(instruction.localized)
                        .font(.caption)
                        .italic()
                        .oakSecondaryText()
                        .padding(.top, 2)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            Spacer()
            Button {
                guard status != .taken, status != .skipped else { return }
                if reduceMotion {
                    isShowingActions = true
                } else {
                    withAnimation(.snappy) { isShowingActions = true }
                }
            } label: {
                Image(systemName: symbolName(for: status))
                    .foregroundStyle(symbolColor(for: status))
                    .font(.title2)
                    .scaleEffect(iconScale)
                    .animation(reduceMotion ? nil : .snappy, value: status)
                    .frame(width: 44, height: 44)
                    .contentShape(Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(symbolAccessibilityLabel(for: status))
        }
        .padding()
        .oakCardStyle(.paper, cornerRadius: OAKRadius.md)
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(borderColor, lineWidth: borderWidth)
                .allowsHitTesting(false)
        )
        .animation(reduceMotion ? nil : .snappy, value: urgency)
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
            OAKPalette.taken(for: colorScheme)
        case .skipped:
            OAKPalette.skipped(for: colorScheme)
        case .missed:
            OAKPalette.missed(for: colorScheme)
        }
    }

    private var statusAccent: Color {
        switch status {
        case .taken: OAKPalette.taken(for: colorScheme)
        case .skipped: OAKPalette.skipped(for: colorScheme)
        case .missed: OAKPalette.missed(for: colorScheme)
        case .planned: urgency == .dueSoon ? OAKPalette.due(for: colorScheme) : Color.secondary.opacity(0.24)
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
        case .dueSoon: OAKPalette.due(for: colorScheme).opacity(0.42)
        case .missedSoon: OAKPalette.missed(for: colorScheme).opacity(0.42)
        }
    }
    
    private var borderWidth: CGFloat {
        urgency == .none ? 0 : 1
    }
    
    @MainActor
    private func pulseIcon() {
        guard !reduceMotion else {
            iconScale = 1
            return
        }
        withAnimation(.spring(response: 0.22, dampingFraction: 0.7)) {
            iconScale = 1.18
        }
        Task {
            do {
                try await Task.sleep(for: .milliseconds(160))
            } catch {
                return
            }
            await MainActor.run {
                withAnimation(.spring(response: 0.22, dampingFraction: 0.7)) {
                    iconScale = 1
                }
            }
        }
    }
}

private struct UrgencyChip: View {
    let title: String
    let tint: Color
    @Environment(\.colorScheme) private var colorScheme
    
    var body: some View {
        HStack(spacing: 6) {
            Circle()
                .fill(tint)
                .frame(width: 8, height: 8)
            Text(title)
                .font(.caption2)
                .oakSecondaryText()
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(OAKPalette.mutedSurface(for: colorScheme))
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
                    .oakTertiaryText()
                Text("resting_title".localized)
                    .font(.caption)
                    .oakSecondaryText()
            }
            Spacer()
            Text(String.localizedStringWithFormat("days_remaining_format".localized, info.daysRemaining))
                .font(.caption)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(.secondary.opacity(0.2))
                .clipShape(Capsule())
        }
        .padding()
        .oakCardStyle(.paper, cornerRadius: OAKRadius.md)
    }
}

#Preview {
    HomeView(activeClientManager: ActiveClientManager(), notificationService: NotificationService())
        .environment(EntitlementManager())
        .modelContainer(for: [ClientProfile.self, UserSupplement.self, IntakeRecord.self], inMemory: true)
}
