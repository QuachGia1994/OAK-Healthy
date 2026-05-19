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
        .navigationTitle("notification_debug_title".localized)
        .task {
            guard isLoading else { return }
            let items = await NotificationService().pendingRequestSnapshots()
            entries = NotificationDebugEntry.parseMany(items)
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
                Text("notification_debug_empty".localized)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(groupedKeys, id: \.self) { day in
                    Section(header: Text(dayHeader(day))) {
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
    
    private func dayHeader(_ day: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale.current
        formatter.dateFormat = "EEEE, dd/MM/yyyy"
        return formatter.string(from: day)
    }
}

private struct NotificationRow: View {
    let entry: NotificationDebugEntry
    
    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: iconName)
                .foregroundStyle(.tint)
                .font(.title3)
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(entry.name)
                        .font(.headline)
                    Spacer()
                    Text(timeText)
                        .font(.subheadline)
                        .fontWeight(.semibold)
                }
                Text(detailText)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
    }
    
    private var timeText: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        return formatter.string(from: entry.scheduledAt)
    }
    
    private var iconName: String {
        iconFor(substanceName: entry.name)
    }
    
    private var detailText: String {
        let dose = entry.dose.trimmingCharacters(in: .whitespacesAndNewlines)
        let cycle = entry.cycleText.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedDose = dose.isEmpty ? "—" : dose
        let doseText = String(format: "notification_debug_dose_format".localized, resolvedDose)
        guard !cycle.isEmpty else { return doseText }
        return "\(doseText) • \(cycle)"
    }
    
    private func iconFor(substanceName: String) -> String {
        let name = substanceName.lowercased()
        if name.contains("caffeine") { return "bolt.fill" }
        if name.contains("creatine") { return "dumbbell.fill" }
        if name.contains("omega") { return "drop.fill" }
        if name.contains("vitamin d3") || name.contains("d3") { return "sun.max.fill" }
        if name.contains("magnesium") || name.contains("zinc") || name.contains("coq10") { return "pills.fill" }
        if name.contains("nac") { return "leaf.fill" }
        return "cross.case.fill"
    }
}

public struct NotificationDebugEntry: Identifiable, Hashable {
    public let id: String
    public let name: String
    public let dose: String
    public let cycleText: String
    public let scheduledAt: Date
    
    static func parseMany(_ items: [PendingNotificationSnapshot]) -> [NotificationDebugEntry] {
        items.compactMap(parseOne).sorted { $0.scheduledAt < $1.scheduledAt }
    }
    
    private static func parseOne(_ item: PendingNotificationSnapshot) -> NotificationDebugEntry? {
        let name = item.title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { return nil }
        return NotificationDebugEntry(
            id: item.id,
            name: name,
            dose: item.dosage,
            cycleText: item.cycle,
            scheduledAt: item.scheduledAt
        )
    }
    
    static func parseMany(_ raw: [String]) -> [NotificationDebugEntry] {
        raw.compactMap(parseOne).sorted { $0.scheduledAt < $1.scheduledAt }
    }
    
    private static func parseOne(_ raw: String) -> NotificationDebugEntry? {
        let parts = raw.components(separatedBy: "||")
        if parts.count >= 4, let date = parseDate(parts[3]) {
            let name = parts[0].trimmingCharacters(in: .whitespacesAndNewlines)
            let dose = parts[1].trimmingCharacters(in: .whitespacesAndNewlines)
            let cycleText = parts[2].trimmingCharacters(in: .whitespacesAndNewlines)
            return NotificationDebugEntry(id: raw, name: name, dose: dose, cycleText: cycleText, scheduledAt: date)
        }
        
        if parts.count >= 3, let date = parseDate(parts[2]) {
            let name = parts[0].trimmingCharacters(in: .whitespacesAndNewlines)
            let dose = parts[1].trimmingCharacters(in: .whitespacesAndNewlines)
            return NotificationDebugEntry(id: raw, name: name, dose: dose, cycleText: "", scheduledAt: date)
        }
        
        let legacy = raw.components(separatedBy: " | ")
        guard legacy.count >= 2, let date = parseDate(legacy[1]) else { return nil }
        let name = legacy[0].trimmingCharacters(in: .whitespacesAndNewlines)
        return NotificationDebugEntry(id: raw, name: name, dose: "", cycleText: "", scheduledAt: date)
    }
    
    private static func parseDate(_ raw: String) -> Date? {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm"
        return formatter.date(from: raw.trimmingCharacters(in: .whitespacesAndNewlines))
    }
}
