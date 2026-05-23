import SwiftUI
import Charts
import SwiftData

/// Màn hình lịch sử uống với biểu đồ (iOS).
public struct HistoryView: View {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.modelContext) private var modelContext
    @AppStorage("oakLastSyncEpochMs") private var lastSyncEpochMs: Double = 0
    @State private var viewModel = HistoryViewModel()
    @State private var sections: [HistorySectionModel] = []
    @State private var recordsCount: Int = 0
    @State private var allRecords: [IntakeRecord] = []
    @State private var searchText: String = ""
    @State private var filter: HistoryFilter = .all
    @State private var rebuildTask: Task<Void, Never>? = nil
    @State private var isShowingSettingsSheet: Bool = false
    
    public let activeClientManager: ActiveClientManager
    
    public init(activeClientManager: ActiveClientManager) {
        self.activeClientManager = activeClientManager
    }
    
    public var body: some View {
        NavigationStack {
            ZStack {
                backgroundGradient
                    .ignoresSafeArea()
                
                ScrollView {
                    VStack(alignment: .leading, spacing: 24) {
                        InsightsCard(insights7: viewModel.insights7, insights30: viewModel.insights30)
                            .padding()
                            .oakCard()

                        VStack(alignment: .leading) {
                            Text("intake_frequency_last_7".localized)
                                .font(.headline)
                            
                            Chart {
                                ForEach(viewModel.weeklyData) { data in
                                    BarMark(
                                        x: .value("chart_axis_day".localized, data.date, unit: .day),
                                        y: .value("chart_axis_count".localized, data.count)
                                    )
                                    .foregroundStyle(Color.blue.gradient)
                                    .cornerRadius(4)
                                }
                            }
                            .frame(height: 200)
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
                        .padding()
                        .oakCard()
                        
                        VStack(alignment: .leading) {
                            Text("log_details".localized)
                                .font(.headline)
                            
                            HistoryFilterBar(searchText: $searchText, filter: $filter)
                                .padding(.top, 8)
                            
                            if recordsCount == 0 {
                                VStack(spacing: 10) {
                                    Image(systemName: "clock")
                                        .font(.title2)
                                        .foregroundStyle(.secondary)
                                    Text("no_logs_yet".localized)
                                        .foregroundStyle(.secondary)
                                        .multilineTextAlignment(.center)
                                }
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 18)
                            } else {
                                LazyVStack(alignment: .leading, spacing: 12) {
                                    ForEach(sections) { section in
                                        VStack(alignment: .leading, spacing: 8) {
                                            Text(section.title)
                                                .font(.headline)
                                                .frame(maxWidth: .infinity, alignment: .leading)
                                                .padding(.horizontal, 12)
                                                .padding(.vertical, 8)
                                                .background(.secondary.opacity(0.12))
                                                .clipShape(RoundedRectangle(cornerRadius: 12))
                                            
                                            ForEach(section.rows) { row in
                                                HistoryRow(row: row)
                                                    .equatable()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        .padding()
                        .oakCard()
                    }
                    .padding(.horizontal)
                    .padding(.top, 8)
                    .padding(.bottom, 24)
                }
            }
            .navigationTitle("history_title".localized)
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(.ultraThinMaterial, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        isShowingSettingsSheet = true
                    } label: {
                        Image(systemName: "gearshape.fill")
                    }
                }
            }
            .sheet(isPresented: $isShowingSettingsSheet) {
                SettingsView(activeClientManager: activeClientManager)
            }
            .task(id: ReloadKey(clientId: activeClientManager.currentClientId, syncEpochMs: lastSyncEpochMs)) {
                DebugReporter.report("history_task_start", fields: [
                    "clientId": activeClientManager.currentClientId?.uuidString ?? ""
                ])
                await reload()
            }
            .onChange(of: searchText) {
                scheduleSearchRebuild()
            }
            .onChange(of: filter) {
                withAnimation(.snappy) { rebuildSections() }
            }
        }
    }
    
    private var backgroundGradient: LinearGradient {
        let colors: [Color] = colorScheme == .dark
            ? [Color(red: 0.08, green: 0.0, blue: 0.15), .black]
            : [Color(.systemGroupedBackground), Color(.systemBackground)]
        return LinearGradient(colors: colors, startPoint: .topLeading, endPoint: .bottomTrailing)
    }
    
    @MainActor
    private func reload() async {
        guard let clientId = activeClientManager.currentClientId else {
            recordsCount = 0
            allRecords = []
            sections = []
            viewModel.processHistory(records: [])
            DebugReporter.report("history_reload_no_client")
            return
        }
        DebugReporter.report("history_reload_start", fields: [
            "clientId": clientId.uuidString
        ])
        do {
            var descriptor = FetchDescriptor<IntakeRecord>(
                sortBy: [SortDescriptor(\IntakeRecord.date, order: .reverse)]
            )
            descriptor.fetchLimit = 5_000
            let fetched = try modelContext.fetch(descriptor)
            let filtered = fetched.filter { $0.supplement?.client?.id == clientId }
            recordsCount = filtered.count
            allRecords = filtered
            rebuildSections()
            viewModel.processHistory(records: filtered)
            DebugReporter.report("history_reload_success", fields: [
                "count": String(filtered.count)
            ])
        } catch {
            recordsCount = 0
            allRecords = []
            sections = []
            viewModel.processHistory(records: [])
            DebugReporter.report("history_reload_failed", fields: [
                "error": String(describing: error)
            ])
        }
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

    private struct ReloadKey: Hashable {
        let clientId: UUID?
        let syncEpochMs: Double
    }
}

private struct InsightsCard: View {
    let insights7: InsightsSummary?
    let insights30: InsightsSummary?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("insights_title".localized)
                .font(.headline)
            HStack(spacing: 12) {
                InsightsWindowCard(titleKey: "insights_last_7", summary: insights7)
                InsightsWindowCard(titleKey: "insights_last_30", summary: insights30)
            }
        }
    }
}

private struct InsightsWindowCard: View {
    @Environment(\.colorScheme) private var colorScheme
    
    let titleKey: String
    let summary: InsightsSummary?

    var body: some View {
        let base: Color = colorScheme == .dark ? Color.white.opacity(0.10) : Color.white.opacity(0.62)
        let border: Color = colorScheme == .dark ? Color.white.opacity(0.10) : Color.black.opacity(0.06)
        VStack(alignment: .leading, spacing: 8) {
            Text(titleKey.localized)
                .font(.subheadline)
                .fontWeight(.semibold)
            if let summary {
                let completion = Int((summary.completionRate * 100).rounded())
                Text(String.localizedStringWithFormat("insights_completion_format".localized, completion))
                    .font(.caption)
                    .foregroundStyle(.primary)
                Text(String.localizedStringWithFormat("insights_late_format".localized, summary.lateCount))
                    .font(.caption)
                    .foregroundStyle(.primary)
                if let top = summary.topLate.first {
                    Text(String.localizedStringWithFormat("insights_top_late_format".localized, top.title, top.count))
                        .font(.caption)
                        .foregroundStyle(.primary)
                }
                if let hour = summary.topLateHour {
                    Text(String.localizedStringWithFormat("insights_top_late_hour_format".localized, hour.title, hour.count))
                        .font(.caption)
                        .foregroundStyle(.primary)
                }
                if let top = summary.topSkipped.first {
                    Text(String.localizedStringWithFormat("insights_top_skipped_format".localized, top.title, top.count))
                        .font(.caption)
                        .foregroundStyle(.primary)
                }
            } else {
                Text("insights_no_data".localized)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(base)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(border, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

/// Dòng hiển thị chi tiết nhật ký.
private struct HistoryRow: View, Equatable {
    let row: HistoryRowModel
    
    var body: some View {
        let isSkipped = row.status == IntakeStatus.skipped.rawValue
        HStack {
            Text(row.timeText)
                .font(.caption)
                .monospacedDigit()
                .foregroundStyle(.secondary)
                .frame(width: 64, alignment: .leading)
            
            Text(row.supplementName)
                .font(.callout)
                .fontWeight(.medium)
            Spacer()
            Image(systemName: isSkipped ? "xmark.seal.fill" : "checkmark.seal.fill")
                .foregroundStyle(isSkipped ? .orange : .green)
        }
        .padding()
        .background(.ultraThinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .shadow(color: .black.opacity(0.10), radius: 10, x: 0, y: 5)
    }
}

private extension View {
    func oakCard() -> some View {
        oakCardStyle(.glass, cornerRadius: 14, strokeOpacity: 0.0, shadowOpacity: 0.12, shadowRadius: 12, shadowY: 6)
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
    
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            TextField("history_search_placeholder".localized, text: $searchText)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .padding(12)
                .background(.ultraThinMaterial)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            
            Picker("", selection: $filter) {
                ForEach(HistoryFilter.allCases, id: \.self) { item in
                    Text(item.title).tag(item)
                }
            }
            .pickerStyle(.segmented)
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
        return formatter.string(from: day)
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
        .modelContainer(for: [ClientProfile.self, UserSupplement.self, IntakeRecord.self], inMemory: true)
}
