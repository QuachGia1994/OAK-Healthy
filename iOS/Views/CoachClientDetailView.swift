import SwiftUI

struct CoachClientDetailView: View {
    let client: ClientProfile
    @State private var windowDays = 7
    @State private var note = ""
    @State private var feeling: CoachRoutineFeeling = .okay
    @State private var checkInVersion = 0
    @State private var checkIns: [CoachCheckInEntry] = []
    @State private var checkInErrorMessage: String?

    var body: some View {
        let detail = makeDetail()
        let report = CoachWorkspaceBuilder.reportDocument(
            detail: detail,
            checkIns: checkIns,
            generatedAtEpochMs: Int64(Date().timeIntervalSince1970 * 1000)
        )
        List {
            windowSection
            comparisonSection(detail)
            checkInSection(checkIns)
            reportSection(report)
        }
        .listSectionSpacing(20)
        .id(checkInVersion)
        .task(id: checkInVersion) { loadCheckIns() }
        .navigationTitle(client.name)
        .navigationBarTitleDisplayMode(.inline)
    }

    private var windowSection: some View {
        Section {
            Picker("coach_report_window".localized, selection: $windowDays) {
                ForEach([7, 30, 90], id: \.self) { days in
                    Text(String(format: "coach_window_days_format".localized, days)).tag(days)
                }
            }
            .pickerStyle(.segmented)
        }
    }

    private func comparisonSection(_ detail: CoachClientDetail) -> some View {
        Section {
            VStack(alignment: .leading, spacing: OAKSpacing.md) {
                Text("coach_detail_comparison_title".localized)
                    .font(.subheadline.weight(.semibold))
                Text(periodText("coach_current_period_format", stats: detail.current))
                    .font(.oakDisplay(size: 26))
                Text(periodText("coach_previous_period_format", stats: detail.previous))
                    .font(.subheadline)
                    .oakSecondaryText()
                Text(deltaText(detail.completionDeltaPoints))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(OAKPalette.accent)
                Divider()
                Text("coach_trend_title".localized)
                    .font(.subheadline.weight(.semibold))
                ForEach(detail.trend.suffix(6)) { point in
                    HStack {
                        Text(point.bucketStart.formatted(date: .numeric, time: .omitted))
                        Spacer()
                        Text(point.completionPercent.map { "\($0)%" } ?? "—")
                            .fontWeight(.semibold)
                    }
                    .font(.caption)
                }
            }
            .padding(OAKSpacing.xl)
            .oakCardStyle(.paper, cornerRadius: OAKRadius.lg)
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
        }
    }

    private func checkInSection(_ entries: [CoachCheckInEntry]) -> some View {
        Section("coach_check_in_title".localized) {
            Picker("coach_check_in_feeling".localized, selection: $feeling) {
                ForEach(CoachRoutineFeeling.allCases, id: \.self) { value in
                    Text(feelingText(value)).tag(value)
                }
            }
            TextField("coach_note_hint".localized, text: $note, axis: .vertical)
                .lineLimit(2...5)
                .onChange(of: note) { _, value in
                    if value.count > CoachCheckInPolicy.maxNoteLength {
                        note = String(value.prefix(CoachCheckInPolicy.maxNoteLength))
                    }
                }
            Button("coach_save_check_in".localized, action: saveCheckIn)
            if let checkInErrorMessage {
                Text(checkInErrorMessage).foregroundStyle(.red).font(.caption)
            } else if entries.isEmpty {
                Text("coach_check_in_empty".localized).foregroundStyle(.secondary)
            } else {
                ForEach(entries.prefix(3)) { entry in checkInRow(entry) }
            }
        }
    }

    private func reportSection(_ report: CoachReportDocument) -> some View {
        Section("coach_report_ready_title".localized) {
            Text(
                String(
                    format: "coach_report_ready_format".localized,
                    report.trend.count,
                    report.checkIns.count
                )
            )
            .font(.caption)
            .foregroundStyle(.secondary)
            Text("coach_report_ready_hint".localized)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    private func makeDetail() -> CoachClientDetail {
        let snapshot = CoachClientSnapshot(id: client.id, name: client.name)
        let records = client.supplements.flatMap(\.intakeRecords).map {
            CoachRecordSnapshot(date: $0.date, status: $0.status)
        }
        return CoachWorkspaceBuilder.buildDetail(
            client: snapshot,
            records: records,
            now: .now,
            windowDays: windowDays
        )
    }

    private func periodText(_ key: String, stats: CoachWindowStats) -> String {
        let completion = stats.completionPercent.map { "\($0)%" } ?? "—"
        return String(format: key.localized, completion, stats.activeDays)
    }

    private func deltaText(_ delta: Int?) -> String {
        let text = delta.map { $0 >= 0 ? "+\($0)" : "\($0)" } ?? "—"
        return String(format: "coach_delta_format".localized, text)
    }

    private func feelingText(_ value: CoachRoutineFeeling) -> String {
        switch value {
        case .comfortable: return "coach_feeling_comfortable".localized
        case .okay: return "coach_feeling_okay".localized
        case .difficult: return "coach_feeling_difficult".localized
        }
    }

    private func checkInRow(_ entry: CoachCheckInEntry) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text("\(Date(timeIntervalSince1970: Double(entry.epochMs) / 1000).formatted(date: .abbreviated, time: .shortened)) · \(feelingText(entry.feeling))")
                .font(.caption.weight(.semibold))
            if !entry.note.isEmpty { Text(entry.note).font(.caption).foregroundStyle(.secondary) }
        }
    }

    private func saveCheckIn() {
        do {
            try CoachCheckInStore.add(
                clientId: client.id,
                feeling: feeling,
                note: note,
                epochMs: Int64(Date().timeIntervalSince1970 * 1_000)
            )
            note = ""
            checkInErrorMessage = nil
            checkInVersion += 1
        } catch {
            checkInErrorMessage = String(format: "coach_check_in_error_format".localized, error.localizedDescription)
        }
    }

    private func loadCheckIns() {
        do {
            checkIns = try CoachCheckInStore.entries(clientId: client.id)
            checkInErrorMessage = nil
        } catch {
            checkIns = []
            checkInErrorMessage = String(format: "coach_check_in_error_format".localized, error.localizedDescription)
        }
    }
}
