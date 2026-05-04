import SwiftUI
import SwiftData

/// Màn hình thêm mới thực phẩm bổ sung.
public struct AddSupplementView: View {
    @State private var viewModel: AddSupplementViewModel
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var modelContext
    
    public var onSave: (UserSupplement) -> Void
    
    public init(modelContext: ModelContext, onSave: @escaping (UserSupplement) -> Void) {
        self.onSave = onSave
        _viewModel = State(initialValue: AddSupplementViewModel(modelContext: modelContext))
    }
    
    public var body: some View {
        NavigationStack {
            Form {
                Section("Thông tin cơ bản") {
                    TextField("Tên chất (VD: Vitamin D3)", text: $viewModel.name)
                        .onChange(of: viewModel.name) {
                            Task { await viewModel.updateSuggestions() }
                        }
                    
                    if !viewModel.suggestions.isEmpty {
                        ForEach(viewModel.suggestions) { suggestion in
                            Button {
                                viewModel.selectSuggestion(suggestion)
                            } label: {
                                HStack {
                                    VStack(alignment: .leading) {
                                        Text(suggestion.name)
                                            .font(.headline)
                                        Text("Gợi ý: \(suggestion.preferredTime.rawValue)")
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    Image(systemName: "plus.circle")
                                }
                            }
                        }
                    }
                    
                    TextField("Liều lượng hàng ngày (VD: 1000 IU)", text: $viewModel.dailyDose)
                }
                
                Section("Lịch trình & Chu kỳ") {
                    DatePicker("Ngày bắt đầu", selection: $viewModel.startDate, displayedComponents: .date)
                    
                    DatePicker("Giờ uống", selection: $viewModel.selectedTime, displayedComponents: .hourAndMinute)
                    
                    Toggle("Uống liên tục (Không nghỉ)", isOn: $viewModel.isContinuous)
                    
                    if !viewModel.isContinuous {
                        HStack {
                            Text("Số ngày uống (On Days)")
                            Spacer()
                            TextField("Ví dụ: 14", text: $viewModel.daysOn)
                                .keyboardType(.numberPad)
                                .multilineTextAlignment(.trailing)
                                .frame(width: 80)
                        }
                        
                        HStack {
                            Text("Số ngày nghỉ (Off Days)")
                            Spacer()
                            TextField("Ví dụ: 7", text: $viewModel.daysOff)
                                .keyboardType(.numberPad)
                                .multilineTextAlignment(.trailing)
                                .frame(width: 80)
                        }
                    }
                    
                    HStack {
                        Text("Tổng thời hạn (Duration)")
                        Spacer()
                        TextField("Vô thời hạn", text: $viewModel.durationMonths)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                            .frame(width: 80)
                    }
                    if !viewModel.durationMonths.isEmpty {
                        Text("Tháng")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle("Thêm chất mới")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Hủy") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Lưu") {
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

#Preview {
    AddSupplementView { _ in }
}
