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
                        .background(.ultraThinMaterial)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                        .shadow(color: .black.opacity(0.12), radius: 12, x: 0, y: 6)
                        
                        VStack(alignment: .leading) {
                            Text("log_details".localized)
                                .font(.headline)
                            
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
                    }
                    .padding()
                }
            }
            .navigationTitle("history_title".localized)
            .task(id: activeClientManager.currentClientId) {
                await reload()
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
            sections = []
            viewModel.processHistory(records: [])
            return
        }
        do {
            var descriptor = FetchDescriptor<IntakeRecord>(
                predicate: #Predicate { $0.supplement?.client?.id == clientId },
                sortBy: [SortDescriptor(\IntakeRecord.date, order: .reverse)]
            )
            descriptor.fetchLimit = 5_000
            let fetched = try modelContext.fetch(descriptor)
            recordsCount = fetched.count
            sections = makeSections(records: fetched)
            viewModel.processHistory(records: fetched)
        } catch {
            recordsCount = 0
            sections = []
            viewModel.processHistory(records: [])
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
                            supplementName: record.supplement?.name ?? "not_available".localized
                        )
                    }
                return HistorySectionModel(date: item.date, title: title, rows: rows)
            }
    }
}

/// Dòng hiển thị chi tiết nhật ký.
private struct HistoryRow: View, Equatable {
    let row: HistoryRowModel
    
    var body: some View {
        HStack {
            Text(row.timeText)
                .font(.caption)
                .foregroundStyle(.secondary)
                .frame(width: 64, alignment: .leading)
            
            Text(row.supplementName)
                .font(.body)
                .fontWeight(.medium)
            Spacer()
            Image(systemName: "checkmark.seal.fill")
                .foregroundStyle(.green)
        }
        .padding()
        .background(.ultraThinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .shadow(color: .black.opacity(0.10), radius: 10, x: 0, y: 5)
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
}

#Preview {
    HistoryView(activeClientManager: ActiveClientManager())
        .modelContainer(for: [ClientProfile.self, UserSupplement.self, IntakeRecord.self], inMemory: true)
}
