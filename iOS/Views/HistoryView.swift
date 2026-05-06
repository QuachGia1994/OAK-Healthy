import SwiftUI
import Charts
import SwiftData

/// Màn hình lịch sử uống với biểu đồ (iOS).
public struct HistoryView: View {
    @Environment(\.colorScheme) private var colorScheme
    @Query(sort: [SortDescriptor(\IntakeRecord.date, order: .reverse)])
    private var allRecords: [IntakeRecord]
    @State private var viewModel = HistoryViewModel()
    
    public let activeClientManager: ActiveClientManager
    
    public init(activeClientManager: ActiveClientManager) {
        self.activeClientManager = activeClientManager
    }
    
    private var records: [IntakeRecord] {
        guard let currentClientId = activeClientManager.currentClientId else { return [] }
        return allRecords.filter { $0.supplement?.client?.id == currentClientId }
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
                            
                            if records.isEmpty {
                                Text("no_logs_yet".localized)
                                    .foregroundStyle(.secondary)
                            } else {
                                LazyVStack(alignment: .leading, spacing: 12) {
                                    ForEach(groupedRecords, id: \.date) { section in
                                        VStack(alignment: .leading, spacing: 8) {
                                            Text(sectionTitle(for: section.date))
                                                .font(.headline)
                                                .frame(maxWidth: .infinity, alignment: .leading)
                                                .padding(.horizontal, 12)
                                                .padding(.vertical, 8)
                                                .background(.secondary.opacity(0.12))
                                                .clipShape(RoundedRectangle(cornerRadius: 12))
                                            
                                            ForEach(section.records) { record in
                                                HistoryRow(record: record)
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
            .onAppear {
                viewModel.processHistory(records: records)
            }
            .onChange(of: records) {
                viewModel.processHistory(records: records)
            }
            .onChange(of: activeClientManager.currentClientId) {
                viewModel.processHistory(records: records)
            }
        }
    }
    
    private var backgroundGradient: LinearGradient {
        let colors: [Color] = colorScheme == .dark
            ? [Color(red: 0.08, green: 0.0, blue: 0.15), .black]
            : [Color(.systemGroupedBackground), Color(.systemBackground)]
        return LinearGradient(colors: colors, startPoint: .topLeading, endPoint: .bottomTrailing)
    }
    
    private var groupedRecords: [(date: Date, records: [IntakeRecord])] {
        let calendar = Calendar.current
        let grouped = Dictionary(grouping: records) { record in
            calendar.startOfDay(for: record.date)
        }
        
        return grouped
            .map { (date: $0.key, records: $0.value.sorted { $0.date > $1.date }) }
            .sorted { $0.date > $1.date }
    }
    
    private func sectionTitle(for date: Date) -> String {
        let calendar = Calendar.current
        
        if calendar.isDateInToday(date) {
            return isVietnamese ? "Hôm nay" : "Today"
        }
        
        if calendar.isDateInYesterday(date) {
            return isVietnamese ? "Hôm qua" : "Yesterday"
        }
        
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: isVietnamese ? "vi_VN" : "en_US")
        formatter.dateStyle = .long
        formatter.timeStyle = .none
        return formatter.string(from: date)
    }
    
    private var isVietnamese: Bool {
        guard let preferredLanguage = Locale.preferredLanguages.first else { return false }
        return preferredLanguage.hasPrefix("vi")
    }
}

/// Dòng hiển thị chi tiết nhật ký.
private struct HistoryRow: View {
    let record: IntakeRecord
    
    var body: some View {
        HStack {
            Text(record.date.formatted(date: .omitted, time: .shortened))
                .font(.caption)
                .foregroundStyle(.secondary)
                .frame(width: 64, alignment: .leading)
            
            Text(record.supplement?.name ?? "not_available".localized)
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

#Preview {
    HistoryView(activeClientManager: ActiveClientManager())
        .modelContainer(for: [ClientProfile.self, UserSupplement.self, IntakeRecord.self], inMemory: true)
}
