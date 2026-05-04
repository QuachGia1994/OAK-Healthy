import SwiftUI
import Charts
import SwiftData

/// Màn hình lịch sử uống với biểu đồ (iOS).
public struct HistoryView: View {
    @Query(sort: \IntakeRecord.date, order: .reverse) private var records: [IntakeRecord]
    @State private var viewModel = HistoryViewModel()
    
    public init() {}
    
    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    // Biểu đồ thống kê tuần
                    VStack(alignment: .leading) {
                        Text("Tần suất uống (7 ngày qua)")
                            .font(.headline)
                        
                        Chart {
                            ForEach(viewModel.weeklyData) { data in
                                BarMark(
                                    x: .value("Ngày", data.date, unit: .day),
                                    y: .value("Số lần", data.count)
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
                    .background(Color(.secondarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    
                    // Danh sách lịch sử chi tiết
                    VStack(alignment: .leading) {
                        Text("Chi tiết nhật ký")
                            .font(.headline)
                        
                        if records.isEmpty {
                            Text("Chưa có nhật ký nào.")
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
            .navigationTitle("Lịch sử")
            .onAppear {
                viewModel.processHistory(records: records)
            }
            .onChange(of: records) {
                viewModel.processHistory(records: records)
            }
        }
    }
}

/// Dòng hiển thị chi tiết nhật ký.
private struct HistoryRow: View {
    let record: IntakeRecord
    
    var body: some View {
        HStack {
            VStack(alignment: .leading) {
                Text(record.supplement?.name ?? "N/A")
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
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

#Preview {
    HistoryView()
        .modelContainer(for: IntakeRecord.self, inMemory: true)
}
