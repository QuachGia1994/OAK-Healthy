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
    @State private var activeSupplementCount: Int = 0
    @State private var pendingOnlyCount: Int = 0
    @State private var shadowOnlyCount: Int = 0
    @State private var shadowErrorCount: Int = 0
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
                Text(diagnosisTitle)
                    .font(.headline)
                
                Text(diagnosisHint)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                
                LabeledContent("Permission") { Text(authorizationStatusText) }
                LabeledContent("Enabled (User)") { Text(isNotificationEnabledByUser ? "ON" : "OFF") }
                LabeledContent("Active client") { Text(activeClientManager.currentClientId == nil ? "NO" : "YES") }
                LabeledContent("Active supplements") { Text("\(activeSupplementCount)") }
                LabeledContent("Pending (OS)") { Text("\(pendingEntries.count)") }
                LabeledContent("Shadow") { Text("\(shadowEntries.count)") }
                LabeledContent("Pending only") { Text("\(pendingOnlyCount)") }
                LabeledContent("Shadow only") { Text("\(shadowOnlyCount)") }
                LabeledContent("Shadow errors") { Text("\(shadowErrorCount)") }
                
                Button("Refresh") { Task { await refresh() } }
                Button("Clear pending") { Task { await clearPending() } }
                Button("Reschedule (active client)") { Task { await rescheduleForActiveClient() } }
                
                if let message {
                    Text(message)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            
            Section("Diagnostics") {
                Text(diagnosticsText)
                    .font(.system(.footnote, design: .monospaced))
                    .textSelection(.enabled)
                    .foregroundStyle(.secondary)
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
        let parsedPending = NotificationDebugEntry.parseMany(pending)
        let parsedShadow = NotificationDebugEntry.parseMany(shadowRaw)
        let supplementCount = await fetchActiveSupplementCount(clientId: activeClientManager.currentClientId)
        let (pendingOnly, shadowOnly, errorCount) = computeReconciliation(pending: parsedPending, shadow: parsedShadow)
        await MainActor.run {
            authorizationStatus = settings.authorizationStatus
            pendingEntries = parsedPending
            shadowEntries = parsedShadow
            activeSupplementCount = supplementCount
            pendingOnlyCount = pendingOnly
            shadowOnlyCount = shadowOnly
            shadowErrorCount = errorCount
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
            await NotificationService.shared.replaceAllSchedules(supplements: supplements)
            await refresh()
        } catch {
            await MainActor.run { message = error.localizedDescription }
        }
    }
    
    private func computeReconciliation(
        pending: [NotificationDebugEntry],
        shadow: [NotificationDebugEntry]
    ) -> (pendingOnly: Int, shadowOnly: Int, errorCount: Int) {
        let shadowErrors = shadow.filter { $0.isShadowError }
        let shadowValid = shadow.filter { !$0.isShadowError }
        let pendingKeys = Set(pending.map { entryKey($0) })
        let shadowKeys = Set(shadowValid.map { entryKey($0) })
        let pendingOnly = pendingKeys.subtracting(shadowKeys).count
        let shadowOnly = shadowKeys.subtracting(pendingKeys).count
        return (pendingOnly, shadowOnly, shadowErrors.count)
    }
    
    @MainActor
    private func fetchActiveSupplementCount(clientId: UUID?) -> Int {
        guard let clientId else { return 0 }
        do {
            let descriptor = FetchDescriptor<UserSupplement>(
                predicate: #Predicate { $0.deletedAtEpochMs == nil && $0.client?.id == clientId }
            )
            let supplements = try modelContext.fetch(descriptor)
            return supplements.count
        } catch {
            return 0
        }
    }
    
    private func entryKey(_ entry: NotificationDebugEntry) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyyMMddHHmm"
        return "\(entry.id)|\(formatter.string(from: entry.scheduledAt))"
    }
    
    private var diagnosisTitle: String {
        if authorizationStatus == .denied { return "DENIED" }
        if !isNotificationEnabledByUser { return "OFF" }
        if activeClientManager.currentClientId == nil { return "NO ACTIVE CLIENT" }
        if activeSupplementCount == 0 { return "NO SUPPLEMENTS" }
        if pendingEntries.isEmpty { return "SCHEDULED = 0" }
        if pendingOnlyCount > 0 || shadowOnlyCount > 0 || shadowErrorCount > 0 { return "MISMATCH" }
        return "OK"
    }
    
    private var diagnosisHint: String {
        if authorizationStatus == .denied { return "Permission denied. Enable notifications in Settings." }
        if authorizationStatus == .notDetermined { return "Permission not requested yet. Turn ON the toggle to request." }
        if !isNotificationEnabledByUser { return "User toggle is OFF. Turn ON to schedule reminders." }
        if activeClientManager.currentClientId == nil { return "Select an active client first." }
        if activeSupplementCount == 0 { return "Add at least one supplement for the active client." }
        if pendingEntries.isEmpty { return "No pending requests. Try Reschedule; check cycle/weekly/quiet-hours rules." }
        if shadowErrorCount > 0 { return "Some schedules failed. Check Shadow errors in the list/log." }
        if pendingOnlyCount > 0 { return "Pending has entries not found in Shadow. Try Clear pending then Reschedule." }
        if shadowOnlyCount > 0 { return "Shadow has entries missing in Pending. Reschedule may have failed or been cleared." }
        return "Healthy state."
    }
    
    private var diagnosticsText: String {
        [
            "permission=\(authorizationStatusText)",
            "enabledByUser=\(isNotificationEnabledByUser)",
            "activeClient=\(activeClientManager.currentClientId?.uuidString ?? "nil")",
            "activeSupplements=\(activeSupplementCount)",
            "pendingCount=\(pendingEntries.count)",
            "shadowCount=\(shadowEntries.count)",
            "pendingOnly=\(pendingOnlyCount)",
            "shadowOnly=\(shadowOnlyCount)",
            "shadowErrors=\(shadowErrorCount)"
        ].joined(separator: "\n")
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
    
    fileprivate var isShadowError: Bool {
        dose == "ERROR"
    }
    
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
        if parts.count >= 5, let date = parseDate(parts[4]) {
            let id = parts[0].trimmingCharacters(in: .whitespacesAndNewlines)
            let name = parts[1].trimmingCharacters(in: .whitespacesAndNewlines)
            let dose = parts[2].trimmingCharacters(in: .whitespacesAndNewlines)
            let cycleText = parts[3].trimmingCharacters(in: .whitespacesAndNewlines)
            return NotificationDebugEntry(id: id, name: name, dose: dose, cycleText: cycleText, scheduledAt: date)
        }
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
