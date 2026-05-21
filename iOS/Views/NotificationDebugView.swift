import SwiftUI
import SwiftData
@preconcurrency import UserNotifications

public struct NotificationDebugScreen: View {
    @Environment(\.modelContext) private var modelContext
    @AppStorage("isNotificationEnabledByUser") private var isNotificationEnabledByUser: Bool = false
    
    @State private var isLoading = true
    @State private var authorizationStatus: UNAuthorizationStatus = .notDetermined
    @State private var pendingEntries: [NotificationDebugEntry] = []
    @State private var shadowEntries: [NotificationDebugEntry] = []
    @State private var message: String?
    
    public let activeClientManager: ActiveClientManager
    
    public init(activeClientManager: ActiveClientManager) {
        self.activeClientManager = activeClientManager
    }
    
    public var body: some View {
        ZStack {
            if isLoading {
                ProgressView()
            } else {
                listContent
            }
        }
        .navigationTitle("notification_debug_title".localized)
        .task {
            guard isLoading else { return }
            await refresh()
        }
    }
    
    private var listContent: some View {
        List {
            Section {
                LabeledContent("Permission") {
                    Text(authorizationStatusText)
                        .foregroundStyle(authorizationStatus == .authorized || authorizationStatus == .provisional ? .primary : .secondary)
                }
                LabeledContent("Enabled (User)") {
                    Text(isNotificationEnabledByUser ? "ON" : "OFF")
                }
                LabeledContent("Pending (OS)") { Text("\(pendingEntries.count)") }
                LabeledContent("Shadow") { Text("\(shadowEntries.count)") }
                
                Button("Refresh") { Task { await refresh() } }
                Button("Clear pending") { Task { await clearPending() } }
                Button("Reschedule (active client)") { Task { await rescheduleForActiveClient() } }
                
                if let message {
                    Text(message)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            
            if groupedItems.isEmpty {
                Text("notification_debug_empty".localized)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(groupedKeys, id: \.self) { key in
                    Section(header: Text(groupHeader(key))) {
                        ForEach(groupedItems[key] ?? []) { item in
                            NotificationRow(entry: item.entry, source: item.source)
                        }
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
    }
    
    private func refresh() async {
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()
        let pending = await NotificationService.shared.pendingRequestSnapshots()
        let shadowRaw = await NotificationService.shared.shadowScheduledTimes()
        await MainActor.run {
            authorizationStatus = settings.authorizationStatus
            pendingEntries = NotificationDebugEntry.parseMany(pending)
            shadowEntries = NotificationDebugEntry.parseMany(shadowRaw)
            message = nil
            isLoading = false
        }
    }
    
    private func clearPending() async {
        await NotificationService.shared.clearAllPendingNotifications()
        await refresh()
    }
    
    private func rescheduleForActiveClient() async {
        guard let clientId = activeClientManager.currentClientId else {
            await MainActor.run { message = "No active client" }
            return
        }
        do {
            let descriptor = FetchDescriptor<UserSupplement>(
                predicate: #Predicate { $0.deletedAtEpochMs == nil && $0.client?.id == clientId },
                sortBy: [SortDescriptor(\UserSupplement.name)]
            )
            let supplements = try modelContext.fetch(descriptor)
            await NotificationService.shared.scheduleAll(supplements: supplements)
            await refresh()
        } catch {
            await MainActor.run { message = error.localizedDescription }
        }
    }
    
    private var authorizationStatusText: String {
        switch authorizationStatus {
        case .authorized: return "authorized"
        case .provisional: return "provisional"
        case .denied: return "denied"
        case .notDetermined: return "notDetermined"
        case .ephemeral: return "ephemeral"
        @unknown default: return "unknown"
        }
    }
    
    fileprivate enum Source: String, Hashable {
        case pending
        case shadow
    }
    
    private struct Item: Identifiable, Hashable {
        let source: Source
        let entry: NotificationDebugEntry
        
        var id: String { "\(source.rawValue)-\(entry.id)" }
    }
    
    private struct GroupKey: Hashable {
        let source: Source
        let day: Date
    }
    
    private var items: [Item] {
        let pending = pendingEntries.map { Item(source: .pending, entry: $0) }
        let shadow = shadowEntries.map { Item(source: .shadow, entry: $0) }
        return (pending + shadow).sorted { $0.entry.scheduledAt < $1.entry.scheduledAt }
    }
    
    private var groupedItems: [GroupKey: [Item]] {
        let calendar = Calendar.current
        let groups = Dictionary(grouping: items) { item in
            GroupKey(source: item.source, day: calendar.startOfDay(for: item.entry.scheduledAt))
        }
        return groups.mapValues { $0.sorted { $0.entry.scheduledAt < $1.entry.scheduledAt } }
    }
    
    private var groupedKeys: [GroupKey] {
        groupedItems.keys.sorted {
            if $0.day != $1.day { return $0.day < $1.day }
            return $0.source.rawValue < $1.source.rawValue
        }
    }
    
    private func groupHeader(_ key: GroupKey) -> String {
        let sourceText = key.source == .pending ? "Pending" : "Shadow"
        return "\(sourceText) • \(dayHeader(key.day))"
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
    let source: NotificationDebugScreen.Source
    
    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: iconName)
                .foregroundStyle(.tint)
                .font(.title3)
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(entry.name).font(.headline)
                    Spacer()
                    Text(timeText).font(.subheadline).fontWeight(.semibold)
                }
                Text(detailText).font(.caption).foregroundStyle(.secondary)
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
        let origin = source == .pending ? "Pending" : "Shadow"
        let dose = entry.dose.trimmingCharacters(in: .whitespacesAndNewlines)
        let cycle = entry.cycleText.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedDose = dose.isEmpty ? "—" : dose
        let doseText = String(format: "notification_debug_dose_format".localized, resolvedDose)
        guard !cycle.isEmpty else { return "\(origin) • \(doseText)" }
        return "\(origin) • \(doseText) • \(cycle)"
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
