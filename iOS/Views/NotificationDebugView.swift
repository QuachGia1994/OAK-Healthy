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
    @State private var isRepairingShadow = false
    @State private var didAutoRepairShadow = false
    
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
                
                LabeledContent("onboarding_permission_status".localized) { Text(authorizationStatusText) }
                LabeledContent("notification_debug_enabled_user_label".localized) { Text(enabledText) }
                LabeledContent("notification_debug_active_client_label".localized) { Text(activeClientText) }
                LabeledContent("notification_debug_active_supplements_label".localized) { Text("\(activeSupplementCount)") }
                LabeledContent("notification_debug_pending_os_label".localized) { Text("\(pendingEntries.count)") }
                LabeledContent("notification_debug_shadow_label".localized) { Text("\(shadowEntries.count)") }
                LabeledContent("notification_debug_pending_only_label".localized) { Text("\(pendingOnlyCount)") }
                LabeledContent("notification_debug_shadow_only_label".localized) { Text("\(shadowOnlyCount)") }
                LabeledContent("notification_debug_shadow_errors_label".localized) { Text("\(shadowErrorCount)") }
                LabeledContent("notification_reliability_health".localized) { Text(reliabilityLevelText) }
                LabeledContent("notification_reliability_mismatch".localized) { Text("\(reliabilityReport.mismatchCount)") }

                if reliabilityReport.shouldOfferRepair {
                    Button("notification_reliability_rebuild".localized) {
                        Task { await rebuildSchedules() }
                    }
                    .disabled(isRepairingShadow)
                }
                
                if shouldShowRepairShadow {
                    Button("notification_debug_repair_shadow".localized) {
                        Task { await repairShadow() }
                    }
                    .disabled(isRepairingShadow)
                }
            }
            
            Section("notification_debug_diagnostics_section".localized) {
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
        .refreshable {
            await refresh()
        }
    }
    
    @MainActor
    private func refresh() async {
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()
        let pending = await NotificationService.shared.pendingRequestSnapshots()
        let shadowRaw = await NotificationService.shared.shadowScheduledTimes()
        var parsedPending = NotificationDebugEntry.parseMany(pending)
        var parsedShadow = NotificationDebugEntry.parseMany(shadowRaw)
        let supplements = fetchActiveSupplements(clientId: activeClientManager.currentClientId)
        let supplementCount = supplements.count
        var (pendingOnly, shadowOnly, errorCount) = computeReconciliation(pending: parsedPending, shadow: parsedShadow)
        
        if shouldAutoRepairShadow(settings: settings, pendingOnly: pendingOnly, shadowOnly: shadowOnly, errorCount: errorCount) {
            didAutoRepairShadow = true
            _ = await NotificationService.shared.reconcileSchedulesIfNeeded(supplements: supplements)
            parsedPending = NotificationDebugEntry.parseMany(await NotificationService.shared.pendingRequestSnapshots())
            parsedShadow = NotificationDebugEntry.parseMany(await NotificationService.shared.shadowScheduledTimes())
            (pendingOnly, shadowOnly, errorCount) = computeReconciliation(pending: parsedPending, shadow: parsedShadow)
        }
        
        authorizationStatus = settings.authorizationStatus
        pendingEntries = parsedPending
        shadowEntries = parsedShadow
        activeSupplementCount = supplementCount
        pendingOnlyCount = pendingOnly
        shadowOnlyCount = shadowOnly
        shadowErrorCount = errorCount
        isLoading = false
    }
    
    @MainActor
    private func rebuildSchedules() async {
        guard let clientId = activeClientManager.currentClientId else { return }
        do {
            let supplements = try ClientScopedStore.activeSupplements(
                modelContext: modelContext,
                clientId: clientId
            )
            await NotificationService.shared.replaceAllSchedules(supplements: supplements)
            await refresh()
        } catch {
            return
        }
    }

    @MainActor
    private func repairShadow() async {
        guard !isRepairingShadow else { return }
        isRepairingShadow = true
        defer { isRepairingShadow = false }
        await NotificationService.shared.rebuildShadowFromPendingRequests()
        await refresh()
    }
    
    private var reliabilityReport: NotificationReliabilityReport {
        NotificationReliabilityEvaluator.evaluate(
            NotificationReliabilityInput(
                permissionGranted: authorizationStatus == .authorized || authorizationStatus == .provisional,
                enabledByUser: isNotificationEnabledByUser,
                hasActiveClient: activeClientManager.currentClientId != nil,
                activeSupplementCount: activeSupplementCount,
                pendingCount: pendingEntries.count,
                pendingOnlyCount: pendingOnlyCount,
                shadowOnlyCount: shadowOnlyCount,
                shadowErrorCount: shadowErrorCount
            )
        )
    }

    private var reliabilityLevelText: String {
        switch reliabilityReport.level {
        case .healthy: return "notification_reliability_healthy".localized
        case .degraded: return "notification_reliability_degraded".localized
        case .needsRepair: return "notification_reliability_needs_repair".localized
        case .inactive: return "notification_reliability_inactive".localized
        }
    }

    private var shouldShowRepairShadow: Bool {
        isNotificationEnabledByUser &&
            (authorizationStatus == .authorized || authorizationStatus == .provisional) &&
            (pendingOnlyCount > 0 || shadowOnlyCount > 0 || shadowErrorCount > 0)
    }
    
    private func shouldAutoRepairShadow(
        settings: UNNotificationSettings,
        pendingOnly: Int,
        shadowOnly: Int,
        errorCount: Int
    ) -> Bool {
        guard !didAutoRepairShadow else { return false }
        guard isNotificationEnabledByUser else { return false }
        guard settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional else { return false }
        return pendingOnly > 0 || shadowOnly > 0 || errorCount > 0
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
    private func fetchActiveSupplements(clientId: UUID?) -> [UserSupplement] {
        guard let clientId else { return [] }
        return (try? ClientScopedStore.activeSupplements(
            modelContext: modelContext,
            clientId: clientId
        )) ?? []
    }
    
    private func entryKey(_ entry: NotificationDebugEntry) -> String {
        "\(entry.id)|\(NotificationDebugFormatters.entryKey.string(from: entry.scheduledAt))"
    }
    
    private var diagnosisTitle: String {
        if authorizationStatus == .denied { return "notification_debug_diag_denied_title".localized }
        if !isNotificationEnabledByUser { return "notification_debug_diag_off_title".localized }
        if activeClientManager.currentClientId == nil { return "notification_debug_diag_no_active_client_title".localized }
        if activeSupplementCount == 0 { return "notification_debug_diag_no_supplements_title".localized }
        if pendingEntries.isEmpty { return "notification_debug_diag_scheduled_zero_title".localized }
        if pendingOnlyCount > 0 || shadowOnlyCount > 0 || shadowErrorCount > 0 { return "notification_debug_diag_mismatch_title".localized }
        return "notification_debug_diag_ok_title".localized
    }
    
    private var diagnosisHint: String {
        if authorizationStatus == .denied { return "notification_debug_diag_denied_hint".localized }
        if authorizationStatus == .notDetermined { return "notification_debug_diag_not_determined_hint".localized }
        if !isNotificationEnabledByUser { return "notification_debug_diag_off_hint".localized }
        if activeClientManager.currentClientId == nil { return "notification_debug_diag_no_active_client_hint".localized }
        if activeSupplementCount == 0 { return "notification_debug_diag_no_supplements_hint".localized }
        if pendingEntries.isEmpty { return "notification_debug_diag_scheduled_zero_hint".localized }
        if shadowErrorCount > 0 { return "notification_debug_diag_shadow_error_hint".localized }
        if pendingOnlyCount > 0 { return "notification_debug_diag_pending_only_hint".localized }
        if shadowOnlyCount > 0 { return "notification_debug_diag_shadow_only_hint".localized }
        return "notification_debug_diag_ok_hint".localized
    }
    
    private var diagnosticsText: String {
        [
            "permission=\(authorizationStatusText)",
            "enabledByUser=\(enabledText)",
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
        case .authorized, .provisional:
            return "onboarding_permission_granted".localized
        case .denied:
            return "onboarding_permission_denied".localized
        default:
            return "onboarding_permission_not_determined".localized
        }
    }
    
    private var enabledText: String {
        isNotificationEnabledByUser ? "status_on".localized : "status_off".localized
    }
    
    private var activeClientText: String {
        activeClientManager.currentClientId == nil ? "status_no".localized : "status_yes".localized
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
        let sourceText = key.source == .pending ? "notification_debug_source_pending".localized : "notification_debug_source_shadow".localized
        return "\(sourceText) • \(dayHeader(key.day))"
    }
    
    private func dayHeader(_ day: Date) -> String {
        NotificationDebugFormatters.dayHeader.string(from: day)
    }
}

private enum NotificationDebugFormatters {
    static let entryKey: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyyMMddHHmm"
        return formatter
    }()
    
    static let dayHeader: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale.current
        formatter.dateFormat = "EEEE, dd/MM/yyyy"
        return formatter
    }()
    
    static let time: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale.current
        formatter.dateFormat = "HH:mm"
        return formatter
    }()
    
    static let shadowDate: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd HH:mm"
        return formatter
    }()
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
        NotificationDebugFormatters.time.string(from: entry.scheduledAt)
    }
    
    private var iconName: String {
        iconFor(substanceName: entry.name)
    }
    
    private var detailText: String {
        let origin = source == .pending ? "notification_debug_source_pending".localized : "notification_debug_source_shadow".localized
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
        NotificationDebugFormatters.shadowDate.date(from: raw.trimmingCharacters(in: .whitespacesAndNewlines))
    }
}
