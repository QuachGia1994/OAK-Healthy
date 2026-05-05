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
                .navigationTitle("add_supplement_title")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("cancel") { dismiss() }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("save") {
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
        Section("basic_info_title") {
            TextField("name_hint", text: $viewModel.name)
                .onChange(of: viewModel.name) {
                    Task { await viewModel.updateSuggestions() }
                }
            
            suggestionsSection
            
            TextField("dose_hint", text: $viewModel.dailyDose)
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
                                Text(advice)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            } else {
                                Text(String(format: String(localized: "suggested_format"), suggestion.preferredTime))
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
        Section("schedule_cycle_title") {
            DatePicker("start_date", selection: $viewModel.startDate, displayedComponents: .date)
            DatePicker("intake_time", selection: $viewModel.selectedTime, displayedComponents: .hourAndMinute)
            Toggle("continuous", isOn: $viewModel.isContinuous)
            
            if !viewModel.isContinuous {
                HStack {
                    Text("on_days")
                    Spacer()
                    TextField("example_on_days", text: $viewModel.daysOn)
                        .keyboardType(.numberPad)
                        .multilineTextAlignment(.trailing)
                        .frame(width: 80)
                }
                
                HStack {
                    Text("off_days")
                    Spacer()
                    TextField("example_off_days", text: $viewModel.daysOff)
                        .keyboardType(.numberPad)
                        .multilineTextAlignment(.trailing)
                        .frame(width: 80)
                }
            }
            
            HStack {
                Text("duration")
                Spacer()
                TextField("unlimited", text: $viewModel.durationMonths)
                    .keyboardType(.numberPad)
                    .multilineTextAlignment(.trailing)
                    .frame(width: 80)
            }
            
            if !viewModel.durationMonths.isEmpty {
                Text("months")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
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
                Text("Preview unavailable")
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
