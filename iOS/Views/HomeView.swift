import SwiftUI
import SwiftData

/// Màn hình chính Dashboard trên iOS.
public struct HomeView: View {
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \UserSupplement.name) private var supplements: [UserSupplement]
    
    @State private var viewModel = HomeViewModel()
    @State private var updateService = UpdateService()
    @State private var isShowingAddSheet = false
    @State private var editingSupplement: UserSupplement?
    
    public init() {}
    
    public var body: some View {
        NavigationStack {
            List {
                Section("Cần uống hôm nay") {
                    if viewModel.activeSupplements.isEmpty {
                        Text("Hôm nay bạn không có lịch uống nào.")
                            .foregroundStyle(.secondary)
                    } else {
                        // Sắp xếp theo giờ uống
                        let sortedTimes = viewModel.activeSupplements.keys.sorted()
                        ForEach(sortedTimes, id: \.self) { time in
                            if let items = viewModel.activeSupplements[time] {
                                TimeGroupSection(
                                    time: time,
                                    supplements: items,
                                    viewModel: viewModel,
                                    onEdit: { editingSupplement = $0 }
                                )
                            }
                        }
                    }
                }
                
                if !viewModel.restingSupplements.isEmpty {
                    Section("Đang trong chu kỳ nghỉ") {
                        ForEach(viewModel.restingSupplements) { info in
                            RestingSupplementRow(info: info, onEdit: { editingSupplement = $0 })
                                .swipeActions(edge: .trailing) {
                                    Button(role: .destructive) {
                                        viewModel.deleteSupplement(info.supplement, context: modelContext)
                                    } label: {
                                        Label("Xóa", systemImage: "trash")
                                    }
                                }
                                .swipeActions(edge: .leading) {
                                    Button {
                                        editingSupplement = info.supplement
                                    } label: {
                                        Label("Edit", systemImage: "pencil")
                                    }
                                    .tint(.orange)
                                }
                                .contextMenu {
                                    Button {
                                        editingSupplement = info.supplement
                                    } label: {
                                        Label("Chỉnh sửa", systemImage: "pencil")
                                    }
                                    
                                    Button(role: .destructive) {
                                        viewModel.deleteSupplement(info.supplement, context: modelContext)
                                    } label: {
                                        Label("Xóa", systemImage: "trash")
                                    }
                                }
                        }
                    }
                }
            }
            .navigationTitle("Dashboard")
            .toolbar {
                Button {
                    isShowingAddSheet = true
                } label: {
                    Image(systemName: "plus")
                }
            }
            .sheet(isPresented: $isShowingAddSheet) {
                AddSupplementView(modelContext: modelContext) { _ in
                    // Callback sau khi lưu thành công
                }
            }
            .sheet(item: $editingSupplement) { supplement in
                AddSupplementView(modelContext: modelContext, editingSupplement: supplement) { _ in
                }
            }
            .alert("Đã có phiên bản mới!", isPresented: $updateService.isUpdateAvailable) {
                if let url = URL(string: updateService.updateInfo?.updateUrl ?? "") {
                    Link("Cập nhật ngay", destination: url)
                }
                Button("Để sau", role: .cancel) { }
            } message: {
                Text("Hãy cập nhật để trải nghiệm những tính năng mới nhất và tăng cường bảo mật (v\(updateService.updateInfo?.version ?? "")).")
            }
            .task {
                await updateService.checkForUpdates()
            }
            .onAppear {
                viewModel.processSupplements(supplements)
            }
            .onChange(of: supplements) {
                viewModel.processSupplements(supplements)
            }
        }
    }
}

/// Thành phần hiển thị nhóm theo thời gian.
private struct TimeGroupSection: View {
    @Environment(\.modelContext) private var modelContext
    let time: String
    let supplements: [UserSupplement]
    let viewModel: HomeViewModel
    let onEdit: (UserSupplement) -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(time)
                .font(.caption)
                .fontWeight(.bold)
                .foregroundStyle(.blue)
            
            ForEach(supplements) { supplement in
                ActiveSupplementRow(
                    supplement: supplement, 
                    onToggle: viewModel.toggleIntake,
                    isTaken: viewModel.isTakenToday(supplement)
                )
                .swipeActions(edge: .leading) {
                    Button {
                        onEdit(supplement)
                    } label: {
                        Label("Edit", systemImage: "pencil")
                    }
                    .tint(.orange)
                }
                .swipeActions(edge: .trailing) {
                    Button(role: .destructive) {
                        viewModel.deleteSupplement(supplement, context: modelContext)
                    } label: {
                        Label("Xóa", systemImage: "trash")
                    }
                }
                .contextMenu {
                    Button {
                        onEdit(supplement)
                    } label: {
                        Label("Chỉnh sửa", systemImage: "pencil")
                    }
                    
                    Button(role: .destructive) {
                        viewModel.deleteSupplement(supplement, context: modelContext)
                    } label: {
                        Label("Xóa", systemImage: "trash")
                    }
                }
            }
        }
        .padding(.vertical, 4)
    }
}

/// Thành phần hiển thị chất đang hoạt động.
private struct ActiveSupplementRow: View {
    @Environment(\.modelContext) private var modelContext
    let supplement: UserSupplement
    let onToggle: (UserSupplement, ModelContext) -> Void
    let isTaken: Bool
    
    var body: some View {
        HStack {
            VStack(alignment: .leading) {
                Text(supplement.name)
                    .font(.headline)
                HStack {
                    Image(systemName: "clock")
                        .font(.caption2)
                    Text(supplement.intakeTime)
                        .font(.caption2)
                    Text("•")
                    Text("Liều lượng: \(supplement.dailyDose)")
                        .font(.caption)
                }
                .foregroundStyle(.secondary)
            }
            Spacer()
            Button {
                onToggle(supplement, modelContext)
            } label: {
                Image(systemName: isTaken ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(isTaken ? .green : .gray)
                    .font(.title2)
            }
        }
        .padding()
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}

/// Thành phần hiển thị chất đang nghỉ.
private struct RestingSupplementRow: View {
    let info: RestingSupplementInfo
    let onEdit: (UserSupplement) -> Void
    
    var body: some View {
        HStack {
            VStack(alignment: .leading) {
                Text(info.supplement.name)
                    .font(.headline)
                    .foregroundStyle(.secondary)
                Text("Nghỉ ngơi")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Text("Còn \(info.daysRemaining) ngày")
                .font(.caption)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(.secondary.opacity(0.2))
                .clipShape(Capsule())
        }
        .opacity(0.6)
    }
}

#Preview {
    HomeView()
        .modelContainer(for: UserSupplement.self, inMemory: true)
}
