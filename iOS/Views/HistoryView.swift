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
                            Text("intake_frequency_last_7")
                                .font(.headline)
                            
                            Chart {
                                ForEach(viewModel.weeklyData) { data in
                                    BarMark(
                                        x: .value(String(localized: "chart_axis_day"), data.date, unit: .day),
                                        y: .value(String(localized: "chart_axis_count"), data.count)
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
                            Text("log_details")
                                .font(.headline)
                            
                            if records.isEmpty {
                                Text("no_logs_yet")
                                    .foregroundStyle(.secondary)
                            } else {
                                ForEach(records) { record in
                                    HistoryRow(record: record)
                                }
                            }
                        }
                    }
                    .padding()
                }
            }
            .navigationTitle("history_title")
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
}

/// Dòng hiển thị chi tiết nhật ký.
private struct HistoryRow: View {
    let record: IntakeRecord
    
    var body: some View {
        HStack {
            VStack(alignment: .leading) {
                Text(record.supplement?.name ?? String(localized: "not_available"))
                    .font(.body)
                    .fontWeight(.medium)
                Text(record.date.formatted(date: .abbreviated, time: .shortened))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
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
