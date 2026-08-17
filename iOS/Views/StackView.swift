import SwiftUI
import SwiftData

public struct StackView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.colorScheme) private var colorScheme
    @Environment(EntitlementManager.self) private var entitlementManager
    @Query(sort: \ClientProfile.createdAt) private var clients: [ClientProfile]
    @Query(sort: \UserSupplement.name) private var supplements: [UserSupplement]
    
    @State private var isShowingAddSheet: Bool = false
    @State private var editingSupplement: UserSupplement?
    @State private var isShowingSettingsSheet: Bool = false
    @State private var searchText: String = ""
    @State private var visibleSupplements: [UserSupplement] = []
    @State private var hasLoadedVisibleSupplements: Bool = false
    @State private var errorMessage: String?
    @State private var isShowingError: Bool = false
    @State private var selectedDestination: StackDestination?
    
    public let activeClientManager: ActiveClientManager
    public let notificationService: NotificationService
    
    private let cycleEngine = CycleCalculator()
    
    public init(activeClientManager: ActiveClientManager, notificationService: NotificationService) {
        self.activeClientManager = activeClientManager
        self.notificationService = notificationService
    }
    
    public var body: some View {
        NavigationStack {
            ZStack {
                Color.clear.oakBackground()
                
                List {
                    Section {
                        StackOverviewCard(
                            totalCount: displayedSupplements.count,
                            restingCount: restingCount
                        )
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                        .listRowInsets(EdgeInsets(top: 12, leading: 16, bottom: 8, trailing: 16))

                        HStack(spacing: 0) {
                            Button {
                                selectedDestination = .syncCenter
                            } label: {
                                StackQuickAction(
                                    title: "sync_center_title".localized,
                                    systemImage: "arrow.triangle.2.circlepath"
                                )
                            }
                            .buttonStyle(.plain)
                            Divider()
                            Button {
                                selectedDestination = .userGuide
                            } label: {
                                StackQuickAction(
                                    title: "user_guide_title".localized,
                                    systemImage: "book.closed.fill"
                                )
                            }
                            .buttonStyle(.plain)
                        }
                        .background(
                            OAKPalette.mutedSurface(for: colorScheme),
                            in: RoundedRectangle(cornerRadius: OAKRadius.md, style: .continuous)
                        )
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                        .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 12, trailing: 16))
                    }
                    
                    Section {
                        if displayedSupplements.isEmpty {
                            StackEmptyState(onAdd: { isShowingAddSheet = true })
                                .listRowBackground(Color.clear)
                                .listRowSeparator(.hidden)
                                .listRowInsets(EdgeInsets(top: 10, leading: 16, bottom: 10, trailing: 16))
                        } else {
                            ForEach(displayedSupplements) { supplement in
                                let rowInfo = cycleRowInfo(for: supplement)
                                StackSupplementRow(
                                    name: displayName(for: supplement),
                                    cycleSummary: rowInfo.summary,
                                    isOffCycle: rowInfo.isOffCycle
                                )
                                .equatable()
                                .listRowBackground(Color.clear)
                                .listRowSeparator(.visible)
                                .listRowInsets(EdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 16))
                                .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                                    Button {
                                        editingSupplement = supplement
                                    } label: {
                                        Label("edit".localized, systemImage: "pencil")
                                    }
                                    Button(role: .destructive) {
                                        deleteSupplement(supplement)
                                    } label: {
                                        Label("delete".localized, systemImage: "trash")
                                    }
                                }
                            }
                        }
                    } header: {
                        Text("my_list_title".localized)
                    }
                }
                .scrollContentBackground(.hidden)
                .scrollIndicators(.hidden)
                .scrollDismissesKeyboard(.interactively)
                .safeAreaPadding(.bottom, 128)
                .searchable(text: $searchText, placement: .navigationBarDrawer(displayMode: .always))
                .navigationTitle("my_list_title".localized)
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
                .navigationDestination(item: $selectedDestination) { destination in
                    destinationView(for: destination)
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
                .alert("error_title".localized, isPresented: $isShowingError) {
                    Button("ok".localized) { isShowingError = false }
                } message: {
                    Text(errorMessage ?? "")
                }
                .onChange(of: supplements) {
                    pruneExpiredSupplementsIfNeeded()
                    refreshVisibleSupplements()
                }
                .onChange(of: searchText) {
                    refreshVisibleSupplements()
                }
                .task(id: activeClientManager.currentClientId) {
                    pruneExpiredSupplementsIfNeeded()
                    refreshVisibleSupplements()
                }
            }
        }
        .task {
            guard activeClientManager.currentClientId == nil else { return }
            guard let first = clients.first else { return }
            activeClientManager.setCurrentClientId(first.id)
        }
    }

    @ViewBuilder
    private func destinationView(for destination: StackDestination) -> some View {
        switch destination {
        case .syncCenter:
            if entitlementManager.canUse(.encryptedCloudSync) {
                SyncCenterView(activeClientManager: activeClientManager)
            } else {
                PlanAccessView()
            }
        case .userGuide:
            UserGuideView()
        }
    }
    
    private var permittedClients: [ClientProfile] {
        entitlementManager.maxClients.map { Array(clients.prefix($0)) } ?? clients
    }

    private var activeClient: ClientProfile? {
        guard let id = activeClientManager.currentClientId else { return nil }
        return permittedClients.first { $0.id == id }
    }
    
    private var displayedSupplements: [UserSupplement] {
        guard hasLoadedVisibleSupplements else { return makeVisibleSupplements() }
        return visibleSupplements
    }

    private func refreshVisibleSupplements() {
        visibleSupplements = makeVisibleSupplements()
        hasLoadedVisibleSupplements = true
    }

    private func makeVisibleSupplements() -> [UserSupplement] {
        guard let id = activeClientManager.currentClientId else { return [] }
        let base = supplements
            .filter { $0.deletedAtEpochMs == nil && $0.client?.id == id && !isExpired($0) }
            .sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
        let q = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        if q.isEmpty { return base }
        return base.filter { $0.name.localizedCaseInsensitiveContains(q) }
    }
    
    private var clientTitle: String {
        activeClient?.name ?? "dashboard_title".localized
    }

    private var restingCount: Int {
        displayedSupplements.reduce(into: 0) { count, supplement in
            if cycleRowInfo(for: supplement).isOffCycle { count += 1 }
        }
    }

    private func pruneExpiredSupplementsIfNeeded(today: Date = .now) {
        guard let id = activeClientManager.currentClientId else { return }
        let expired = supplements.filter {
            $0.deletedAtEpochMs == nil &&
            $0.client?.id == id &&
            isExpired($0, today: today)
        }
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

    private func isExpired(_ supplement: UserSupplement, today: Date = .now) -> Bool {
        guard let days = supplement.cycleConfig.durationMonths, days > 0 else { return false }
        let calendar = Calendar.current
        guard let endDate = calendar.date(byAdding: .day, value: days, to: supplement.startDate) else { return false }
        return calendar.startOfDay(for: today) >= calendar.startOfDay(for: endDate)
    }
    
    private func displayName(for supplement: UserSupplement) -> String {
        let time = supplement.intakeTime.trimmingCharacters(in: .whitespacesAndNewlines)
        return time.isEmpty ? supplement.name : "\(supplement.name) (\(time))"
    }
    
    private func cycleRowInfo(for supplement: UserSupplement) -> (summary: String, isOffCycle: Bool) {
        let config = supplement.cycleConfig
        let durationText = cycleDurationText(for: supplement)
        if config.isContinuous {
            if let interval = config.intervalDays, interval > 1 {
                return ("\("repeat_every_n_days".localized): \(interval) • \(durationText)", false)
            }
            return ("\( "cycle_continuous".localized) • \(durationText)", false)
        }
        let status = try? cycleEngine.determineStatus(for: supplement.startDate, config: config, at: .now)
        let statusText = status == .on ? "cycle_status_on".localized : "cycle_status_off".localized
        let cycleText = String(format: "cycle_summary_format".localized, statusText, config.daysOn, config.daysOff)
        return ("\(cycleText) • \(durationText)", status == .off)
    }

    private func cycleDurationText(for supplement: UserSupplement) -> String {
        guard let days = supplement.cycleConfig.durationMonths, days > 0 else {
            return "unlimited".localized
        }
        let calendar = Calendar.current
        guard let endDate = calendar.date(byAdding: .day, value: days, to: supplement.startDate) else {
            return "unlimited".localized
        }
        let dateText = endDate.formatted(date: .numeric, time: .omitted)
        return String(format: "cycle_until_format".localized, dateText)
    }
    
    private func deleteSupplement(_ supplement: UserSupplement) {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        supplement.deletedAtEpochMs = now
        supplement.updatedAtEpochMs = now
        do {
            try modelContext.save()
        } catch {
            errorMessage = error.localizedDescription
            isShowingError = true
            return
        }
        
        refreshVisibleSupplements()
        Task {
            await notificationService.cancelReminders(for: supplement)
            CloudSyncAutoSync.requestSyncSoon(modelContext: modelContext, clientId: supplement.client?.id)
        }
    }
}

private enum StackDestination: Hashable, Identifiable {
    case syncCenter
    case userGuide

    var id: Self { self }
}

private struct StackSupplementRow: View, Equatable {
    let name: String
    let cycleSummary: String
    let isOffCycle: Bool
    @Environment(\.colorScheme) private var colorScheme
    
    var body: some View {
        let accent = isOffCycle ? Color.secondary.opacity(0.55) : OAKPalette.taken(for: colorScheme)
        HStack(spacing: OAKSpacing.md) {
            Circle().fill(accent).frame(width: 8, height: 8)
            VStack(alignment: .leading, spacing: OAKSpacing.xs) {
                Text(name)
                    .font(.headline)
                    .foregroundStyle(isOffCycle ? Color.secondary : Color.primary)
                Text(cycleSummary)
                    .font(.caption)
                    .oakSecondaryText()
                    .lineLimit(2)
            }
            Spacer(minLength: 8)
            Image(systemName: isOffCycle ? "moon.zzz.fill" : "checkmark.circle.fill")
                .foregroundStyle(accent)
                .accessibilityHidden(true)
        }
        .padding(.vertical, OAKSpacing.md)
        .accessibilityElement(children: .combine)
    }

    nonisolated static func == (lhs: StackSupplementRow, rhs: StackSupplementRow) -> Bool {
        lhs.name == rhs.name && lhs.cycleSummary == rhs.cycleSummary && lhs.isOffCycle == rhs.isOffCycle
    }
}

private struct StackOverviewCard: View {
    let totalCount: Int
    let restingCount: Int

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("my_list_title".localized)
                .font(.subheadline.weight(.semibold))
                .oakSecondaryText()
            Text(totalCount, format: .number)
                .font(.oakDisplay(size: 52))
                .foregroundStyle(.primary)
                .monospacedDigit()
            HStack(spacing: 28) {
                StackMetric(title: "cycle_status_on".localized, value: max(0, totalCount - restingCount))
                StackMetric(title: "cycle_status_off".localized, value: restingCount)
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .oakCardStyle(.paper, cornerRadius: 16)
        .accessibilityElement(children: .combine)
    }
}

private struct StackMetric: View {
    let title: String
    let value: Int

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value, format: .number)
                .font(.title3.weight(.semibold))
                .monospacedDigit()
            Text(title)
                .font(.caption)
                .oakSecondaryText()
                .lineLimit(1)
                .minimumScaleFactor(0.75)
        }
    }
}

private struct StackQuickAction: View {
    let title: String
    let systemImage: String

    var body: some View {
        HStack(spacing: OAKSpacing.sm) {
            Image(systemName: systemImage)
                .foregroundStyle(OAKPalette.accent)
            Text(title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.primary)
                .lineLimit(1)
                .minimumScaleFactor(0.75)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, OAKSpacing.md)
        .oakTouchTarget()
        .frame(maxWidth: .infinity)
    }
}

private struct StackEmptyState: View {
    let onAdd: () -> Void

    var body: some View {
        OAKFeedbackView(
            title: "no_supplements_yet".localized,
            message: "add_supplement".localized,
            actionTitle: "add_supplement".localized,
            action: onAdd
        )
    }
}
