import SwiftUI
import SwiftData

/// Màn hình chính Dashboard trên iOS.
public struct HomeView: View {
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \UserSupplement.name) private var supplements: [UserSupplement]
    
    @State private var viewModel = HomeViewModel()
    @State private var updateService = UpdateService()
    @State private var isShowingAddSheet = false
    
    public init() {}
    
    public var body: some View {
        NavigationStack {
            List {
                Section("Cần uống hôm nay") {
                    if viewModel.activeSupplements.isEmpty {
                        Text("Hôm nay bạn không có lịch uống nào.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(IntakeTime.allCases.sorted(by: { $0.order < $1.order }), id: \.self) { time in
                            if let items = viewModel.activeSupplements[time] {
                                TimeGroupSection(time: time, supplements: items, onLog: viewModel.logIntake)
                            }
                        }
                    }
                }
                
                if !viewModel.restingSupplements.isEmpty {
                    Section("Đang trong chu kỳ nghỉ") {
                        ForEach(viewModel.restingSupplements) { info in
                            RestingSupplementRow(info: info)
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
    let time: IntakeTime
    let supplements: [UserSupplement]
    let onLog: (UserSupplement, ModelContext) -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(time.rawValue)
                .font(.caption)
                .fontWeight(.bold)
                .foregroundStyle(.blue)
            
            ForEach(supplements) { supplement in
                ActiveSupplementRow(supplement: supplement, onLog: onLog)
            }
        }
        .padding(.vertical, 4)
    }
}

/// Thành phần hiển thị chất đang hoạt động.
private struct ActiveSupplementRow: View {
    @Environment(\.modelContext) private var modelContext
    let supplement: UserSupplement
    let onLog: (UserSupplement, ModelContext) -> Void
    @State private var isTaken = false
    
    var body: some View {
        HStack {
            VStack(alignment: .leading) {
                Text(supplement.name)
                    .font(.headline)
                Text("Liều lượng: \(supplement.dailyDose)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Button {
                isTaken.toggle()
                if isTaken {
                    onLog(supplement, modelContext)
                }
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
