import SwiftUI
import Charts
import SwiftData

/// Màn hình lịch sử uống với biểu đồ (iOS).
public struct HistoryView: View {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.modelContext) private var modelContext
    @State private var viewModel = HistoryViewModel()
    @State private var sections: [HistorySectionModel] = []
    @State private var recordsCount: Int = 0
    @State private var allRecords: [IntakeRecord] = []
    @State private var searchText: String = ""
    @State private var filter: HistoryFilter = .all
    
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
                        }
                        .padding()
                        .oakCard()
                        
                        VStack(alignment: .leading) {
                            Text("log_details".localized)
                                .font(.headline)
                            
                            HistoryFilterBar(searchText: $searchText, filter: $filter)
                                .padding(.top, 8)
                            
                            if recordsCount == 0 {
                                Text("no_logs_yet".localized)
                                    .foregroundStyle(.secondary)
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
            .task(id: activeClientManager.currentClientId) {
                DebugReporter.report("history_task_start", fields: [
                    "clientId": activeClientManager.currentClientId?.uuidString ?? ""
                ])
                await reload()
            }
            .onChange(of: searchText) {
                withAnimation(.snappy) { rebuildSections() }
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
            let fetchedAll = try modelContext.fetch(descriptor)
            let fetched = fetchedAll.filter { $0.supplement?.client?.id == clientId }
            recordsCount = fetched.count
            allRecords = fetched
            rebuildSections()
            viewModel.processHistory(records: fetched)
            DebugReporter.report("history_reload_success", fields: [
                "count": String(fetched.count)
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
    
    private func filteredRecords(from records: [IntakeRecord]) -> [IntakeRecord] {
        let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return records.filter { record in
            if filter != .all {
                if filter == .taken && record.status != IntakeStatus.taken.rawValue { return false }
                if filter == .skipped && record.status != IntakeStatus.skipped.rawValue { return false }
            }
            if query.isEmpty { return true }
            let name = record.supplement?.name.lowercased() ?? ""
            return name.contains(query)
        }
    }
    
    private func makeSections(records: [IntakeRecord]) -> [HistorySectionModel] {
        let isVietnamese = (Locale.preferredLanguages.first ?? "").hasPrefix("vi")
        let calendar = Calendar.current
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: isVietnamese ? "vi_VN" : "en_US")
        formatter.dateStyle = .long
        formatter.timeStyle = .none
        
        let grouped = Dictionary(grouping: records) { calendar.startOfDay(for: $0.date) }
        return grouped
            .map { (date: $0.key, records: $0.value) }
            .sorted { $0.date > $1.date }
            .map { item in
                let title: String = {
                    if calendar.isDateInToday(item.date) { return "history_today".localized }
                    if calendar.isDateInYesterday(item.date) { return "history_yesterday".localized }
                    return formatter.string(from: item.date)
                }()
                let rows = item.records
                    .sorted { $0.date > $1.date }
                    .map { record in
                        HistoryRowModel(
                            id: record.id,
                            timeText: record.date.formatted(date: .omitted, time: .shortened),
                            supplementName: record.supplement?.name ?? "not_available".localized,
                            status: record.status
                        )
                    }
                return HistorySectionModel(date: item.date, title: title, rows: rows)
            }
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
    let titleKey: String
    let summary: InsightsSummary?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(titleKey.localized)
                .font(.subheadline)
                .fontWeight(.semibold)
            if let summary {
                let completion = Int((summary.completionRate * 100).rounded())
                Text(String(format: "insights_completion_format".localized, completion))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text(String(format: "insights_late_format".localized, summary.lateCount))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                if let top = summary.topLate.first {
                    Text(String(format: "insights_top_late_format".localized, top.title, top.count))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                if let hour = summary.topLateHour {
                    Text(String(format: "insights_top_late_hour_format".localized, hour.title, hour.count))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                if let top = summary.topSkipped.first {
                    Text(String(format: "insights_top_skipped_format".localized, top.title, top.count))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            } else {
                Text("insights_no_data".localized)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.secondary.opacity(0.10))
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
        background(.ultraThinMaterial)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .shadow(color: .black.opacity(0.12), radius: 12, x: 0, y: 6)
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

private struct HistorySectionModel: Identifiable, Equatable {
    var id: Date { date }
    let date: Date
    let title: String
    let rows: [HistoryRowModel]
}

private struct HistoryRowModel: Identifiable, Equatable {
    let id: UUID
    let timeText: String
    let supplementName: String
    let status: String
}

#Preview {
    HistoryView(activeClientManager: ActiveClientManager())
        .modelContainer(for: [ClientProfile.self, UserSupplement.self, IntakeRecord.self], inMemory: true)
}
