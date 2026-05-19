import SwiftUI
import SwiftData

public struct SyncCenterView: View {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \ClientProfile.createdAt) private var clients: [ClientProfile]
    
    @AppStorage("isNotificationEnabledByUser") private var isNotificationEnabledByUser: Bool = false
    @AppStorage("isAutoSyncEnabled") private var isAutoSyncEnabled: Bool = false
    @AppStorage("cloudSyncHostedBinId") private var hostedBinId: String = ""
    @AppStorage("cloudSyncLinkedBinId") private var linkedBinId: String = ""
    @AppStorage("oakSafeModeEnabled") private var isSafeModeEnabled: Bool = false
    
    @State private var selectedTab: SyncCenterTab = .host
    @State private var isInputCodeVisible: Bool = false
    @State private var isCloudSyncLoading: Bool = false
    @State private var isBinIdVisible: Bool = false
    @State private var isRevokingBinId: Bool = false
    @State private var isShowingRevokeConfirm: Bool = false
    @State private var isShowingRehostConfirm: Bool = false
    @State private var isShowingRotateKeyConfirm: Bool = false
    @State private var isShowingDisableEncryptionConfirm: Bool = false
    @State private var isShowingImportKeyConfirm: Bool = false
    @State private var isShowingClearLogConfirm: Bool = false
    @State private var logQuery: String = ""
    @State private var logPhaseFilter: String = "ALL"
    @State private var syncPhase: SyncPhase = .idle
    
    @State private var importErrorMessage: String = ""
    @State private var showImportErrorAlert: Bool = false
    
    @State private var isCloudEncryptionEnabled: Bool = CloudSyncKeyManager.isEncryptionEnabled()
    @State private var exportedCloudSyncKey: String = ""
    @State private var importCloudSyncKeyInput: String = ""
    
    @State private var cachedSupplements: [UserSupplement] = []
    @State private var cachedRecords: [IntakeRecord] = []
    @State private var logEntries: [CloudSyncLogEntry] = []
    
    public let activeClientManager: ActiveClientManager
    
    public init(activeClientManager: ActiveClientManager) {
        self.activeClientManager = activeClientManager
    }
    
    public var body: some View {
        ZStack {
            backgroundGradient.ignoresSafeArea()
            List {
                onboardingSection
                statusSection
                tabSection
                encryptionSection
                logsSection
            }
            .scrollContentBackground(.hidden)
        }
        .navigationTitle("sync_center_title".localized)
        .navigationBarTitleDisplayMode(.inline)
        .task {
            DebugReporter.report("sync_center_task_boot", fields: [
                "currentClientId": activeClientManager.currentClientId?.uuidString ?? "",
                "clientsCount": String(clients.count)
            ])
            guard activeClientManager.currentClientId == nil else { return }
            guard let first = clients.first else { return }
            activeClientManager.setCurrentClientId(first.id)
        }
        .task(id: activeClientManager.currentClientId) {
            DebugReporter.report("sync_center_task_reload_caches", fields: [
                "clientId": activeClientManager.currentClientId?.uuidString ?? ""
            ])
            await reloadCaches()
        }
        .task(id: activeBinId) {
            DebugReporter.report("sync_center_task_load_logs", fields: [
                "binId": activeBinId
            ])
            logEntries = loadLogEntries(binId: activeBinId)
        }
        .task(id: isCloudEncryptionEnabled) {
            DebugReporter.report("sync_center_task_refresh_key", fields: [
                "enabled": String(isCloudEncryptionEnabled)
            ])
            await refreshExportedCloudKey()
        }
        .alert("sync_center_notice_title".localized, isPresented: $showImportErrorAlert) {
            Button("ok".localized) {}
        } message: {
            Text(importErrorMessage)
        }
    }
    
    @ViewBuilder
    private var onboardingSection: some View {
        Section {
            Toggle("sync_center_auto_sync".localized, isOn: $isAutoSyncEnabled)
                .onChange(of: isAutoSyncEnabled) {
                    DebugReporter.report("sync_center_auto_sync_changed", fields: [
                        "enabled": String(isAutoSyncEnabled)
                    ])
                    if isAutoSyncEnabled {
                        CloudSyncAutoSync.startRealtimeSync(
                            modelContext: modelContext,
                            activeClientManager: activeClientManager
                        )
                        return
                    }
                    CloudSyncAutoSync.stopRealtimeSync()
                }
            
            VStack(alignment: .leading, spacing: 6) {
                if selectedTab == .host {
                    Text("sync_center_onboarding_host_1".localized)
                    Text("sync_center_onboarding_host_2".localized)
                    Text("sync_center_onboarding_host_3".localized)
                } else {
                    Text("sync_center_onboarding_link_1".localized)
                    Text("sync_center_onboarding_link_2".localized)
                    Text("sync_center_onboarding_link_3".localized)
                }
            }
            .font(.caption)
            .foregroundStyle(.secondary)
        } header: {
            Text("sync_center_onboarding_header".localized)
        }
        .listRowBackground(glassRowBackground)
    }
    
    @ViewBuilder
    private var statusSection: some View {
        Section {
            if !activeBinId.isEmpty {
                let lastSyncKey = "cloudSyncLastSyncEpochMs_\(activeBinId)"
                let lastAttemptKey = "cloudSyncLastAttemptEpochMs_\(activeBinId)"
                let retryKey = "cloudSyncConflictRetryCount_\(activeBinId)"
                let bytesDownKey = "cloudSyncBytesDownloaded_\(activeBinId)"
                let bytesUpKey = "cloudSyncBytesUploaded_\(activeBinId)"
                let pullMsKey = "cloudSyncPullMs_\(activeBinId)"
                let mergeMsKey = "cloudSyncMergeMs_\(activeBinId)"
                let pushMsKey = "cloudSyncPushMs_\(activeBinId)"
                let totalMsKey = "cloudSyncTotalMs_\(activeBinId)"
                let lastErrorKey = "cloudSyncLastError_\(activeBinId)"
                let lastSyncEpochMs = Int64(UserDefaults.standard.double(forKey: lastSyncKey))
                let lastAttemptEpochMs = Int64(UserDefaults.standard.double(forKey: lastAttemptKey))
                let retryCount = UserDefaults.standard.integer(forKey: retryKey)
                let bytesDown = UserDefaults.standard.integer(forKey: bytesDownKey)
                let bytesUp = UserDefaults.standard.integer(forKey: bytesUpKey)
                let pullMs = UserDefaults.standard.integer(forKey: pullMsKey)
                let mergeMs = UserDefaults.standard.integer(forKey: mergeMsKey)
                let pushMs = UserDefaults.standard.integer(forKey: pushMsKey)
                let totalMs = UserDefaults.standard.integer(forKey: totalMsKey)
                let hasPendingChanges = activeClientManager.currentClientId.map {
                    hasLocalChangesSince(clientId: $0, lastSyncEpochMs: lastSyncEpochMs)
                } ?? false
                let lastError = (UserDefaults.standard.string(forKey: lastErrorKey) ?? "")
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                
                VStack(alignment: .leading, spacing: 6) {
                    if lastSyncEpochMs > 0 {
                        let date = Date(timeIntervalSince1970: Double(lastSyncEpochMs) / 1000.0)
                        Text(
                            String(
                                format: "sync_center_last_sync_format".localized,
                                date.formatted(date: .abbreviated, time: .shortened)
                            )
                        )
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    } else {
                        Text("sync_center_no_sync_yet".localized)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    
                    if lastAttemptEpochMs > 0 {
                        let date = Date(timeIntervalSince1970: Double(lastAttemptEpochMs) / 1000.0)
                        Text(
                            String(
                                format: "sync_center_last_attempt_format".localized,
                                date.formatted(date: .abbreviated, time: .shortened)
                            )
                        )
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    
                    Text(String(format: "sync_center_phase_format".localized, syncPhase.text))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    
                    HStack(spacing: 10) {
                        SyncStepChip(label: "sync_center_chip_pull".localized, done: syncPhase != .idle)
                        SyncStepChip(label: "sync_center_chip_merge".localized, done: syncPhase.isMergeDone)
                        SyncStepChip(label: "sync_center_chip_push".localized, done: syncPhase == .done)
                    }
                    
                    Text(
                        hasPendingChanges
                            ? "sync_center_pending_changes".localized
                            : "sync_center_no_pending_changes".localized
                    )
                        .font(.caption)
                        .foregroundStyle(hasPendingChanges ? .orange : .secondary)
                    
                    if retryCount > 0 {
                        Text(String(format: "sync_center_conflict_retry_format".localized, retryCount))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    
                    if bytesDown > 0 || bytesUp > 0 {
                        Text(
                            String(
                                format: "sync_center_bytes_format".localized,
                                formatBytes(bytesDown),
                                formatBytes(bytesUp)
                            )
                        )
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    
                    if pullMs > 0 || mergeMs > 0 || pushMs > 0 || totalMs > 0 {
                        Text(
                            String(
                                format: "sync_center_timings_format".localized,
                                pullMs,
                                mergeMs,
                                pushMs,
                                totalMs
                            )
                        )
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(2)
                    }
                    
                    if !lastError.isEmpty {
                        Text(String(format: "sync_center_last_error_format".localized, lastError))
                            .font(.caption)
                            .foregroundStyle(.red)
                            .lineLimit(3)
                        let hint = isCloudEncryptionEnabled &&
                            (lastError.localizedCaseInsensitiveContains("Missing cloud sync key") ||
                             lastError.localizedCaseInsensitiveContains("Decrypt failed"))
                        if hint {
                            Text("sync_center_hint_missing_key".localized)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .lineLimit(3)
                        }
                    }
                }
                
                Button("sync_center_sync_now".localized) {
                    Task { await syncNow() }
                }
                .disabled(isCloudSyncLoading || activeClientManager.currentClientId == nil)
            } else {
                Text("sync_center_no_link_code".localized)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        } header: {
            Text("sync_center_status_header".localized)
        }
        .listRowBackground(glassRowBackground)
    }
    
    @ViewBuilder
    private var tabSection: some View {
        Section {
            Picker("", selection: $selectedTab) {
                Text("sync_center_tab_host".localized).tag(SyncCenterTab.host)
                Text("sync_center_tab_link".localized).tag(SyncCenterTab.link)
            }
            .pickerStyle(.segmented)
            
            switch selectedTab {
            case .host:
                hostTab
            case .link:
                linkTab
            }
        } header: {
            Text("sync_center_device_header".localized)
        }
        .listRowBackground(glassRowBackground)
    }
    
    @ViewBuilder
    private var hostTab: some View {
        Button("sync_center_host_action".localized) {
            let existing = hostedBinId.trimmingCharacters(in: .whitespacesAndNewlines)
            if !existing.isEmpty {
                isShowingRehostConfirm = true
                return
            }
            Task { await hostData() }
        }
        .disabled(isCloudSyncLoading)
        .confirmationDialog(
            "sync_center_rehost_confirm".localized,
            isPresented: $isShowingRehostConfirm,
            titleVisibility: .visible
        ) {
            Button("sync_center_rehost_confirm_action".localized, role: .destructive) {
                Task { await hostData() }
            }
            Button("cancel".localized, role: .cancel) {}
        }
        
        if isCloudSyncLoading {
            ProgressView()
        }
        
        if !hostedBinId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            let binId = hostedBinId.trimmingCharacters(in: .whitespacesAndNewlines)
            HStack(alignment: .center, spacing: 8) {
                Text("sync_center_your_link_code".localized)
                    .foregroundStyle(.secondary)
                
                Text(isBinIdVisible ? binId : String(repeating: "•", count: 24))
                    .font(.title3)
                    .fontWeight(.bold)
                    .textSelection(.enabled)
                
                Button(action: { isBinIdVisible.toggle() }) {
                    Image(systemName: isBinIdVisible ? "eye.slash" : "eye")
                        .foregroundStyle(.gray)
                }
                .buttonStyle(.borderless)
            }
            
            Button(role: .destructive) {
                isShowingRevokeConfirm = true
            } label: {
                if isRevokingBinId {
                    ProgressView()
                } else {
                    Text("sync_center_revoke_action".localized)
                }
            }
            .disabled(isCloudSyncLoading || isRevokingBinId)
            .confirmationDialog(
                "sync_center_revoke_confirm".localized,
                isPresented: $isShowingRevokeConfirm,
                titleVisibility: .visible
            ) {
                Button("sync_center_revoke_action".localized, role: .destructive) {
                    Task { await revokeHostedBin() }
                }
                Button("cancel".localized, role: .cancel) {}
            }
        } else {
            Text("sync_center_host_hint".localized)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }
    
    @ViewBuilder
    private var linkTab: some View {
        HStack(spacing: 8) {
            if isInputCodeVisible {
                TextField("sync_center_link_code_placeholder".localized, text: $linkedBinId)
            } else {
                SecureField("sync_center_link_code_placeholder".localized, text: $linkedBinId)
            }
            Button(action: { isInputCodeVisible.toggle() }) {
                Image(systemName: isInputCodeVisible ? "eye.slash" : "eye")
                    .foregroundStyle(.gray)
            }
            .buttonStyle(.borderless)
        }
        .textInputAutocapitalization(.never)
        .autocorrectionDisabled()
        
        Button("sync_center_download_action".localized) {
            Task { await receiveData() }
        }
        .disabled(isCloudSyncLoading)
    }
    
    @ViewBuilder
    private var encryptionSection: some View {
        Section {
            Toggle(
                "sync_center_encryption_toggle".localized,
                isOn: Binding(
                    get: { isCloudEncryptionEnabled },
                    set: { newValue in
                        guard newValue != isCloudEncryptionEnabled else { return }
                        if newValue {
                            isCloudEncryptionEnabled = true
                            Task { await applyCloudEncryptionSetting(enabled: true) }
                            return
                        }
                        isShowingDisableEncryptionConfirm = true
                    }
                )
            )
            .confirmationDialog(
                "sync_center_disable_encryption_confirm".localized,
                isPresented: $isShowingDisableEncryptionConfirm,
                titleVisibility: .visible
            ) {
                Button("sync_center_disable_encryption_action".localized, role: .destructive) {
                    isCloudEncryptionEnabled = false
                    Task { await applyCloudEncryptionSetting(enabled: false) }
                }
                Button("sync_center_keep_encryption_action".localized, role: .cancel) {}
            }
            
            if isCloudEncryptionEnabled {
                if !exportedCloudSyncKey.isEmpty {
                    Text("sync_center_export_key_label".localized)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(exportedCloudSyncKey)
                        .font(.caption)
                        .textSelection(.enabled)
                        .foregroundStyle(.secondary)
                }
                
                HStack(spacing: 12) {
                    Button("sync_center_rotate_key_action".localized) { isShowingRotateKeyConfirm = true }
                    .disabled(isCloudSyncLoading)
                    .confirmationDialog(
                        "sync_center_rotate_key_confirm".localized,
                        isPresented: $isShowingRotateKeyConfirm,
                        titleVisibility: .visible
                    ) {
                        Button("sync_center_rotate_key_action".localized, role: .destructive) {
                            Task { await rotateCloudSyncKey() }
                        }
                        Button("cancel".localized, role: .cancel) {}
                    }
                    
                    Button("sync_center_refresh_key_action".localized) {
                        Task { await refreshExportedCloudKey() }
                    }
                }
                
                TextField("sync_center_import_key_placeholder".localized, text: $importCloudSyncKeyInput)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .textFieldStyle(.roundedBorder)
                
                Button("sync_center_import_key_action".localized) {
                    isShowingImportKeyConfirm = true
                }
                .disabled(importCloudSyncKeyInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                .confirmationDialog(
                    importKeyConfirmText,
                    isPresented: $isShowingImportKeyConfirm,
                    titleVisibility: .visible
                ) {
                    Button("sync_center_import_key_action".localized, role: .destructive) {
                        Task { await importCloudSyncKey() }
                    }
                    Button("cancel".localized, role: .cancel) {}
                }
            } else {
                Text("sync_center_encryption_off_hint".localized)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        } header: {
            Text("sync_center_security_header".localized)
        }
        .listRowBackground(glassRowBackground)
    }
    
    @ViewBuilder
    private var logsSection: some View {
        Section {
            if activeBinId.isEmpty {
                Text("sync_center_logs_hint".localized)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else {
                TextField("sync_center_logs_search_placeholder".localized, text: $logQuery)
                    .textFieldStyle(.roundedBorder)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                
                Picker("sync_center_logs_phase".localized, selection: $logPhaseFilter) {
                    Text("sync_center_logs_phase_all".localized).tag("ALL")
                    ForEach(availableLogPhases, id: \.self) { phase in
                        Text(phase).tag(phase)
                    }
                }
                .pickerStyle(.menu)
                .font(.caption)
                
                HStack {
                    ShareLink(item: rawLogJsonString) {
                        Text("sync_center_logs_export".localized)
                    }
                    .font(.caption)
                    
                    Spacer()
                    Button(role: .destructive) { isShowingClearLogConfirm = true } label: {
                        Text("sync_center_logs_clear".localized)
                    }
                    .confirmationDialog(
                        "sync_center_clear_log_confirm".localized,
                        isPresented: $isShowingClearLogConfirm,
                        titleVisibility: .visible
                    ) {
                        Button("sync_center_clear_log_action".localized, role: .destructive) {
                            clearLogs()
                        }
                        Button("cancel".localized, role: .cancel) {}
                    }
                }
                
                if filteredLogEntries.isEmpty {
                    Text("sync_center_no_logs".localized)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(filteredLogEntries) { entry in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(entry.title)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            Text(entry.message)
                                .font(.caption)
                        }
                        .padding(.vertical, 2)
                    }
                }
            }
        } header: {
            Text("sync_center_logs_header".localized)
        }
        .listRowBackground(glassRowBackground)
    }
    
    private var availableLogPhases: [String] {
        Array(Set(logEntries.map(\.phase))).sorted()
    }
    
    private var filteredLogEntries: [CloudSyncLogEntry] {
        let q = logQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        return logEntries.filter { entry in
            let phaseOk = logPhaseFilter == "ALL" || entry.phase == logPhaseFilter
            if !phaseOk { return false }
            if q.isEmpty { return true }
            return entry.phase.localizedCaseInsensitiveContains(q) ||
                entry.message.localizedCaseInsensitiveContains(q) ||
                entry.title.localizedCaseInsensitiveContains(q)
        }
    }
    
    private var rawLogJsonString: String {
        let id = activeBinId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !id.isEmpty else { return "[]" }
        let key = "cloudSyncLog_\(id)"
        guard let data = UserDefaults.standard.data(forKey: key) else { return "[]" }
        return String(data: data, encoding: .utf8) ?? "[]"
    }
    
    private var activeBinId: String {
        let hosted = hostedBinId.trimmingCharacters(in: .whitespacesAndNewlines)
        if !hosted.isEmpty { return hosted }
        return linkedBinId.trimmingCharacters(in: .whitespacesAndNewlines)
    }
    
    private var backgroundGradient: LinearGradient {
        let colors: [Color] = colorScheme == .dark
            ? [Color(red: 0.08, green: 0.0, blue: 0.15), .black]
            : [Color(.systemGroupedBackground), Color(.systemBackground)]
        return LinearGradient(colors: colors, startPoint: .topLeading, endPoint: .bottomTrailing)
    }
    
    private var glassRowBackground: some View {
        Color.clear.background(.ultraThinMaterial)
    }
    
    private var importKeyConfirmText: String {
        let raw = importCloudSyncKeyInput.trimmingCharacters(in: .whitespacesAndNewlines)
        let parts = raw.split(separator: ":", maxSplits: 1).map { String($0) }
        let keyId = parts.first?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if keyId.isEmpty { return "sync_center_import_key_confirm_invalid".localized }
        let current = (UserDefaults.standard.string(forKey: "cloudSyncEncCurrentKeyId") ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let previous = (UserDefaults.standard.string(forKey: "cloudSyncEncPreviousKeyId") ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if keyId == current {
            return String(format: "sync_center_import_key_confirm_same_current_format".localized, keyId)
        }
        if keyId == previous {
            return String(format: "sync_center_import_key_confirm_same_previous_format".localized, keyId)
        }
        return String(format: "sync_center_import_key_confirm_format".localized, keyId)
    }
    
    private func hostData() async {
        guard activeClientManager.currentClientId != nil else {
            showToast("sync_center_toast_missing_client".localized)
            return
        }
        await withLoading {
            await reloadCaches()
            isBinIdVisible = false
            do {
                let old = hostedBinId.trimmingCharacters(in: .whitespacesAndNewlines)
                try await revokeOldHostedBinIfNeeded(oldBinId: old)
                let newId = try await uploadHostedBackup()
                hostedBinId = newId
                appendLog(binId: newId, phase: "HOST", message: "DONE")
                showToast("sync_center_toast_host_success".localized)
            } catch {
                showToast(String(format: "sync_center_toast_host_failed_format".localized, error.localizedDescription))
            }
        }
    }
    
    private func revokeHostedBin() async {
        let binId = hostedBinId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !binId.isEmpty else { return }
        
        isRevokingBinId = true
        defer { isRevokingBinId = false }
        
        do {
            appendLog(binId: binId, phase: "HOST", message: "REVOKE START")
            let stack = (UserDefaults.standard.string(forKey: "cloudSyncStackBinId_\(binId)") ?? "")
                .trimmingCharacters(in: .whitespacesAndNewlines)
            let history = (UserDefaults.standard.string(forKey: "cloudSyncHistoryBinId_\(binId)") ?? "")
                .trimmingCharacters(in: .whitespacesAndNewlines)
            if !stack.isEmpty { try? await CloudSyncManager.shared.deleteBackup(binId: stack) }
            if !history.isEmpty { try? await CloudSyncManager.shared.deleteBackup(binId: history) }
            try await CloudSyncManager.shared.deleteBackup(binId: binId)
            hostedBinId = ""
            isBinIdVisible = false
            appendLog(binId: binId, phase: "HOST", message: "REVOKE DONE")
            showToast("sync_center_toast_revoke_done".localized)
        } catch {
            appendLog(binId: binId, phase: "HOST", message: "REVOKE ERROR: \(error.localizedDescription)")
            showToast(String(format: "sync_center_toast_revoke_failed_format".localized, error.localizedDescription))
        }
    }
    
    @MainActor
    private func receiveData() async {
        guard let clientId = activeClientManager.currentClientId else {
            showToast("sync_center_toast_missing_client".localized)
            return
        }
        let binId = linkedBinId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !binId.isEmpty else {
            showToast("sync_center_toast_missing_link_code".localized)
            return
        }
        await withLoading {
            await reloadCaches()
            guard let client = clients.first(where: { $0.id == clientId }) ?? clients.first else {
                showToast("sync_center_toast_missing_client".localized)
                return
            }
            await runSyncFlow(binId: binId, client: client, label: "LINK")
        }
    }
    
    @MainActor
    private func syncNow() async {
        guard let clientId = activeClientManager.currentClientId else { return }
        let id = activeBinId
        guard !id.isEmpty else { return }
        guard let client = clients.first(where: { $0.id == clientId }) ?? clients.first else { return }
        await withLoading {
            await reloadCaches()
            await runSyncFlow(binId: id, client: client, label: "MANUAL")
        }
    }
    
    @MainActor
    private func syncTwoWay(binId: String, client: ClientProfile) async throws {
        let manifestId = binId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !manifestId.isEmpty else { throw CloudSyncError.invalidBinId }
        let keys = SyncKeys(binId: manifestId)
        let startedAt = Date()
        let lastSyncEpochMs = Int64(UserDefaults.standard.double(forKey: keys.lastSync))
        let localStackChanged = hasLocalStackChangesSince(clientId: client.id, lastSyncEpochMs: lastSyncEpochMs)
        let localHistoryChanged = hasLocalHistoryChangesSince(clientId: client.id, lastSyncEpochMs: lastSyncEpochMs)
        
        do {
            resetSyncMetrics(keys: keys)
            markAttempt(keys: keys)
            syncPhase = .pulling
            appendLog(binId: manifestId, phase: "PULL", message: "START")
            let pullStartedAt = Date()
            let parts: CloudSyncManifest
            do {
                parts = try await resolveManifestParts(manifestId: manifestId)
            } catch CloudSyncError.invalidResponse {
                try await syncTwoWayLegacy(binId: manifestId, client: client, keys: keys, startedAt: startedAt, lastSyncEpochMs: lastSyncEpochMs)
                return
            }
            let stackData = try await CloudSyncManager.shared.downloadBackupIfChanged(binId: parts.stackBinId)
            let historyData = try await CloudSyncManager.shared.downloadBackupIfChanged(binId: parts.historyBinId)
            UserDefaults.standard.set(Int(pullStartedAt.distance(to: Date()) * 1000), forKey: keys.pullMs)
            UserDefaults.standard.set((stackData?.count ?? 0) + (historyData?.count ?? 0), forKey: keys.bytesDown)
            
            if stackData != nil || historyData != nil {
                syncPhase = .merging
                appendLog(binId: manifestId, phase: "MERGE", message: "START")
                let mergeStartedAt = Date()
                if let stackData { try SupplementExportCodec.mergeBackup(data: stackData, client: client, context: modelContext) }
                if let historyData { try SupplementExportCodec.mergeBackup(data: historyData, client: client, context: modelContext) }
                UserDefaults.standard.set(Int(mergeStartedAt.distance(to: Date()) * 1000), forKey: keys.mergeMs)
                appendLog(binId: manifestId, phase: "MERGE", message: "DONE")
            }
            
            if !localStackChanged && !localHistoryChanged {
                finalizeSuccess(keys: keys, startedAt: startedAt)
                return
            }
            
            syncPhase = .pushing
            appendLog(binId: manifestId, phase: "PUSH", message: "START")
            let pushStartedAt = Date()
            var bytesUp = 0
            if localStackChanged {
                let payload = try SupplementExportCodec.encodeBackup(supplements: cachedSupplements, records: [])
                bytesUp += payload.count
                try await upsertWithConflictRetry(
                    binId: parts.stackBinId,
                    client: client,
                    payload: payload,
                    retryPayload: { try SupplementExportCodec.encodeBackup(supplements: cachedSupplements, records: []) },
                    keys: keys
                )
            }
            if localHistoryChanged {
                let payload = try SupplementExportCodec.encodeBackup(supplements: [], records: cachedRecords)
                bytesUp += payload.count
                try await upsertWithConflictRetry(
                    binId: parts.historyBinId,
                    client: client,
                    payload: payload,
                    retryPayload: { try SupplementExportCodec.encodeBackup(supplements: [], records: cachedRecords) },
                    keys: keys
                )
            }
            UserDefaults.standard.set(bytesUp, forKey: keys.bytesUp)
            UserDefaults.standard.set(Int(pushStartedAt.distance(to: Date()) * 1000), forKey: keys.pushMs)
            appendLog(binId: manifestId, phase: "PUSH", message: "DONE")
            finalizeSuccess(keys: keys, startedAt: startedAt)
        } catch {
            finalizeError(keys: keys, error: error)
            throw error
        }
    }

    @MainActor
    private func resolveManifestParts(manifestId: String) async throws -> CloudSyncManifest {
        let stackKey = "cloudSyncStackBinId_\(manifestId)"
        let historyKey = "cloudSyncHistoryBinId_\(manifestId)"
        let storedStack = (UserDefaults.standard.string(forKey: stackKey) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let storedHistory = (UserDefaults.standard.string(forKey: historyKey) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if !storedStack.isEmpty, !storedHistory.isEmpty {
            return CloudSyncManifest(v: 1, stackBinId: storedStack, historyBinId: storedHistory)
        }
        let manifestData = try await CloudSyncManager.shared.downloadBackup(binId: manifestId)
        guard let decoded = CloudSyncManifestCodec.decode(manifestData) else { throw CloudSyncError.invalidResponse }
        let stackId = decoded.stackBinId.trimmingCharacters(in: .whitespacesAndNewlines)
        let historyId = decoded.historyBinId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !stackId.isEmpty, !historyId.isEmpty else { throw CloudSyncError.invalidResponse }
        UserDefaults.standard.set(stackId, forKey: stackKey)
        UserDefaults.standard.set(historyId, forKey: historyKey)
        return decoded
    }

    @MainActor
    private func syncTwoWayLegacy(
        binId: String,
        client: ClientProfile,
        keys: SyncKeys,
        startedAt: Date,
        lastSyncEpochMs: Int64
    ) async throws {
        let localChanged = hasLocalChangesSince(clientId: client.id, lastSyncEpochMs: lastSyncEpochMs)
        let pullStartedAt = Date()
        let downloaded = try await CloudSyncManager.shared.downloadBackupIfChanged(binId: binId)
        UserDefaults.standard.set(Int(pullStartedAt.distance(to: Date()) * 1000), forKey: keys.pullMs)
        UserDefaults.standard.set(downloaded?.count ?? 0, forKey: keys.bytesDown)
        if let downloaded {
            syncPhase = .merging
            let mergeStartedAt = Date()
            try SupplementExportCodec.mergeBackup(data: downloaded, client: client, context: modelContext)
            UserDefaults.standard.set(Int(mergeStartedAt.distance(to: Date()) * 1000), forKey: keys.mergeMs)
        }
        guard localChanged else {
            finalizeSuccess(keys: keys, startedAt: startedAt)
            return
        }
        syncPhase = .pushing
        let pushStartedAt = Date()
        let payload = try SupplementExportCodec.encodeBackup(supplements: cachedSupplements, records: cachedRecords)
        UserDefaults.standard.set(payload.count, forKey: keys.bytesUp)
        try await upsertWithConflictRetry(
            binId: binId,
            client: client,
            payload: payload,
            retryPayload: { try SupplementExportCodec.encodeBackup(supplements: cachedSupplements, records: cachedRecords) },
            keys: keys
        )
        UserDefaults.standard.set(Int(pushStartedAt.distance(to: Date()) * 1000), forKey: keys.pushMs)
        finalizeSuccess(keys: keys, startedAt: startedAt)
    }
    
    private func hasLocalChangesSince(clientId: UUID, lastSyncEpochMs: Int64) -> Bool {
        guard lastSyncEpochMs > 0 else { return true }
        if hasLocalStackChangesSince(clientId: clientId, lastSyncEpochMs: lastSyncEpochMs) { return true }
        return hasLocalHistoryChangesSince(clientId: clientId, lastSyncEpochMs: lastSyncEpochMs)
    }
    
    private func hasLocalStackChangesSince(clientId: UUID, lastSyncEpochMs: Int64) -> Bool {
        guard lastSyncEpochMs > 0 else { return true }
        do {
            var descriptor = FetchDescriptor<UserSupplement>(
                predicate: #Predicate {
                    $0.updatedAtEpochMs > lastSyncEpochMs ||
                        ($0.deletedAtEpochMs != nil && $0.deletedAtEpochMs! > lastSyncEpochMs)
                }
            )
            descriptor.fetchLimit = 50
            let changed = try modelContext.fetch(descriptor)
            return changed.contains(where: { $0.client?.id == clientId })
        } catch {
            return true
        }
    }
    
    private func hasLocalHistoryChangesSince(clientId: UUID, lastSyncEpochMs: Int64) -> Bool {
        guard lastSyncEpochMs > 0 else { return true }
        do {
            var descriptor = FetchDescriptor<IntakeRecord>(
                predicate: #Predicate { $0.updatedAtEpochMs > lastSyncEpochMs },
                sortBy: [SortDescriptor(\IntakeRecord.updatedAtEpochMs, order: .reverse)]
            )
            descriptor.fetchLimit = 100
            let changed = try modelContext.fetch(descriptor)
            return changed.contains(where: { $0.supplement?.client?.id == clientId })
        } catch {
            return true
        }
    }

    @MainActor
    private func withLoading(_ work: () async -> Void) async {
        guard !isCloudSyncLoading else { return }
        isCloudSyncLoading = true
        defer { isCloudSyncLoading = false }
        await work()
    }
    
    @MainActor
    private func revokeOldHostedBinIfNeeded(oldBinId: String) async throws {
        guard !oldBinId.isEmpty else { return }
        let stack = (UserDefaults.standard.string(forKey: "cloudSyncStackBinId_\(oldBinId)") ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let history = (UserDefaults.standard.string(forKey: "cloudSyncHistoryBinId_\(oldBinId)") ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        if !stack.isEmpty { try? await CloudSyncManager.shared.deleteBackup(binId: stack) }
        if !history.isEmpty { try? await CloudSyncManager.shared.deleteBackup(binId: history) }
        try await CloudSyncManager.shared.deleteBackup(binId: oldBinId)
        hostedBinId = ""
    }
    
    @MainActor
    private func uploadHostedBackup() async throws -> String {
        let stackPayload = try SupplementExportCodec.encodeBackup(supplements: cachedSupplements, records: [])
        let historyPayload = try SupplementExportCodec.encodeBackup(supplements: [], records: cachedRecords)
        let stackId = try await CloudSyncManager.shared.uploadBackup(jsonData: stackPayload)
        let historyId = try await CloudSyncManager.shared.uploadBackup(jsonData: historyPayload)
        guard let manifest = CloudSyncManifestCodec.encode(stackBinId: stackId, historyBinId: historyId) else {
            throw CloudSyncError.invalidResponse
        }
        let manifestId = try await CloudSyncManager.shared.uploadBackup(jsonData: manifest)
        UserDefaults.standard.set(stackId, forKey: "cloudSyncStackBinId_\(manifestId)")
        UserDefaults.standard.set(historyId, forKey: "cloudSyncHistoryBinId_\(manifestId)")
        UserDefaults.standard.set(Double(Date().timeIntervalSince1970 * 1000), forKey: "cloudSyncLastSyncEpochMs_\(manifestId)")
        return manifestId
    }
    
    @MainActor
    private func runSyncFlow(binId: String, client: ClientProfile, label: String) async {
        do {
            appendLog(binId: binId, phase: "SYNC", message: "\(label) START")
            try await syncTwoWay(binId: binId, client: client)
            activeClientManager.setCurrentClientId(client.id)
            isSafeModeEnabled = false
            await rescheduleNotificationsIfEnabled()
            appendLog(binId: binId, phase: "SYNC", message: "\(label) DONE")
            showToast("sync_center_toast_sync_success".localized)
        } catch {
            appendLog(binId: binId, phase: "SYNC", message: "\(label) ERROR: \(error.localizedDescription)")
            showToast(String(format: "sync_center_toast_sync_failed_format".localized, error.localizedDescription))
        }
    }
    
    @MainActor
    private func rescheduleNotificationsIfEnabled() async {
        guard isNotificationEnabledByUser else { return }
        let center = UNUserNotificationCenter.current()
        do {
            let granted = try await center.requestAuthorization(options: [.alert, .sound, .badge])
            guard granted else { return }
        } catch {
            return
        }
        let active = cachedSupplements.filter { $0.deletedAtEpochMs == nil }
        await NotificationService.shared.scheduleAll(supplements: active)
    }
    
    @MainActor
    private func resetSyncMetrics(keys: SyncKeys) {
        UserDefaults.standard.set(0, forKey: keys.bytesDown)
        UserDefaults.standard.set(0, forKey: keys.bytesUp)
        UserDefaults.standard.set(0, forKey: keys.pullMs)
        UserDefaults.standard.set(0, forKey: keys.mergeMs)
        UserDefaults.standard.set(0, forKey: keys.pushMs)
        UserDefaults.standard.set(0, forKey: keys.totalMs)
        UserDefaults.standard.set(0, forKey: keys.retryCount)
    }
    
    @MainActor
    private func markAttempt(keys: SyncKeys) {
        let now = Double(Date().timeIntervalSince1970 * 1000)
        UserDefaults.standard.set(now, forKey: keys.lastAttempt)
    }
    
    @MainActor
    private func upsertWithConflictRetry(
        binId: String,
        client: ClientProfile,
        payload: Data,
        retryPayload: () throws -> Data,
        keys: SyncKeys
    ) async throws {
        do {
            let etagKey = "cloudSyncEtag_\(binId)"
            let etag = UserDefaults.standard.string(forKey: etagKey)
            try await CloudSyncManager.shared.upsertBackup(binId: binId, jsonData: payload, ifMatchEtag: etag)
        } catch CloudSyncError.serverError(let statusCode, _) where statusCode == 409 || statusCode == 412 {
            syncPhase = .retryingConflict
            UserDefaults.standard.set(1, forKey: keys.retryCount)
            appendLog(binId: binId, phase: "CONFLICT", message: "RETRY START")
            let latest = try await CloudSyncManager.shared.downloadBackup(binId: binId)
            try SupplementExportCodec.mergeBackup(data: latest, client: client, context: modelContext)
            let payload = try retryPayload()
            let etagKey = "cloudSyncEtag_\(binId)"
            let retryEtag = UserDefaults.standard.string(forKey: etagKey)
            try await CloudSyncManager.shared.upsertBackup(binId: binId, jsonData: payload, ifMatchEtag: retryEtag)
        }
    }
    
    @MainActor
    private func finalizeSuccess(keys: SyncKeys, startedAt: Date) {
        UserDefaults.standard.removeObject(forKey: keys.lastError)
        UserDefaults.standard.set(Double(Date().timeIntervalSince1970 * 1000), forKey: keys.lastSync)
        UserDefaults.standard.set(Int(startedAt.distance(to: Date()) * 1000), forKey: keys.totalMs)
        syncPhase = .done
        appendLog(binId: keys.binId, phase: "SYNC", message: "DONE")
    }
    
    @MainActor
    private func finalizeError(keys: SyncKeys, error: Error) {
        UserDefaults.standard.set(error.localizedDescription, forKey: keys.lastError)
        syncPhase = .error
        appendLog(binId: keys.binId, phase: "SYNC", message: "ERROR: \(error.localizedDescription)")
    }
    
    @MainActor
    private func applyCloudEncryptionSetting(enabled: Bool) async {
        do {
            try CloudSyncKeyManager.setEncryptionEnabled(enabled)
            isCloudEncryptionEnabled = enabled
            await refreshExportedCloudKey()
            showToast(enabled ? "sync_center_toast_encryption_on".localized : "sync_center_toast_encryption_off".localized)
        } catch {
            showToast(String(format: "sync_center_toast_encryption_failed_format".localized, error.localizedDescription))
            isCloudEncryptionEnabled = CloudSyncKeyManager.isEncryptionEnabled()
        }
    }
    
    @MainActor
    private func refreshExportedCloudKey() async {
        exportedCloudSyncKey = (try? CloudSyncKeyManager.exportCurrentKey()) ?? ""
    }
    
    @MainActor
    private func rotateCloudSyncKey() async {
        do {
            _ = try CloudSyncKeyManager.rotateKey()
            await refreshExportedCloudKey()
            showToast("sync_center_toast_rotate_done".localized)
        } catch {
            showToast(String(format: "sync_center_toast_rotate_failed_format".localized, error.localizedDescription))
        }
    }
    
    @MainActor
    private func importCloudSyncKey() async {
        do {
            let input = importCloudSyncKeyInput
            importCloudSyncKeyInput = ""
            _ = try CloudSyncKeyManager.importKey(exported: input)
            await refreshExportedCloudKey()
            showToast("sync_center_toast_import_key_done".localized)
        } catch {
            showToast(String(format: "sync_center_toast_import_key_failed_format".localized, error.localizedDescription))
        }
    }
    
    @MainActor
    private func reloadCaches() async {
        guard let clientId = activeClientManager.currentClientId else {
            cachedSupplements = []
            cachedRecords = []
            return
        }
        do {
            let supplementsAll = try modelContext.fetch(
                FetchDescriptor<UserSupplement>(sortBy: [SortDescriptor(\UserSupplement.name)])
            )
            cachedSupplements = supplementsAll.filter { $0.client?.id == clientId }
            
            var recordsDescriptor = FetchDescriptor<IntakeRecord>(
                sortBy: [SortDescriptor(\IntakeRecord.date, order: .reverse)]
            )
            recordsDescriptor.fetchLimit = 5_000
            let recordsAll = try modelContext.fetch(recordsDescriptor)
            cachedRecords = recordsAll.filter { $0.supplement?.client?.id == clientId }
        } catch {
            cachedSupplements = []
            cachedRecords = []
        }
    }
    
    @MainActor
    private func showToast(_ message: String) {
        importErrorMessage = message
        showImportErrorAlert = true
    }
    
    @MainActor
    private func appendLog(binId: String, phase: String, message: String) {
        let resolvedBinId = binId == "pending" ? activeBinId : binId
        guard !resolvedBinId.isEmpty else { return }
        var current = loadLogEntries(binId: resolvedBinId)
        current.insert(CloudSyncLogEntry(epochMs: nowEpochMs(), phase: phase, message: message), at: 0)
        if current.count > 30 { current = Array(current.prefix(30)) }
        saveLogEntries(binId: resolvedBinId, entries: current)
        if activeBinId == resolvedBinId { logEntries = current }
    }
    
    private func loadLogEntries(binId: String) -> [CloudSyncLogEntry] {
        let key = "cloudSyncLog_\(binId)"
        guard let data = UserDefaults.standard.data(forKey: key) else { return [] }
        return (try? JSONDecoder().decode([CloudSyncLogEntry].self, from: data)) ?? []
    }
    
    private func saveLogEntries(binId: String, entries: [CloudSyncLogEntry]) {
        let key = "cloudSyncLog_\(binId)"
        guard let data = try? JSONEncoder().encode(entries) else { return }
        UserDefaults.standard.set(data, forKey: key)
    }
    
    private func nowEpochMs() -> Int64 {
        Int64(Date().timeIntervalSince1970 * 1000.0)
    }
    
    @MainActor
    private func clearLogs() {
        let id = activeBinId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !id.isEmpty else { return }
        UserDefaults.standard.removeObject(forKey: "cloudSyncLog_\(id)")
        logEntries = []
        showToast("sync_center_toast_log_cleared".localized)
    }
}

private enum SyncCenterTab: String {
    case host
    case link
}

private struct SyncKeys {
    let binId: String
    let lastSync: String
    let lastAttempt: String
    let retryCount: String
    let bytesDown: String
    let bytesUp: String
    let pullMs: String
    let mergeMs: String
    let pushMs: String
    let totalMs: String
    let lastError: String
    let etag: String
    
    init(binId: String) {
        self.binId = binId
        lastSync = "cloudSyncLastSyncEpochMs_\(binId)"
        lastAttempt = "cloudSyncLastAttemptEpochMs_\(binId)"
        retryCount = "cloudSyncConflictRetryCount_\(binId)"
        bytesDown = "cloudSyncBytesDownloaded_\(binId)"
        bytesUp = "cloudSyncBytesUploaded_\(binId)"
        pullMs = "cloudSyncPullMs_\(binId)"
        mergeMs = "cloudSyncMergeMs_\(binId)"
        pushMs = "cloudSyncPushMs_\(binId)"
        totalMs = "cloudSyncTotalMs_\(binId)"
        lastError = "cloudSyncLastError_\(binId)"
        etag = "cloudSyncEtag_\(binId)"
    }
}

private struct CloudSyncLogEntry: Codable, Identifiable {
    var id: String { "\(epochMs)_\(phase)" }
    let epochMs: Int64
    let phase: String
    let message: String
    
    var title: String {
        let date = Date(timeIntervalSince1970: Double(epochMs) / 1000.0)
        return "\(date.formatted(date: .abbreviated, time: .standard)) • \(phase)"
    }
}

private func formatBytes(_ bytes: Int) -> String {
    if bytes < 1024 { return "\(bytes)B" }
    let kb = Double(bytes) / 1024.0
    if kb < 1024 { return String(format: "%.1fKB", kb) }
    let mb = kb / 1024.0
    return String(format: "%.2fMB", mb)
}

private enum SyncPhase: String {
    case idle
    case pulling
    case merging
    case pushing
    case retryingConflict
    case done
    case error
    
    var isMergeDone: Bool {
        switch self {
        case .merging, .pushing, .retryingConflict, .done, .error:
            return true
        case .idle, .pulling:
            return false
        }
    }
    
    var text: String {
        switch self {
        case .idle: return "sync_phase_idle".localized
        case .pulling: return "sync_phase_pulling".localized
        case .merging: return "sync_phase_merging".localized
        case .pushing: return "sync_phase_pushing".localized
        case .retryingConflict: return "sync_phase_retrying_conflict".localized
        case .done: return "sync_phase_done".localized
        case .error: return "sync_phase_error".localized
        }
    }
}

private struct SyncStepChip: View {
    let label: String
    let done: Bool
    
    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: done ? "checkmark.circle.fill" : "chevron.right")
                .foregroundStyle(done ? Color.green : Color.secondary)
                .font(.caption)
            Text(label)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(done ? Color.green.opacity(0.12) : Color.secondary.opacity(0.12))
        .clipShape(Capsule())
    }
}

#Preview {
    NavigationStack {
        SyncCenterView(activeClientManager: ActiveClientManager())
    }
}
