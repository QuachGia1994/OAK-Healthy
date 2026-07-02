import SwiftUI
import SwiftData

/// Màn hình thêm mới thực phẩm bổ sung.
public struct AddSupplementView: View {
    @Environment(\.colorScheme) private var colorScheme
    @State private var viewModel: AddSupplementViewModel
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var modelContext
    
    public var onSave: (UserSupplement) -> Void
    
    private let activeClient: ClientProfile?
    
    public init(
        modelContext: ModelContext,
        editingSupplement: UserSupplement? = nil,
        activeClient: ClientProfile? = nil,
        onSave: @escaping (UserSupplement) -> Void
    ) {
        self.onSave = onSave
        self.activeClient = activeClient
        _viewModel = State(initialValue: AddSupplementViewModel(modelContext: modelContext, editingSupplement: editingSupplement, activeClient: activeClient))
    }
    
    public var body: some View {
        NavigationStack {
            ZStack {
                Form {
                    basicSection
                    cycleSection
                }
                .scrollContentBackground(.hidden)
                .listStyle(.insetGrouped)
                .navigationTitle("add_supplement_title".localized)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("cancel".localized) { dismiss() }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("save".localized) {
                            Task {
                                if let supplement = await viewModel.saveSupplement() {
                                    onSave(supplement)
                                    dismiss()
                                }
                            }
                        }
                        .disabled(viewModel.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || viewModel.isSaving)
                    }
                }
                .alert(
                    "error_title".localized,
                    isPresented: Binding(
                        get: { viewModel.errorMessage != nil },
                        set: { isPresented in
                            if !isPresented { viewModel.errorMessage = nil }
                        }
                    )
                ) {
                    Button("ok".localized, role: .cancel) {}
                } message: {
                    Text(viewModel.errorMessage ?? "")
                }
            }
            .overlay {
                if viewModel.isSaving {
                    ZStack {
                        Color.black.opacity(0.2).ignoresSafeArea()
                        ProgressView()
                            .progressViewStyle(.circular)
                            .tint(.white)
                            .padding(16)
                            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
                    }
                }
            }
        }
        .oakBackground()
    }

    private var basicSection: some View {
        Section {
            TextField("name_hint".localized, text: $viewModel.name)
                .onChange(of: viewModel.name) {
                    Task { await viewModel.updateSuggestions() }
                }
                .listRowBackground(OakGlassRow.background)
            
            suggestionsSection
            
            TextField("dose_hint".localized, text: $viewModel.dailyDose)
                .listRowBackground(OakGlassRow.background)
        } header: {
            Text("basic_info_title".localized)
        }
    }
    
    @ViewBuilder
    private var suggestionsSection: some View {
        if !viewModel.suggestions.isEmpty {
            ForEach(viewModel.suggestions) { suggestion in
                Button {
                    viewModel.selectSuggestion(suggestion)
                } label: {
                    HStack {
                        VStack(alignment: .leading) {
                            Text(suggestion.name)
                                .font(.headline)
                            if let advice = suggestion.advice, !advice.isEmpty {
                                Text(advice.localized)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            } else {
                                Text(String(format: "suggested_format".localized, suggestion.preferredTime))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        Spacer()
                        Image(systemName: "plus.circle")
                            .accessibilityLabel("a11y_add_supplement".localized)
                    }
                }
                .listRowBackground(OakGlassRow.background)
            }
        }
    }
    
    private var cycleSection: some View {
        Section {
            DatePicker("start_date".localized, selection: $viewModel.startDate, displayedComponents: .date)
                .listRowBackground(OakGlassRow.background)
            TextField("intake_time".localized, text: $viewModel.intakeTimes)
                .keyboardType(.numbersAndPunctuation)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .onSubmit {
                    viewModel.intakeTimes = TimeStrings.normalizeString(viewModel.intakeTimes)
                }
                .listRowBackground(OakGlassRow.background)
            HStack {
                DatePicker("", selection: $viewModel.selectedTime, displayedComponents: .hourAndMinute)
                    .labelsHidden()
                Spacer()
                Button("add_time".localized) { viewModel.addSelectedTime() }
            }
            .listRowBackground(OakGlassRow.background)
            weeklyRecurrenceControls
                .listRowBackground(OakGlassRow.background)
            intervalDaysControls
            continuousCycleControls
        } header: {
            Text("schedule_cycle_title".localized)
        }
    }

    @ViewBuilder
    private var intervalDaysControls: some View {
        Toggle("repeat_every_n_days".localized, isOn: $viewModel.isIntervalDaysEnabled)
            .listRowBackground(OakGlassRow.background)
        if viewModel.isIntervalDaysEnabled {
            intervalDaysRow
                .listRowBackground(OakGlassRow.background)
        }
    }

    private var intervalDaysRow: some View {
        HStack {
            Text("interval_days_label".localized)
            Spacer()
            TextField("2", text: $viewModel.intervalDays)
                .keyboardType(.numberPad)
                .multilineTextAlignment(.trailing)
                .frame(width: 80)
        }
    }

    @ViewBuilder
    private var continuousCycleControls: some View {
        Toggle("continuous".localized, isOn: $viewModel.isContinuous)
            .listRowBackground(OakGlassRow.background)
        if !viewModel.isContinuous {
            cycleDaysOnRow
                .listRowBackground(OakGlassRow.background)
            cycleDaysOffRow
                .listRowBackground(OakGlassRow.background)
        }
        durationRow
            .listRowBackground(OakGlassRow.background)
        if !viewModel.durationMonths.isEmpty {
            durationHintRow
                .listRowBackground(OakGlassRow.background)
        }
    }

    private var cycleDaysOnRow: some View {
        HStack {
            Text("on_days".localized)
            Spacer()
            TextField("example_on_days".localized, text: $viewModel.daysOn)
                .keyboardType(.numberPad)
                .multilineTextAlignment(.trailing)
                .frame(width: 80)
        }
    }

    private var cycleDaysOffRow: some View {
        HStack {
            Text("off_days".localized)
            Spacer()
            TextField("example_off_days".localized, text: $viewModel.daysOff)
                .keyboardType(.numberPad)
                .multilineTextAlignment(.trailing)
                .frame(width: 80)
        }
    }

    private var durationRow: some View {
        HStack {
            Text("duration".localized)
            Spacer()
            TextField("unlimited".localized, text: $viewModel.durationMonths)
                .keyboardType(.numberPad)
                .multilineTextAlignment(.trailing)
                .frame(width: 80)
        }
    }

    private var durationHintRow: some View {
        Text("months".localized)
            .font(.caption)
            .foregroundStyle(.secondary)
    }
    
    private var weeklyRecurrenceControls: some View {
        VStack(alignment: .leading, spacing: 10) {
            Toggle("repeat_weekly".localized, isOn: $viewModel.isWeeklyRecurrenceEnabled)
            if viewModel.isWeeklyRecurrenceEnabled {
                VStack(alignment: .leading, spacing: 8) {
                    Text("repeat_on_weekdays".localized)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    LazyVGrid(
                        columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 7),
                        spacing: 8
                    ) {
                        ForEach(Array(weekdayLabels.enumerated()), id: \.offset) { index, label in
                            let isSelected = (viewModel.weekdaysMask & (1 << index)) != 0
                            Button { viewModel.toggleWeekday(bitIndex: index) } label: {
                                Text(label)
                                    .lineLimit(1)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 8)
                                    .font(.subheadline.weight(.semibold))
                            }
                            .buttonStyle(.plain)
                            .contentShape(Capsule())
                            .foregroundStyle(isSelected ? Color.white : Color.primary)
                            .background(
                                Capsule()
                                    .fill(isSelected ? Color.accentColor : Color.gray.opacity(0.18))
                            )
                        }
                    }
                    HStack {
                        Text("repeat_every".localized)
                        Spacer()
                        TextField("1", text: $viewModel.intervalWeeks)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                            .frame(width: 80)
                    }
                    Text(weeklySummaryText)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    private var weekdayLabels: [String] {
        ["weekday_mon".localized, "weekday_tue".localized, "weekday_wed".localized,
         "weekday_thu".localized, "weekday_fri".localized, "weekday_sat".localized, "weekday_sun".localized]
    }
    
    private var weeklySummaryText: String {
        let days = weekdayLabels.enumerated().compactMap { index, label in
            (viewModel.weekdaysMask & (1 << index)) != 0 ? label : nil
        }
        let every = max(1, Int(viewModel.intervalWeeks) ?? 1)
        let dayText = days.isEmpty ? "-" : days.joined(separator: ", ")
        return "\(dayText) • \(String(format: "every_x_weeks_format".localized, every))"
    }
}

#Preview {
    AddSupplementPreview()
}

private struct AddSupplementPreview: View {
    var body: some View {
        Group {
            if let context = Self.makePreviewContext() {
                AddSupplementView(modelContext: context) { _ in }
            } else {
                Text("preview_unavailable".localized)
            }
        }
    }
    
    private static func makePreviewContext() -> ModelContext? {
        do {
            let configuration = ModelConfiguration(isStoredInMemoryOnly: true)
            let container = try ModelContainer(for: ClientProfile.self, UserSupplement.self, IntakeRecord.self, configurations: configuration)
            return container.mainContext
        } catch {
            return nil
        }
    }
}
