import SwiftUI
import SwiftData

public struct StackView: View {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \ClientProfile.createdAt) private var clients: [ClientProfile]
    @Query(sort: \UserSupplement.name) private var supplements: [UserSupplement]
    
    @State private var isShowingAddSheet: Bool = false
    @State private var editingSupplement: UserSupplement?
    @State private var isShowingSettingsSheet: Bool = false
    @State private var searchText: String = ""
    @State private var errorMessage: String?
    @State private var isShowingError: Bool = false
    
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
                backgroundGradient
                    .ignoresSafeArea()
                
                List {
                    Section {
                        NavigationLink("sync_center_title".localized) {
                            SyncCenterView(activeClientManager: activeClientManager)
                        }
                        NavigationLink("user_guide_title".localized) {
                            UserGuideView()
                        }
                    }
                    .listRowBackground(glassRowBackground)
                    
                    Section {
                        if supplementsForActiveClient.isEmpty {
                            Text("no_supplements_yet".localized)
                                .foregroundStyle(.secondary)
                        } else {
                            ForEach(supplementsForActiveClient) { supplement in
                                let rowInfo = cycleRowInfo(for: supplement)
                                Button {
                                    editingSupplement = supplement
                                } label: {
                                    StackSupplementRow(
                                        name: displayName(for: supplement),
                                        cycleSummary: rowInfo.summary,
                                        isOffCycle: rowInfo.isOffCycle
                                    )
                                }
                                .buttonStyle(.plain)
                                .swipeActions(edge: .trailing, allowsFullSwipe: false) {
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
                    .listRowBackground(glassRowBackground)
                }
                .scrollContentBackground(.hidden)
                .searchable(text: $searchText, placement: .navigationBarDrawer(displayMode: .always))
                .navigationTitle("my_list_title".localized)
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
                    
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            isShowingSettingsSheet = true
                        } label: {
                            Image(systemName: "gearshape.fill")
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
                .alert("error_title".localized, isPresented: $isShowingError) {
                    Button("ok".localized) { isShowingError = false }
                } message: {
                    Text(errorMessage ?? "")
                }
            }
        }
        .task {
            guard activeClientManager.currentClientId == nil else { return }
            guard let first = clients.first else { return }
            activeClientManager.setCurrentClientId(first.id)
        }
    }
    
    private var glassRowBackground: some View {
        Color.clear.background(.ultraThinMaterial)
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
        let base = supplements
            .filter { $0.deletedAtEpochMs == nil && $0.client?.id == id }
            .sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
        let q = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        if q.isEmpty { return base }
        return base.filter { $0.name.localizedCaseInsensitiveContains(q) }
    }
    
    private var clientTitle: String {
        activeClient?.name ?? "dashboard_title".localized
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
        guard let months = supplement.cycleConfig.durationMonths, months > 0 else {
            return "unlimited".localized
        }
        let calendar = Calendar.current
        guard let endDate = calendar.date(byAdding: .month, value: months, to: supplement.startDate) else {
            return "unlimited".localized
        }
        let formatter = DateFormatter()
        formatter.locale = Locale.current
        formatter.dateStyle = .short
        formatter.timeStyle = .none
        let dateText = formatter.string(from: endDate)
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
        
        Task {
            await notificationService.cancelReminders(for: supplement)
            await CloudSyncAutoSync.syncIfEnabled(modelContext: modelContext, clientId: supplement.client?.id)
        }
    }
}

private struct StackSupplementRow: View, Equatable {
    let name: String
    let cycleSummary: String
    let isOffCycle: Bool
    
    var body: some View {
        VStack(alignment: .leading) {
            Text(name)
                .font(.headline)
                .opacity(isOffCycle ? 0.6 : 1)
            Text(cycleSummary)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }
}
