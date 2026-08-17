import SwiftData
import SwiftUI

public struct CoachOverviewView: View {
    @Environment(EntitlementManager.self) private var entitlementManager
    @Query(sort: \ClientProfile.createdAt) private var clients: [ClientProfile]
    @State private var selectedWindowDays = 7
    @State private var searchText = ""
    @State private var sort: CoachReportSort = .attention
    @State private var filter: CoachReportFilter = .all

    public init() {}

    public var body: some View {
        let report = overview(windowDays: selectedWindowDays)
        List {
            if entitlementManager.canUse(.coachReports) {
                windowSection
                summarySection(report)
                trendSection(report)
                clientControls
                clientSection(report)
            } else {
                lockedSection
            }
        }
        .scrollContentBackground(.hidden)
        .background { Color.clear.oakBackground() }
        .navigationTitle("coach_overview_title".localized)
        .searchable(text: $searchText, prompt: Text("coach_search_clients".localized))
    }

    private func overview(windowDays: Int) -> CoachOverviewSummary {
        let clientSnapshots = clients.map { CoachClientSnapshot(id: $0.id, name: $0.name) }
        let records = Dictionary(uniqueKeysWithValues: clients.map { client in
            let snapshots = client.supplements.flatMap(\.intakeRecords).map {
                CoachRecordSnapshot(date: $0.date, status: $0.status)
            }
            return (client.id, snapshots)
        })
        return CoachOverviewBuilder.build(
            clients: clientSnapshots,
            recordsByClient: records,
            now: .now,
            windowDays: windowDays
        )
    }

    private var windowSection: some View {
        Section {
            Picker("coach_report_window".localized, selection: $selectedWindowDays) {
                Text(String(format: "coach_window_days_format".localized, 7)).tag(7)
                Text(String(format: "coach_window_days_format".localized, 30)).tag(30)
                Text(String(format: "coach_window_days_format".localized, 90)).tag(90)
            }
            .pickerStyle(.segmented)
        }
    }

    private func summarySection(_ report: CoachOverviewSummary) -> some View {
        Section {
            OAKResponsiveMetricLayout { metrics(report) }
            Text(
                String.localizedStringWithFormat(
                    "coach_attention_count_format".localized,
                    report.needsCheckInCount
                )
            )
            .font(.caption)
            .foregroundStyle(OAKPalette.accent)
            Text(overallCompletionText(report))
                .font(.subheadline)
                .foregroundStyle(.secondary)
        } header: {
            Text(String(format: "coach_report_window_format".localized, report.windowDays))
        }
    }

    @ViewBuilder
    private func metrics(_ report: CoachOverviewSummary) -> some View {
        metric("coach_metric_clients".localized, value: report.totalClients)
        Spacer(minLength: 8)
        metric("coach_metric_active".localized, value: report.activeClients)
        Spacer(minLength: 8)
        metric("coach_metric_check_in".localized, value: report.needsCheckInCount)
    }

    private func trendSection(_ report: CoachOverviewSummary) -> some View {
        Section("coach_trend_title".localized) {
            ForEach(report.trend.suffix(6)) { point in
                HStack {
                    Text(point.bucketStart.formatted(date: .numeric, time: .omitted))
                    Spacer()
                    Text(point.completionPercent.map { "\($0)%" } ?? "—")
                        .fontWeight(.semibold)
                }
                .font(.caption)
            }
        }
    }

    private var clientControls: some View {
        Section {
            Picker("coach_sort_title".localized, selection: $sort) {
                Text("coach_sort_attention".localized).tag(CoachReportSort.attention)
                Text("coach_sort_name".localized).tag(CoachReportSort.name)
                Text("coach_sort_completion".localized).tag(CoachReportSort.completion)
            }
            .pickerStyle(.menu)
            Picker("coach_filter_title".localized, selection: $filter) {
                Text("coach_filter_all".localized).tag(CoachReportFilter.all)
                Text("coach_filter_check_in".localized).tag(CoachReportFilter.checkIn)
                Text("coach_filter_active".localized).tag(CoachReportFilter.active)
                Text("coach_filter_inactive".localized).tag(CoachReportFilter.inactive)
            }
            .pickerStyle(.menu)
        }
    }

    @ViewBuilder
    private func clientSection(_ report: CoachOverviewSummary) -> some View {
        let visible = visibleClients(report.clients)
        Section("client_management".localized) {
            if visible.isEmpty {
                OAKFeedbackView(
                    title: "coach_empty_title".localized,
                    message: "coach_overview_empty".localized
                )
            } else {
                ForEach(visible) { client in
                    if let profile = clients.first(where: { $0.id == client.clientId }) {
                        NavigationLink {
                            CoachClientDetailView(client: profile)
                        } label: {
                            clientRow(client)
                        }
                    }
                }
            }
        }
    }

    private func visibleClients(_ clients: [CoachClientSummary]) -> [CoachClientSummary] {
        let searched = clients.filter {
            searchText.isEmpty || $0.name.localizedCaseInsensitiveContains(searchText)
        }
        let filtered = searched.filter(matchesFilter)
        switch sort {
        case .attention:
            return filtered.sorted { left, right in
                if left.needsCheckIn != right.needsCheckIn { return left.needsCheckIn }
                return left.name.localizedCaseInsensitiveCompare(right.name) == .orderedAscending
            }
        case .name:
            return filtered.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
        case .completion:
            return filtered.sorted { ($0.completionPercent ?? -1) > ($1.completionPercent ?? -1) }
        }
    }

    private func matchesFilter(_ client: CoachClientSummary) -> Bool {
        switch filter {
        case .all: return true
        case .checkIn: return client.needsCheckIn
        case .active: return client.takenCount + client.skippedCount > 0
        case .inactive: return client.takenCount + client.skippedCount == 0
        }
    }

    private var lockedSection: some View {
        Section {
            OAKFeedbackView(
                title: "coach_overview_locked_title".localized,
                message: "coach_overview_locked_body".localized
            )
            NavigationLink("plan_access_manage".localized) { PlanAccessView() }
        }
    }

    private func metric(_ title: String, value: Int) -> some View {
        VStack(spacing: 4) {
            Text("\(value)")
                .font(.oakDisplay(size: 34))
                .monospacedDigit()
            Text(title)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .combine)
    }

    private func overallCompletionText(_ report: CoachOverviewSummary) -> String {
        guard let completion = report.overallCompletionPercent else { return "coach_no_recent_records".localized }
        return String(
            format: "coach_report_total_format".localized,
            completion,
            report.takenCount,
            report.skippedCount
        )
    }

    private func clientRow(_ client: CoachClientSummary) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(client.name).font(.headline)
                Spacer()
                if client.needsCheckIn { Text("coach_check_in_badge".localized).font(.caption.weight(.semibold)) }
            }
            Text(completionText(client)).font(.subheadline).foregroundStyle(.secondary)
            Text(activityText(client)).font(.caption).foregroundStyle(.secondary)
        }
        .accessibilityElement(children: .combine)
    }

    private func completionText(_ client: CoachClientSummary) -> String {
        guard let completion = client.completionPercent else { return "coach_no_recent_records".localized }
        return String(format: "coach_completion_format".localized, completion, client.takenCount, client.skippedCount)
    }

    private func activityText(_ client: CoachClientSummary) -> String {
        guard let date = client.lastActivity else { return "coach_last_activity_none".localized }
        return String(format: "coach_last_activity_format".localized, date.formatted(date: .abbreviated, time: .omitted))
    }
}

private enum CoachReportSort: Hashable {
    case attention
    case name
    case completion
}

private enum CoachReportFilter: Hashable {
    case all
    case checkIn
    case active
    case inactive
}
