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
                backgroundGradient
                    .ignoresSafeArea()
                
                Form {
                    basicSection
                    cycleSection
                }
                .scrollContentBackground(.hidden)
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
                        .disabled(viewModel.name.isEmpty)
                    }
                }
            }
        }
    }
    
    private var backgroundGradient: LinearGradient {
        let colors: [Color] = colorScheme == .dark
            ? [Color(red: 0.08, green: 0.0, blue: 0.15), .black]
            : [Color(.systemGroupedBackground), Color(.systemBackground)]
        return LinearGradient(colors: colors, startPoint: .topLeading, endPoint: .bottomTrailing)
    }
    
    private var basicSection: some View {
        Section {
            TextField("name_hint".localized, text: $viewModel.name)
                .onChange(of: viewModel.name) {
                    Task { await viewModel.updateSuggestions() }
                }
            
            suggestionsSection
            
            TextField("dose_hint".localized, text: $viewModel.dailyDose)
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
                    }
                }
            }
        }
    }
    
    private var cycleSection: some View {
        Section {
            DatePicker("start_date".localized, selection: $viewModel.startDate, displayedComponents: .date)
            DatePicker("intake_time".localized, selection: $viewModel.selectedTime, displayedComponents: .hourAndMinute)
            weeklyRecurrenceControls
            Toggle("continuous".localized, isOn: $viewModel.isContinuous)
            
            if !viewModel.isContinuous {
                HStack {
                    Text("on_days".localized)
                    Spacer()
                    TextField("example_on_days".localized, text: $viewModel.daysOn)
                        .keyboardType(.numberPad)
                        .multilineTextAlignment(.trailing)
                        .frame(width: 80)
                }
                
                HStack {
                    Text("off_days".localized)
                    Spacer()
                    TextField("example_off_days".localized, text: $viewModel.daysOff)
                        .keyboardType(.numberPad)
                        .multilineTextAlignment(.trailing)
                        .frame(width: 80)
                }
            }
            
            HStack {
                Text("duration".localized)
                Spacer()
                TextField("unlimited".localized, text: $viewModel.durationMonths)
                    .keyboardType(.numberPad)
                    .multilineTextAlignment(.trailing)
                    .frame(width: 80)
            }
            
            if !viewModel.durationMonths.isEmpty {
                Text("months".localized)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        } header: {
            Text("schedule_cycle_title".localized)
        }
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
        ["T2", "T3", "T4", "T5", "T6", "T7", "CN"]
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
