import SwiftUI

public struct NotificationDebugScreen: View {
    @State private var isLoading = true
    @State private var entries: [NotificationDebugEntry] = []
    
    public init() {}
    
    public var body: some View {
        Group {
            if isLoading {
                ProgressView()
            } else {
                NotificationDebugView(entries: entries)
            }
        }
        .navigationTitle("Danh sách thông báo")
        .task {
            guard isLoading else { return }
            let raw = await NotificationService().shadowScheduledTimes()
            entries = NotificationDebugEntry.parseMany(raw)
            isLoading = false
        }
    }
}

public struct NotificationDebugView: View {
    let entries: [NotificationDebugEntry]
    
    public init(entries: [NotificationDebugEntry]) {
        self.entries = entries
    }
    
    public var body: some View {
        List {
            if entries.isEmpty {
                Text("Chưa có mốc giờ nào. Vui lòng bật 'Cho phép gửi thông báo' để kích hoạt.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(groupedKeys, id: \.self) { day in
                    Section(dayHeader(day)) {
                        ForEach(grouped[day] ?? []) { item in
                            NotificationRow(entry: item)
                        }
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
    }
    
    private var grouped: [Date: [NotificationDebugEntry]] {
        let calendar = Calendar.current
        let groups = Dictionary(grouping: entries) { calendar.startOfDay(for: $0.scheduledAt) }
        return groups.mapValues { $0.sorted { $0.scheduledAt < $1.scheduledAt } }
    }
    
    private var groupedKeys: [Date] {
        grouped.keys.sorted()
    }
    
    private func dayHeader(_ day: Date) -> Text {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "vi_VN")
        formatter.dateFormat = "EEEE, dd/MM/yyyy"
        return Text(formatter.string(from: day))
    }
}

private struct NotificationRow: View {
    let entry: NotificationDebugEntry
    
    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "pills.fill")
                .foregroundStyle(.blue)
            VStack(alignment: .leading, spacing: 2) {
                Text(entry.name)
                    .font(.headline)
                Text(entry.dose)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Text(timeText)
                .font(.subheadline)
                .fontWeight(.semibold)
        }
        .padding(.vertical, 4)
    }
    
    private var timeText: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        return formatter.string(from: entry.scheduledAt)
    }
}

public struct NotificationDebugEntry: Identifiable, Hashable {
    public let id: String
    public let name: String
    public let dose: String
    public let scheduledAt: Date
    
    static func parseMany(_ raw: [String]) -> [NotificationDebugEntry] {
        raw.compactMap(parseOne).sorted { $0.scheduledAt < $1.scheduledAt }
    }
    
    private static func parseOne(_ raw: String) -> NotificationDebugEntry? {
        let parts = raw.components(separatedBy: "||")
        if parts.count >= 3, let date = parseDate(parts[2]) {
            let name = parts[0].trimmingCharacters(in: .whitespacesAndNewlines)
            let dose = parts[1].trimmingCharacters(in: .whitespacesAndNewlines)
            return NotificationDebugEntry(id: raw, name: name, dose: dose, scheduledAt: date)
        }
        
        let legacy = raw.components(separatedBy: " | ")
        guard legacy.count >= 2, let date = parseDate(legacy[1]) else { return nil }
        let name = legacy[0].trimmingCharacters(in: .whitespacesAndNewlines)
        return NotificationDebugEntry(id: raw, name: name, dose: "", scheduledAt: date)
    }
    
    private static func parseDate(_ raw: String) -> Date? {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm"
        return formatter.date(from: raw.trimmingCharacters(in: .whitespacesAndNewlines))
    }
}

