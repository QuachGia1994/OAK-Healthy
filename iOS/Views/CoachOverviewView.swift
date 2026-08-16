import SwiftData
import SwiftUI

public struct CoachOverviewView: View {
    @Environment(EntitlementManager.self) private var entitlementManager
    @Query(sort: \ClientProfile.createdAt) private var clients: [ClientProfile]
    @State private var selectedWindowDays = 7
    @State private var searchText = ""
    @State private var sort: CoachReportSort = .attention

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
            ViewThatFits(in: .horizontal) {
                HStack { metrics(report) }
                VStack(alignment: .leading) { metrics(report) }
            }
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
        }
    }

    @ViewBuilder
    private func clientSection(_ report: CoachOverviewSummary) -> some View {
        let visible = visibleClients(report.clients)
        Section("client_management".localized) {
            if visible.isEmpty {
                Text("coach_overview_empty".localized).foregroundStyle(.secondary)
            } else {
                ForEach(visible) { client in clientRow(client) }
            }
        }
    }

    private func visibleClients(_ clients: [CoachClientSummary]) -> [CoachClientSummary] {
        let filtered = clients.filter {
            searchText.isEmpty || $0.name.localizedCaseInsensitiveContains(searchText)
        }
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

    private var lockedSection: some View {
        Section {
            Text("coach_overview_locked_body".localized).foregroundStyle(.secondary)
            NavigationLink("plan_access_manage".localized) { PlanAccessView() }
        } header: {
            Text("coach_overview_locked_title".localized)
        }
    }

    private func metric(_ title: String, value: Int) -> some View {
        VStack(spacing: 4) {
            Text("\(value)").font(.title2.bold())
            Text(title).font(.caption).foregroundStyle(.secondary)
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
