import SwiftData
import SwiftUI

public struct CoachOverviewView: View {
    @Environment(EntitlementManager.self) private var entitlementManager
    @Query(sort: \ClientProfile.createdAt) private var clients: [ClientProfile]

    public init() {}

    public var body: some View {
        List {
            if entitlementManager.canUse(.coachReports) {
                summarySection
                clientSection
            } else {
                lockedSection
            }
        }
        .navigationTitle("coach_overview_title".localized)
    }

    private var overview: CoachOverviewSummary {
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
            now: .now
        )
    }

    private var summarySection: some View {
        Section("coach_overview_last_7_days".localized) {
            HStack {
                metric("coach_metric_clients".localized, value: overview.totalClients)
                Spacer()
                metric("coach_metric_active".localized, value: overview.activeClients)
                Spacer()
                metric("coach_metric_check_in".localized, value: overview.needsCheckInCount)
            }
        }
    }

    @ViewBuilder
    private var clientSection: some View {
        Section("client_management".localized) {
            if overview.clients.isEmpty {
                Text("coach_overview_empty".localized)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(overview.clients) { client in
                    clientRow(client)
                }
            }
        }
    }

    private var lockedSection: some View {
        Section {
            Text("coach_overview_locked_body".localized)
                .foregroundStyle(.secondary)
            NavigationLink("plan_access_manage".localized) {
                PlanAccessView()
            }
        } header: {
            Text("coach_overview_locked_title".localized)
        }
    }

    private func metric(_ title: String, value: Int) -> some View {
        VStack(spacing: 4) {
            Text("\(value)")
                .font(.title2.bold())
            Text(title)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .accessibilityElement(children: .combine)
    }

    private func clientRow(_ client: CoachClientSummary) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(client.name).font(.headline)
                Spacer()
                if client.needsCheckIn {
                    Text("coach_check_in_badge".localized)
                        .font(.caption.weight(.semibold))
                }
            }
            Text(completionText(client))
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Text(activityText(client))
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .accessibilityElement(children: .combine)
    }

    private func completionText(_ client: CoachClientSummary) -> String {
        guard let completion = client.completionPercent else {
            return "coach_no_recent_records".localized
        }
        return String(
            format: "coach_completion_format".localized,
            completion,
            client.takenCount,
            client.skippedCount
        )
    }

    private func activityText(_ client: CoachClientSummary) -> String {
        guard let date = client.lastActivity else { return "coach_last_activity_none".localized }
        let formatted = date.formatted(date: .abbreviated, time: .omitted)
        return String(format: "coach_last_activity_format".localized, formatted)
    }
}
