import SwiftUI
import Charts
import SwiftData

/// Màn hình lịch sử uống với biểu đồ (iOS).
public struct HistoryView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.colorScheme) private var colorScheme
    @Environment(EntitlementManager.self) private var entitlementManager
    @AppStorage("oakLastSyncEpochMs") private var lastSyncEpochMs: Double = 0
    @State private var viewModel = HistoryViewModel()
    @State private var sections: [HistorySectionModel] = []
    @State private var recordsCount: Int = 0
    @State private var allRecords: [IntakeRecord] = []
    @State private var searchText: String = ""
    @State private var filter: HistoryFilter = .all
    @State private var rebuildTask: Task<Void, Never>? = nil
    @State private var isShowingSettingsSheet: Bool = false
    @State private var isLoadingHistory: Bool = true
    @State private var historyLoadFailed: Bool = false
    @State private var reloadVersion: Int = 0
    
    public let activeClientManager: ActiveClientManager
    
    public init(activeClientManager: ActiveClientManager) {
        self.activeClientManager = activeClientManager
    }
    
    public var body: some View {
        NavigationStack {
            ZStack {
                Color.clear.oakBackground()
                
                ScrollView {
                    VStack(alignment: .leading, spacing: OAKSpacing.section) {
                        if entitlementManager.canUse(.adherenceAnalytics) {
                            InsightsTrendCard(
                                trend7: viewModel.trend7,
                                trend30: viewModel.trend30,
                                insights7: viewModel.insights7,
                                insights30: viewModel.insights30
                            )
                        } else {
                            NavigationLink {
                                PlanAccessView()
                            } label: {
                                Label("plan_unlock_analytics".localized, systemImage: "lock.fill")
                                    .frame(maxWidth: .infinity)
                            }
                            .buttonStyle(.bordered)
                        }

                        VStack(alignment: .leading, spacing: 14) {
                            Text("intake_frequency_last_7".localized)
                                .font(.system(size: OAKTypeScale.sectionTitle, weight: .semibold))
                            
                            Chart {
                                ForEach(viewModel.weeklyData) { data in
                                    BarMark(
                                        x: .value("chart_axis_day".localized, data.date, unit: .day),
                                        y: .value("chart_axis_count".localized, data.count)
                                    )
                                    .foregroundStyle(OAKPalette.accent)
                                    .cornerRadius(4)
                                }
                            }
                            .frame(height: 168)
                            .chartXAxis {
                                AxisMarks(values: .stride(by: .day)) { _ in
                                    AxisValueLabel(format: .dateTime.weekday(.abbreviated))
                                }
                            }
                            .chartYAxis {
                                AxisMarks(position: .leading) { value in
                                    AxisGridLine()
                                    AxisTick()
                                    if let v = value.as(Int.self) {
                                        AxisValueLabel("\(v)")
                                    } else {
                                        AxisValueLabel()
                                    }
                                }
                            }
                        }
                        .padding(.vertical, OAKSpacing.sm)
                        
                        VStack(alignment: .leading, spacing: 14) {
                            HStack(alignment: .firstTextBaseline) {
                                Text("log_details".localized)
                                    .font(.system(size: OAKTypeScale.sectionTitle, weight: .semibold))
                                Spacer()
                                Text(String.localizedStringWithFormat("history_results_count_format".localized, visibleRecordCount))
                                    .font(.caption)
                                    .oakSecondaryText()
                            }
                            HistoryFilterBar(searchText: $searchText, filter: $filter)
                            
                            if activeClientManager.currentClientId == nil {
                                OAKFeedbackView(
                                    title: "client_management".localized,
                                    message: "add_client_to_start".localized,
                                    actionTitle: "settings_title".localized,
                                    action: { isShowingSettingsSheet = true }
                                )
                            } else if recordsCount == 0 {
                                OAKFeedbackView(
                                    title: "history_empty_title".localized,
                                    message: "history_empty_body".localized
                                )
                            } else if sections.isEmpty {
                                OAKFeedbackView(
                                    title: "history_no_matches_title".localized,
                                    message: "history_no_matches_body".localized
                                )
                            } else {
                                LazyVStack(alignment: .leading, spacing: OAKSpacing.xl) {
                                    ForEach(sections) { section in
                                        VStack(alignment: .leading, spacing: 0) {
                                            HStack {
                                                Text(section.title)
                                                    .font(.subheadline.weight(.semibold))
                                                Spacer()
                                                Text(section.rows.count, format: .number)
                                                    .font(.caption)
                                                    .oakSecondaryText()
                                            }
                                            .padding(.vertical, OAKSpacing.md)
                                            
                                            ForEach(section.rows) { row in
                                                HistoryRow(row: row)
                                                    .equatable()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .frame(maxWidth: 760)
                    .padding(.horizontal, OAKSpacing.lg)
                    .padding(.top, OAKSpacing.sm)
                    .padding(.bottom, 96)
                    .frame(maxWidth: .infinity)
                }
                .opacity(isLoadingHistory || historyLoadFailed ? 0 : 1)
                .overlay {
                    if isLoadingHistory {
                        ProgressView()
                    } else if historyLoadFailed {
                        OAKFeedbackView(
                            title: "history_load_failed_title".localized,
                            message: "history_load_failed_body".localized,
                            actionTitle: "retry".localized,
                            action: { reloadVersion += 1 }
                        )
                        .padding(24)
                    }
                }
                .scrollIndicators(.hidden)
                .scrollDismissesKeyboard(.interactively)
            }
            .navigationTitle("history_title".localized)
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(OAKPalette.background(for: colorScheme), for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        isShowingSettingsSheet = true
                    } label: {
                        Image(systemName: "gearshape.fill")
                            .accessibilityLabel("settings_title".localized)
                    }
                }
            }
            .sheet(isPresented: $isShowingSettingsSheet) {
                SettingsView(activeClientManager: activeClientManager)
            }
            .task(id: ReloadKey(
                clientId: activeClientManager.currentClientId,
                syncEpochMs: lastSyncEpochMs,
                plan: entitlementManager.snapshot.plan,
                reloadVersion: reloadVersion
            )) {
                isLoadingHistory = true
                historyLoadFailed = false
                DebugReporter.report("history_task_start", fields: [
                    "has_client": String(activeClientManager.currentClientId != nil)
                ])
                await reload()
            }
            .onChange(of: searchText) {
                scheduleSearchRebuild()
            }
            .onChange(of: filter) {
                rebuildSections()
            }
            .onDisappear {
                rebuildTask?.cancel()
            }
        }
    }

    @MainActor
    private func reload() async {
        guard let clientId = activeClientManager.currentClientId else {
            clearHistoryState()
            isLoadingHistory = false
            historyLoadFailed = false
            DebugReporter.report("history_reload_no_client")
            return
        }
        DebugReporter.report("history_reload_start", fields: ["has_client": "true"])
        do {
            let records = try fetchRecentHistory(clientId: clientId)
            applyHistory(records)
            isLoadingHistory = false
            historyLoadFailed = false
            DebugReporter.report("history_reload_success")
        } catch {
            clearHistoryState()
            isLoadingHistory = false
            historyLoadFailed = true
            DebugReporter.report("history_reload_failed", fields: [
                "error_type": String(describing: type(of: error))
            ])
        }
    }

    @MainActor
    private func fetchRecentHistory(clientId: UUID) throws -> [IntakeRecord] {
        let cutoff = Calendar.current.date(
            byAdding: .day,
            value: -(entitlementManager.historyDays - 1),
            to: .now
        ) ?? .now
        return try ClientScopedStore.recentHistoryRecords(
            modelContext: modelContext,
            clientId: clientId,
            cutoff: cutoff,
            limit: 5_000
        )
    }

    private func applyHistory(_ records: [IntakeRecord]) {
        recordsCount = records.count
        allRecords = records
        rebuildSections()
        viewModel.processHistory(records: records)
    }

    private func clearHistoryState() {
        recordsCount = 0
        allRecords = []
        sections = []
        viewModel.processHistory(records: [])
    }

    @MainActor
    private func rebuildSections() {
        sections = makeSections(records: filteredRecords(from: allRecords))
    }

    @MainActor
    private func scheduleSearchRebuild() {
        rebuildTask?.cancel()
        rebuildTask = Task {
            do { try await Task.sleep(for: .milliseconds(250)) } catch { return }
            await MainActor.run { rebuildSections() }
        }
    }
    
    private func filteredRecords(from records: [IntakeRecord]) -> [IntakeRecord] {
        let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        return records.filter { record in
            if filter != .all {
                if filter == .taken && record.status != IntakeStatus.taken.rawValue { return false }
                if filter == .skipped && record.status != IntakeStatus.skipped.rawValue { return false }
            }
            if query.isEmpty { return true }
            let name = record.supplement?.name ?? ""
            return name.localizedCaseInsensitiveContains(query)
        }
    }
    
    private func makeSections(records: [IntakeRecord]) -> [HistorySectionModel] {
        HistorySectionBuilder.makeSections(records: records)
    }

    private var visibleRecordCount: Int {
        sections.reduce(0) { $0 + $1.rows.count }
    }

    private struct ReloadKey: Hashable {
        let clientId: UUID?
        let syncEpochMs: Double
        let plan: CommercialPlan
        let reloadVersion: Int
    }
}

private enum InsightsWindow: Int, CaseIterable, Hashable {
    case days7 = 7
    case days30 = 30

    var title: String {
        switch self {
        case .days7: return "insights_last_7".localized
        case .days30: return "insights_last_30".localized
        }
    }
}

private struct InsightsTrendCard: View {
    let trend7: [InsightsTrendPoint]
    let trend30: [InsightsTrendPoint]
    let insights7: InsightsSummary?
    let insights30: InsightsSummary?

    @State private var window: InsightsWindow = .days30
    @State private var isDetailsPresented: Bool = false
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let summary = window == .days7 ? insights7 : insights30
        let trend = window == .days7 ? trend7 : trend30
        let total = (summary?.takenCount ?? 0) + (summary?.skippedCount ?? 0)
        let completion = Int(((summary?.completionRate ?? 0) * 100).rounded())
        let lateCount = summary?.lateCount ?? 0

        VStack(alignment: .leading, spacing: OAKSpacing.lg) {
            insightsHeader(enabled: summary != nil)
            windowPicker
            HistoryCompletionOverview(completion: completion, total: total, late: lateCount)
            Text(signalText(completion: completion, late: lateCount))
                .font(.caption)
                .oakSecondaryText()
            trendChart(trend)
            if summary == nil {
                Text("insights_no_data".localized)
                    .font(.caption)
                    .oakSecondaryText()
            }
        }
        .padding(OAKSpacing.xl)
        .frame(maxWidth: .infinity)
        .oakCardStyle(.paper, cornerRadius: OAKRadius.lg)
        .sheet(isPresented: $isDetailsPresented) {
            InsightsDetailsView(summary: summary)
        }
    }

    private func insightsHeader(enabled: Bool) -> some View {
        HStack {
            Text("insights_title".localized)
                .font(.oakDisplay(size: OAKTypeScale.sectionTitle))
            Spacer()
            Button { if enabled { isDetailsPresented = true } } label: {
                Image(systemName: "chevron.right")
                    .foregroundStyle(.secondary.opacity(enabled ? 0.9 : 0.35))
            }
            .buttonStyle(.plain)
            .oakTouchTarget()
        }
    }

    private var windowPicker: some View {
        Picker("", selection: $window) {
            ForEach(InsightsWindow.allCases, id: \.self) { item in
                Text(item.title).tag(item)
            }
        }
        .pickerStyle(.segmented)
        .tint(OAKPalette.taken(for: colorScheme))
    }

    private func signalText(completion: Int, late: Int) -> String {
        String.localizedStringWithFormat(
            "history_signal_window_format".localized,
            window == .days7 ? 7 : 30,
            completion,
            late
        )
    }

    private func trendChart(_ trend: [InsightsTrendPoint]) -> some View {
        Chart {
            ForEach(trend) { point in
                LineMark(
                    x: .value("chart_axis_day".localized, point.date, unit: .day),
                    y: .value("taken".localized, point.takenCount)
                )
                .interpolationMethod(.catmullRom)
                .foregroundStyle(OAKPalette.taken(for: colorScheme))
                .lineStyle(StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round))
            }
            ForEach(trend) { point in
                LineMark(
                    x: .value("chart_axis_day".localized, point.date, unit: .day),
                    y: .value("dose_status_skipped".localized, point.skippedCount)
                )
                .interpolationMethod(.catmullRom)
                .foregroundStyle(OAKPalette.skipped(for: colorScheme))
                .lineStyle(StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
            }
        }
        .chartXAxis(.hidden)
        .chartYAxis(.hidden)
        .chartLegend(.hidden)
        .frame(height: 140)
    }
}

private struct HistoryCompletionOverview: View {
    let completion: Int
    let total: Int
    let late: Int

    var body: some View {
        HStack(spacing: OAKSpacing.xl) {
            HistoryCompletionRing(completion: completion)
            VStack(alignment: .leading, spacing: OAKSpacing.lg) {
                HistoryMetric(label: "history_metric_recorded".localized, value: total.formatted(.number))
                HistoryMetric(label: "history_metric_late".localized, value: late.formatted(.number))
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

private struct HistoryCompletionRing: View {
    let completion: Int
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let progress = min(max(Double(completion) / 100, 0), 1)
        ZStack {
            Circle()
                .stroke(OAKPalette.mutedSurface(for: colorScheme), lineWidth: 9)
            Circle()
                .trim(from: 0, to: progress)
                .stroke(
                    OAKPalette.taken(for: colorScheme),
                    style: StrokeStyle(lineWidth: 9, lineCap: .round)
                )
                .rotationEffect(.degrees(-90))
            VStack(spacing: 2) {
                Text("\(completion)%")
                    .font(.oakDisplay(size: OAKTypeScale.heroNumber))
                    .monospacedDigit()
                Text("history_completion_title".localized)
                    .font(.caption2)
                    .oakSecondaryText()
            }
        }
        .frame(width: 112, height: 112)
        .accessibilityLabel("\("history_completion_title".localized), \(completion)%")
    }
}

private struct HistoryMetric: View {
    let label: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: OAKSpacing.xs) {
            Text(label).font(.caption).oakSecondaryText()
            Text(value)
                .font(.oakDisplay(size: OAKTypeScale.metric))
                .monospacedDigit()
        }
    }
}

private struct InsightsDetailsView: View {
    @Environment(\.dismiss) private var dismiss
    let summary: InsightsSummary?

    var body: some View {
        NavigationStack {
            List {
                if let summary {
                    insightsSection(title: "insights_details_top_late_title".localized, items: summary.topLate)
                    if let hour = summary.topLateHour {
                        insightsSection(title: "insights_details_top_late_hour_title".localized, items: [hour])
                    }
                    insightsSection(title: "insights_details_top_skipped_title".localized, items: summary.topSkipped)
                } else {
                    Text("insights_no_data".localized)
                        .oakSecondaryText()
                }
            }
            .navigationTitle("insights_details_title".localized)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("cancel".localized) { dismiss() }
                }
            }
        }
    }

    @ViewBuilder
    private func insightsSection(title: String, items: [InsightsItem]) -> some View {
        Section(title) {
            if items.isEmpty {
                Text("insights_no_data".localized)
                    .oakSecondaryText()
            } else {
                ForEach(items) { item in
                    Text(String.localizedStringWithFormat("insights_item_bullet_format".localized, item.title, item.count))
                }
            }
        }
    }
}

/// Dòng hiển thị chi tiết nhật ký.
private struct HistoryRow: View, Equatable {
    let row: HistoryRowModel
    @Environment(\.colorScheme) private var colorScheme
    
    var body: some View {
        let isSkipped = row.status == IntakeStatus.skipped.rawValue
        let accent = isSkipped ? OAKPalette.skipped(for: colorScheme) : OAKPalette.taken(for: colorScheme)
        VStack(spacing: 0) {
            HStack(spacing: OAKSpacing.md) {
                Circle().fill(accent).frame(width: 8, height: 8)
                Text(row.timeText)
                    .font(.caption)
                    .monospacedDigit()
                    .oakSecondaryText()
                    .frame(width: 48, alignment: .leading)
                Text(row.supplementName)
                    .font(.callout.weight(.medium))
                Spacer()
                Image(systemName: isSkipped ? "xmark.circle.fill" : "checkmark.circle.fill")
                    .foregroundStyle(accent)
                    .font(.system(size: 20))
            }
            .padding(.vertical, OAKSpacing.md)
            Divider().overlay(OAKPalette.divider(for: colorScheme))
        }
        .accessibilityElement(children: .combine)
    }

    nonisolated static func == (lhs: HistoryRow, rhs: HistoryRow) -> Bool {
        lhs.row == rhs.row
    }
}

private enum HistoryFilter: String, CaseIterable, Hashable {
    case all
    case taken
    case skipped
    
    var title: String {
        switch self {
        case .all: "filter_all".localized
        case .taken: "notif_action_taken".localized
        case .skipped: "dose_status_skipped".localized
        }
    }
}

private struct HistoryFilterBar: View {
    @Binding var searchText: String
    @Binding var filter: HistoryFilter
    
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        VStack(alignment: .leading, spacing: OAKSpacing.md) {
            HStack(spacing: OAKSpacing.sm) {
                Image(systemName: "magnifyingglass")
                    .oakSecondaryText()
                TextField("history_search_placeholder".localized, text: $searchText)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            }
            .padding(.horizontal, OAKSpacing.md)
            .frame(minHeight: 48)
            .background(
                OAKPalette.mutedSurface(for: colorScheme),
                in: RoundedRectangle(cornerRadius: OAKRadius.md, style: .continuous)
            )
            Picker("", selection: $filter) {
                ForEach(HistoryFilter.allCases, id: \.self) { item in
                    Text(item.title).tag(item)
                }
            }
            .pickerStyle(.segmented)
            .tint(OAKPalette.taken(for: colorScheme))
        }
    }
}

struct HistorySectionModel: Identifiable, Equatable {
    var id: Date { date }
    let date: Date
    let title: String
    let rows: [HistoryRowModel]
}

struct HistoryRowModel: Identifiable, Equatable {
    let id: UUID
    let timeText: String
    let supplementName: String
    let status: String
}

enum HistorySectionBuilder {
    static func makeSections(records: [IntakeRecord], calendar: Calendar = .current) -> [HistorySectionModel] {
        let headerFormatter = HistoryFormatters.dayHeader
        let timeFormatter = HistoryFormatters.timeShort
        var sections: [HistorySectionModel] = []
        var currentDay: Date? = nil
        var currentRows: [HistoryRowModel] = []
        for record in records {
            let day = calendar.startOfDay(for: record.date)
            if currentDay != nil && day != currentDay {
                if let currentDay {
                    sections.append(HistorySectionModel(date: currentDay, title: sectionTitle(for: currentDay, calendar: calendar, formatter: headerFormatter), rows: currentRows))
                }
                currentRows = []
            }
            currentDay = day
            currentRows.append(HistoryRowModel(
                id: record.id,
                timeText: timeFormatter.string(from: record.date),
                supplementName: record.supplement?.name ?? "not_available".localized,
                status: record.status
            ))
        }
        if let currentDay {
            sections.append(HistorySectionModel(date: currentDay, title: sectionTitle(for: currentDay, calendar: calendar, formatter: headerFormatter), rows: currentRows))
        }
        return sections
    }

    private static func sectionTitle(for day: Date, calendar: Calendar, formatter: DateFormatter) -> String {
        if calendar.isDateInToday(day) { return "history_today".localized }
        if calendar.isDateInYesterday(day) { return "history_yesterday".localized }
        return formatter.string(from: day).sentenceCapitalized()
    }
}

private extension String {
    func sentenceCapitalized() -> String {
        guard let first = first else { return self }
        let firstUpper = String(first).uppercased(with: Locale.autoupdatingCurrent)
        return firstUpper + dropFirst()
    }
}

enum HistoryFormatters {
    static let dayHeader: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale.autoupdatingCurrent
        formatter.dateStyle = .long
        formatter.timeStyle = .none
        return formatter
    }()

    static let timeShort: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale.autoupdatingCurrent
        formatter.dateStyle = .none
        formatter.timeStyle = .short
        return formatter
    }()
}

#Preview {
    HistoryView(activeClientManager: ActiveClientManager())
        .environment(EntitlementManager())
        .modelContainer(for: [ClientProfile.self, UserSupplement.self, IntakeRecord.self], inMemory: true)
}
