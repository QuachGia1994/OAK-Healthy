import SwiftData
import SwiftUI

/// Presents the add or edit supplement workflow.
public struct AddSupplementView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(EntitlementManager.self) private var entitlementManager
    @State private var viewModel: AddSupplementViewModel

    private let isEditing: Bool
    private let onSave: (UserSupplement) -> Void

    /// Creates a supplement editor backed by the supplied model context.
    public init(
        modelContext: ModelContext,
        editingSupplement: UserSupplement? = nil,
        activeClient: ClientProfile? = nil,
        onSave: @escaping (UserSupplement) -> Void
    ) {
        self.isEditing = editingSupplement != nil
        self.onSave = onSave
        _viewModel = State(
            initialValue: AddSupplementViewModel(
                modelContext: modelContext,
                editingSupplement: editingSupplement,
                activeClient: activeClient
            )
        )
    }

    public var body: some View {
        NavigationStack {
            ZStack {
                Color.clear.oakBackground()
                formContent
            }
            .navigationTitle(navigationTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { cancelToolbarItem }
            .safeAreaInset(edge: .bottom) { saveBar }
            .overlay { savingOverlay }
            .alert("error_title".localized, isPresented: errorBinding) {
                Button("ok".localized, role: .cancel) {}
            } message: {
                Text(viewModel.errorMessage ?? "")
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .task(id: entitlementManager.snapshot.plan) {
            applyEntitlement()
        }
    }

    private var formContent: some View {
        ScrollView {
            VStack(spacing: 16) {
                formIntro
                detailsSection
                timingSection
                rhythmSection
            }
            .padding(.horizontal, 18)
            .padding(.top, 12)
            .padding(.bottom, 24)
        }
        .scrollDismissesKeyboard(.interactively)
        .scrollIndicators(.hidden)
    }

    private var formIntro: some View {
        HStack(spacing: 14) {
            Image(systemName: "cross.case.fill")
                .font(.title2)
                .foregroundStyle(.white)
                .frame(width: 50, height: 50)
                .background(OAKPalette.accent.gradient, in: RoundedRectangle(cornerRadius: 15))
            VStack(alignment: .leading, spacing: 4) {
                Text(navigationTitle)
                    .font(.title3.weight(.bold))
                Text("add_supplement_intro".localized)
                    .font(.subheadline)
                    .oakSecondaryText()
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 2)
    }

    private var detailsSection: some View {
        SupplementFormSection(
            title: "supplement_details_title".localized,
            subtitle: "supplement_details_body".localized,
            systemImage: "pills.fill"
        ) {
            nameField
            suggestionsContent
            doseField
        }
    }

    private var nameField: some View {
        TextField("name_hint".localized, text: $viewModel.name)
            .textInputAutocapitalization(.words)
            .onChange(of: viewModel.name) { _, value in updateName(value) }
            .supplementFieldStyle()
    }

    @ViewBuilder
    private var suggestionsContent: some View {
        if viewModel.isLoading {
            ProgressView().frame(maxWidth: .infinity, alignment: .leading)
        } else if !viewModel.suggestions.isEmpty {
            ScrollView(.horizontal) {
                HStack(spacing: 10) {
                    ForEach(viewModel.suggestions) { suggestion in
                        suggestionButton(suggestion)
                    }
                }
            }
            .scrollIndicators(.hidden)
        }
    }

    private func suggestionButton(_ suggestion: SupplementReference) -> some View {
        Button { viewModel.selectSuggestion(suggestion) } label: {
            HStack(spacing: 8) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(suggestion.name).font(.subheadline.weight(.semibold))
                    Text(suggestionCaption(suggestion))
                        .font(.caption)
                        .oakSecondaryText()
                        .lineLimit(1)
                }
                Image(systemName: "plus.circle.fill")
                    .foregroundStyle(OAKPalette.accent)
            }
            .padding(12)
            .background(Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 14))
        }
        .buttonStyle(.plain)
    }

    private var doseField: some View {
        TextField("dose_hint".localized, text: $viewModel.dailyDose)
            .supplementFieldStyle()
    }

    private var timingSection: some View {
        SupplementFormSection(
            title: "supplement_timing_title".localized,
            subtitle: "supplement_timing_body".localized,
            systemImage: "clock.fill"
        ) {
            DatePicker(
                "start_date".localized,
                selection: $viewModel.startDate,
                displayedComponents: .date
            )
            Divider().opacity(0.35)
            intakeTimeChips
            addTimeRow
        }
    }

    @ViewBuilder
    private var intakeTimeChips: some View {
        let times = TimeStrings.normalizeList(viewModel.intakeTimes)
        if times.isEmpty {
            Label("intake_times_empty".localized, systemImage: "exclamationmark.circle")
                .font(.footnote)
                .foregroundStyle(.red)
        } else {
            ScrollView(.horizontal) {
                HStack(spacing: 8) {
                    ForEach(times, id: \.self) { time in timeChip(time) }
                }
            }
            .scrollIndicators(.hidden)
        }
    }

    private func timeChip(_ time: String) -> some View {
        Button { viewModel.removeIntakeTime(time) } label: {
            HStack(spacing: 6) {
                Text(time).monospacedDigit()
                Image(systemName: "xmark.circle.fill")
                    .font(.caption)
                    .oakSecondaryText()
            }
            .font(.subheadline.weight(.semibold))
            .padding(.horizontal, 12)
            .padding(.vertical, 9)
            .background(OAKPalette.accent.opacity(0.12), in: Capsule())
        }
        .buttonStyle(.plain)
    }

    private var addTimeRow: some View {
        HStack(spacing: 12) {
            DatePicker("", selection: $viewModel.selectedTime, displayedComponents: .hourAndMinute)
                .labelsHidden()
            Spacer()
            Button { viewModel.addSelectedTime() } label: {
                Label("add_time".localized, systemImage: "plus")
            }
            .buttonStyle(.bordered)
        }
    }

    private var rhythmSection: some View {
        SupplementFormSection(
            title: "supplement_rhythm_title".localized,
            subtitle: "supplement_rhythm_body".localized,
            systemImage: "waveform.path.ecg"
        ) {
            if viewModel.advancedCyclesAllowed {
                cycleModePicker
                cycleFields
                Divider().opacity(0.35)
                weeklyControls
                intervalControls
                durationField
            } else {
                Label("plan_advanced_cycles_required".localized, systemImage: "lock.fill")
                    .font(.subheadline)
                    .oakSecondaryText()
            }
        }
    }

    private var cycleModePicker: some View {
        Picker("schedule_mode_title".localized, selection: $viewModel.isContinuous) {
            Text("schedule_mode_cycle".localized).tag(false)
            Text("continuous".localized).tag(true)
        }
        .pickerStyle(.segmented)
    }

    @ViewBuilder
    private var cycleFields: some View {
        if !viewModel.isContinuous {
            HStack(spacing: 10) {
                numberField("on_days".localized, text: $viewModel.daysOn)
                numberField("off_days".localized, text: $viewModel.daysOff)
            }
        }
    }

    @ViewBuilder
    private var weeklyControls: some View {
        Toggle("repeat_weekly".localized, isOn: $viewModel.isWeeklyRecurrenceEnabled)
            .tint(OAKPalette.accent)
        if viewModel.isWeeklyRecurrenceEnabled {
            weekdayPicker
            numberField("repeat_every".localized, text: $viewModel.intervalWeeks)
            Text(weeklySummaryText)
                .font(.caption)
                .oakSecondaryText()
        }
    }

    private var weekdayPicker: some View {
        HStack(spacing: 6) {
            ForEach(Array(weekdayLabels.enumerated()), id: \.offset) { index, label in
                weekdayButton(label: label, index: index)
            }
        }
    }

    private func weekdayButton(label: String, index: Int) -> some View {
        let isSelected = (viewModel.weekdaysMask & (1 << index)) != 0
        return Button { viewModel.toggleWeekday(bitIndex: index) } label: {
            Text(label)
                .font(.caption.weight(.semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
                .foregroundStyle(isSelected ? Color.white : Color.primary)
                .background(isSelected ? OAKPalette.accent : Color.secondary.opacity(0.10), in: Capsule())
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private var intervalControls: some View {
        Toggle("repeat_every_n_days".localized, isOn: $viewModel.isIntervalDaysEnabled)
            .tint(OAKPalette.accent)
        if viewModel.isIntervalDaysEnabled {
            numberField("interval_days_label".localized, text: $viewModel.intervalDays)
        }
    }

    private var durationField: some View {
        numberField("duration_months_label".localized, text: $viewModel.durationMonths)
    }

    private func numberField(_ title: String, text: Binding<String>) -> some View {
        TextField(title, text: text)
            .keyboardType(.numberPad)
            .supplementFieldStyle()
    }

    private var saveBar: some View {
        Button(action: save) {
            HStack(spacing: 8) {
                if viewModel.isSaving { ProgressView().tint(.white) }
                Text("save".localized).font(.headline)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 4)
        }
        .buttonStyle(.borderedProminent)
        .controlSize(.large)
        .disabled(isSaveDisabled)
        .padding(.horizontal, 18)
        .padding(.vertical, 12)
        .background(.ultraThinMaterial)
    }

    @ToolbarContentBuilder
    private var cancelToolbarItem: some ToolbarContent {
        ToolbarItem(placement: .cancellationAction) {
            Button("cancel".localized) { dismiss() }
        }
    }

    @ViewBuilder
    private var savingOverlay: some View {
        if viewModel.isSaving {
            Color.black.opacity(0.16)
                .ignoresSafeArea()
                .allowsHitTesting(true)
        }
    }

    private var errorBinding: Binding<Bool> {
        Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )
    }

    private var navigationTitle: String {
        (isEditing ? "edit_supplement_title" : "add_supplement_title").localized
    }

    private var isSaveDisabled: Bool {
        let name = viewModel.name.trimmingCharacters(in: .whitespacesAndNewlines)
        return name.isEmpty || TimeStrings.normalizeList(viewModel.intakeTimes).isEmpty || viewModel.isSaving
    }

    private var weekdayLabels: [String] {
        let symbols = Calendar.current.shortWeekdaySymbols
        guard let first = symbols.first else { return [] }
        return Array(symbols.dropFirst()) + [first]
    }

    private var weeklySummaryText: String {
        let days = weekdayLabels.enumerated().compactMap { index, label in
            (viewModel.weekdaysMask & (1 << index)) != 0 ? label : nil
        }
        let interval = max(1, Int(viewModel.intervalWeeks) ?? 1)
        let selectedDays = days.isEmpty ? "-" : days.joined(separator: ", ")
        return "\(selectedDays) - \(String(format: "every_x_weeks_format".localized, interval))"
    }

    private func suggestionCaption(_ suggestion: SupplementReference) -> String {
        guard let advice = suggestion.advice, !advice.isEmpty else {
            return String(format: "suggested_format".localized, suggestion.preferredTime)
        }
        return advice.localized
    }

    private func updateName(_ value: String) {
        if value.count > 100 { viewModel.name = String(value.prefix(100)) }
        Task { await viewModel.updateSuggestions() }
    }

    private func applyEntitlement() {
        let allowed = entitlementManager.canUse(.advancedCycles)
        viewModel.advancedCyclesAllowed = allowed
        guard !allowed, !isEditing else { return }
        viewModel.isContinuous = true
        viewModel.isWeeklyRecurrenceEnabled = false
        viewModel.isIntervalDaysEnabled = false
        viewModel.durationMonths = ""
    }

    private func save() {
        Task {
            guard let supplement = await viewModel.saveSupplement() else { return }
            onSave(supplement)
            dismiss()
        }
    }
}

private struct SupplementFormSection<Content: View>: View {
    let title: String
    let subtitle: String
    let systemImage: String
    let content: Content

    init(
        title: String,
        subtitle: String,
        systemImage: String,
        @ViewBuilder content: () -> Content
    ) {
        self.title = title
        self.subtitle = subtitle
        self.systemImage = systemImage
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .top, spacing: 11) {
                Image(systemName: systemImage)
                    .foregroundStyle(OAKPalette.accent)
                    .frame(width: 28, height: 28)
                    .background(OAKPalette.accent.opacity(0.10), in: RoundedRectangle(cornerRadius: 8))
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.headline)
                    Text(subtitle).font(.caption).oakSecondaryText()
                }
            }
            content
        }
        .padding(18)
        .oakCardStyle(.glass, cornerRadius: 22)
    }
}

private extension View {
    func supplementFieldStyle() -> some View {
        padding(.horizontal, 13)
            .padding(.vertical, 12)
            .background(Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 13))
    }
}

#Preview {
    AddSupplementPreview()
        .environment(EntitlementManager())
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
            let container = try ModelContainer(
                for: ClientProfile.self,
                UserSupplement.self,
                IntakeRecord.self,
                configurations: configuration
            )
            return container.mainContext
        } catch {
            return nil
        }
    }
}
