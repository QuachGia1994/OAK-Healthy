import SwiftUI
import SwiftData
import UIKit

public struct SyncCenterView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.colorScheme) private var colorScheme
    @Query(sort: \ClientProfile.createdAt) private var clients: [ClientProfile]
    
    @AppStorage("isNotificationEnabledByUser") private var isNotificationEnabledByUser: Bool = false
    @AppStorage("isAutoSyncEnabled") private var isAutoSyncEnabled: Bool = false
    @AppStorage("oakSafeModeEnabled") private var isSafeModeEnabled: Bool = false

    @State private var hostedBinId: String = ""
    @State private var linkedBinId: String = ""
    @State private var selectedTab: SyncCenterTab = .host
    @State private var linkCodeInput: String = ""
    @State private var isInputCodeVisible: Bool = false
    @State private var isCloudSyncLoading: Bool = false
    @State private var isBinIdVisible: Bool = false
    @State private var isRevokingBinId: Bool = false
    @State private var isShowingRevokeConfirm: Bool = false
    @State private var isShowingRehostConfirm: Bool = false
    @State private var isShowingDisableEncryptionConfirm: Bool = false
    @State private var isShowingImportKeyConfirm: Bool = false
    @State private var isShowingClearLogConfirm: Bool = false
    @State private var logQuery: String = ""
    @State private var logPhaseFilter: String = "ALL"
    @State private var syncPhase: SyncPhase = .idle
    @State private var isManifestPartsVisible: Bool = false
    @State private var isStatusDiagnosticsVisible: Bool = false
    @State private var isLogsVisible: Bool = false
    
    @State private var importErrorMessage: String = ""
    @State private var showImportErrorAlert: Bool = false
    @State private var toastRetryAction: (() -> Void)?
    
    @State private var isCloudEncryptionEnabled: Bool = CloudSyncKeyManager.isEncryptionEnabled()
    @State private var exportedCloudSyncKey: String = ""
    @State private var importCloudSyncKeyInput: String = ""
    @State private var isExportedKeyVisible: Bool = false
    @State private var isImportKeyVisible: Bool = false
    
    @State private var cachedSupplements: [UserSupplement] = []
    @State private var cachedRecords: [IntakeRecord] = []
    @State private var logEntries: [CloudSyncLogEntry] = []
    
    public let activeClientManager: ActiveClientManager
    private let cloudSyncProfileStore = CloudSyncProfileStore()
    
    public init(activeClientManager: ActiveClientManager) {
        self.activeClientManager = activeClientManager
    }
    
    public var body: some View {
        ZStack {
            Color.clear.oakBackground()
            List {
                onboardingSection
                statusSection
                tabSection
                encryptionSection
                logsSection
            }
            .scrollContentBackground(.hidden)
            .listSectionSpacing(20)
        }
        .navigationTitle("sync_center_title".localized)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        .onAppear {
            if linkCodeInput.isEmpty { linkCodeInput = linkedBinId }
        }
        .task {
            DebugReporter.report("sync_center_task_boot", fields: [
                "hasClient": String(activeClientManager.currentClientId != nil),
                "clientsCount": String(clients.count)
            ])
            guard activeClientManager.currentClientId == nil else { return }
            guard let first = clients.first else { return }
            activeClientManager.setCurrentClientId(first.id)
        }
        .task(id: activeClientManager.currentClientId) {
            DebugReporter.report("sync_center_task_reload_caches", fields: [
                "hasClient": String(activeClientManager.currentClientId != nil)
            ])
            loadProfileCloudLinks()
            await reloadCaches()
        }
        .task(id: activeBinId) {
            DebugReporter.report("sync_center_task_load_logs", fields: [
                "hasLink": String(!activeBinId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            ])
            logEntries = CloudSyncLogStore.load(manifestId: activeBinId)
            let trimmed = activeBinId.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmed.isEmpty else { return }
            guard isAutoSyncEnabled else { return }
            CloudSyncAutoSync.startRealtimeSync(
                modelContext: modelContext,
                activeClientManager: activeClientManager
            )
        }
        .task(id: isCloudEncryptionEnabled) {
            DebugReporter.report("sync_center_task_refresh_key", fields: [
                "enabled": String(isCloudEncryptionEnabled)
            ])
            await refreshExportedCloudKey()
        }
        .alert("sync_center_notice_title".localized, isPresented: $showImportErrorAlert) {
            if let toastRetryAction {
                Button("sync_center_sync_now".localized) {
                    toastRetryAction()
                    self.toastRetryAction = nil
                }
            }
            Button("ok".localized) { toastRetryAction = nil }
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
            .oakSecondaryText()
        } header: {
            Text("sync_center_onboarding_header".localized)
        }
        .listRowBackground(syncRowBackground)
    }
    
    @ViewBuilder
    private var statusSection: some View {
        Section {
            if !activeBinId.isEmpty {
                let status = SyncCenterStatusReader.read(
                    manifestId: activeBinId,
                    clientId: activeClientManager.currentClientId,
                    modelContext: modelContext
                )
                let lastSyncEpochMs = status.lastSyncEpochMs
                let lastAttemptEpochMs = status.lastAttemptEpochMs
                let retryCount = status.conflict.retryCount
                let bytesDown = status.transfer.bytesDownloaded
                let bytesUp = status.transfer.bytesUploaded
                let pullMs = status.transfer.pullMs
                let mergeMs = status.transfer.mergeMs
                let pushMs = status.transfer.pushMs
                let totalMs = status.transfer.totalMs
                let queuedMutationCount = status.queuedMutationCount
                let nextRetryEpochMs = status.nextRetryEpochMs
                let conflictRemoteWins = status.conflict.remoteWins
                let conflictLocalWins = status.conflict.localWins
                let conflictTieLocalWins = status.conflict.tieLocalWins
                let journalCount = status.journalCount
                let hasPendingChanges = status.hasPendingChanges
                let lastError = status.lastError ?? ""
                let health = SyncHealthEvaluator.evaluate(
                    SyncHealthInput(
                        hasLink: true,
                        autoSyncEnabled: isAutoSyncEnabled,
                        hasPendingChanges: hasPendingChanges,
                        lastSyncEpochMs: lastSyncEpochMs,
                        lastAttemptEpochMs: lastAttemptEpochMs,
                        lastError: status.lastError,
                        encryptionEnabled: isCloudEncryptionEnabled
                    )
                )
                let stackId = status.stackBinId
                let historyId = status.historyBinId
                
                VStack(alignment: .leading, spacing: 6) {
                    syncHealthSummary(health)
                    if lastSyncEpochMs > 0 {
                        let date = Date(timeIntervalSince1970: Double(lastSyncEpochMs) / 1000.0)
                        Text(
                            String(
                                format: "sync_center_last_sync_format".localized,
                                date.formatted(date: .abbreviated, time: .shortened)
                            )
                        )
                            .font(.caption)
                            .oakSecondaryText()
                    } else {
                        Text("sync_center_no_sync_yet".localized)
                            .font(.caption)
                            .oakSecondaryText()
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
                            .oakSecondaryText()
                    }
                    
                    Text(String(format: "sync_center_phase_format".localized, syncPhase.text))
                        .font(.caption)
                        .oakSecondaryText()
                    
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
                    if !lastError.isEmpty {
                        Text("sync_center_failure_safe_body".localized)
                            .font(.caption)
                            .foregroundStyle(OAKPalette.missed(for: colorScheme))
                    }
                    Button(
                        isStatusDiagnosticsVisible
                            ? "sync_center_diagnostics_hide".localized
                            : "sync_center_diagnostics_show".localized
                    ) {
                        isStatusDiagnosticsVisible.toggle()
                    }
                    .buttonStyle(.borderless)
                    .font(.caption)
                    .oakTouchTarget()

                    if isStatusDiagnosticsVisible {
                    Text(String(format: "sync_center_queue_format".localized, queuedMutationCount))
                        .font(.caption)
                        .oakSecondaryText()
                    if nextRetryEpochMs > Int64(Date().timeIntervalSince1970 * 1000) {
                        let retryDate = Date(timeIntervalSince1970: Double(nextRetryEpochMs) / 1000)
                        Text(
                            String(
                                format: "sync_center_retry_after_format".localized,
                                retryDate.formatted(date: .abbreviated, time: .shortened)
                            )
                        )
                        .font(.caption)
                        .oakSecondaryText()
                    }
                    if conflictRemoteWins + conflictLocalWins + conflictTieLocalWins > 0 {
                        Text(
                            String(
                                format: "sync_center_conflict_preview_format".localized,
                                conflictRemoteWins,
                                conflictLocalWins,
                                conflictTieLocalWins
                            )
                        )
                        .font(.caption)
                        .oakSecondaryText()
                    }
                    Text(String(format: "sync_center_journal_count_format".localized, journalCount))
                        .font(.caption)
                        .oakSecondaryText()
                    
                    if retryCount > 0 {
                        Text(String(format: "sync_center_conflict_retry_format".localized, retryCount))
                            .font(.caption)
                            .oakSecondaryText()
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
                            .oakSecondaryText()
                    }
                    
                    if !stackId.isEmpty || !historyId.isEmpty {
                        HStack(spacing: 10) {
                            VStack(alignment: .leading, spacing: 4) {
                                if !stackId.isEmpty {
                                    Text(
                                        String(
                                            format: "sync_center_stack_id_label_format".localized,
                                            isManifestPartsVisible ? stackId : String(repeating: "•", count: 16)
                                        )
                                    )
                                    .font(.caption)
                                    .oakSecondaryText()
                                }
                                if !historyId.isEmpty {
                                    Text(
                                        String(
                                            format: "sync_center_history_id_label_format".localized,
                                            isManifestPartsVisible ? historyId : String(repeating: "•", count: 16)
                                        )
                                    )
                                    .font(.caption)
                                    .oakSecondaryText()
                                }
                            }
                            Spacer()
                            Button(action: { isManifestPartsVisible.toggle() }) {
                                Image(systemName: isManifestPartsVisible ? "eye.slash" : "eye")
                                    .foregroundStyle(.gray)
                                    .accessibilityLabel(isManifestPartsVisible ? "hide_details".localized : "show_details".localized)
                            }
                            .buttonStyle(.borderless)
                            .oakTouchTarget()
                        }
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
                            .oakSecondaryText()
                            .lineLimit(2)
                    }
                    
                    if !lastError.isEmpty {
                        Text(String(format: "sync_center_last_error_format".localized, lastError))
                            .font(.caption)
                            .foregroundStyle(OAKPalette.missed(for: colorScheme))
                            .lineLimit(3)
                        let isTransient = lastError.localizedCaseInsensitiveContains("522") ||
                            lastError.localizedCaseInsensitiveContains("quá thời gian") ||
                            lastError.localizedCaseInsensitiveContains("timed out") ||
                            lastError.localizedCaseInsensitiveContains("không có internet") ||
                            lastError.localizedCaseInsensitiveContains("no internet") ||
                            lastError.localizedCaseInsensitiveContains("không thể kết nối")
                        if isTransient {
                            Button("sync_center_sync_now".localized) {
                                Task { await syncNow(label: "MANUAL") }
                            }
                            .buttonStyle(.borderless)
                            .font(.caption)
                            .disabled(isCloudSyncLoading || activeClientManager.currentClientId == nil)
                        }
                        let hint = isCloudEncryptionEnabled &&
                            (lastError.localizedCaseInsensitiveContains("Missing cloud sync key") ||
                             lastError.localizedCaseInsensitiveContains("Decrypt failed"))
                        if hint {
                            Text("sync_center_hint_missing_key".localized)
                                .font(.caption)
                                .oakSecondaryText()
                                .lineLimit(3)
                        }
                    }
                    }
                }
                
                Button("sync_center_sync_now".localized) {
                    Task { await syncNow(label: "MANUAL") }
                }
                .disabled(isCloudSyncLoading || activeClientManager.currentClientId == nil)
            } else {
                Text("sync_center_no_link_code".localized)
                    .font(.caption)
                    .oakSecondaryText()
            }
        } header: {
            Text("sync_center_status_header".localized)
        }
        .listRowBackground(syncRowBackground)
    }
    
    @ViewBuilder
    private func syncHealthSummary(_ report: SyncHealthReport) -> some View {
        HStack(alignment: .center, spacing: 10) {
            VStack(alignment: .leading, spacing: 3) {
                Text(syncHealthTitle(report.level)).font(.subheadline.weight(.semibold))
                Text(syncRecoveryHint(report.action)).font(.caption).oakSecondaryText()
            }
            Spacer()
            if report.action == .syncNow {
                Button("sync_center_sync_now".localized) {
                    Task { await syncNow(label: "MANUAL") }
                }
                .buttonStyle(.borderless)
                .disabled(isCloudSyncLoading)
            }
        }
        .accessibilityElement(children: .combine)
    }

    private func syncHealthTitle(_ level: SyncHealthLevel) -> String {
        switch level {
        case .unlinked: return "sync_health_unlinked".localized
        case .idle: return "sync_health_idle".localized
        case .healthy: return "sync_health_healthy".localized
        case .pending: return "sync_health_pending".localized
        case .needsKey: return "sync_health_needs_key".localized
        case .retryableError: return "sync_health_retryable".localized
        case .actionRequired: return "sync_health_action_required".localized
        }
    }

    private func syncRecoveryHint(_ action: SyncRecoveryAction) -> String {
        switch action {
        case .none: return "sync_health_hint_none".localized
        case .syncNow: return "sync_health_hint_sync_now".localized
        case .importKey: return "sync_health_hint_import_key".localized
        case .checkLink: return "sync_health_hint_check_link".localized
        }
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
        .listRowBackground(syncRowBackground)
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
                    .oakSecondaryText()
                
                Text(isBinIdVisible ? binId : String(repeating: "•", count: 24))
                    .font(.title3)
                    .fontWeight(.bold)
                    .textSelection(.enabled)
                    .onTapGesture {
                        copyToClipboard(binId, sensitive: true)
                        showToast("sync_center_toast_code_copied".localized)
                    }
                    .accessibilityLabel("sync_center_your_link_code".localized)
                    .accessibilityAddTraits(.isStaticText)
                
                Button(action: { isBinIdVisible.toggle() }) {
                    Image(systemName: isBinIdVisible ? "eye.slash" : "eye")
                        .foregroundStyle(.gray)
                        .accessibilityLabel(isBinIdVisible ? "hide_link_code".localized : "show_link_code".localized)
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
                .oakSecondaryText()
        }
    }
    
    @ViewBuilder
    private var linkTab: some View {
        HStack(spacing: 8) {
            if isInputCodeVisible {
                TextField("sync_center_link_code_placeholder".localized, text: $linkCodeInput)
            } else {
                SecureField("sync_center_link_code_placeholder".localized, text: $linkCodeInput)
            }
            Button(action: { isInputCodeVisible.toggle() }) {
                Image(systemName: isInputCodeVisible ? "eye.slash" : "eye")
                    .foregroundStyle(.gray)
            }
            .buttonStyle(.borderless)
            Button("sync_center_action_paste".localized) {
                let pasted = (UIPasteboard.general.string ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
                if !pasted.isEmpty { linkCodeInput = pasted }
            }
            .font(.caption)
            .buttonStyle(.borderless)
            .disabled(isCloudSyncLoading)
        }
        .textInputAutocapitalization(.never)
        .autocorrectionDisabled()
        
        Button("sync_center_download_action".localized) {
            Task { await receiveData() }
        }
        .disabled(isCloudSyncLoading || linkCodeInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)

        if !linkedBinId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            Button("sync_center_unlink_action".localized, role: .destructive) {
                unlinkCurrentProfile()
            }
            .disabled(isCloudSyncLoading)
        }
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
            .disabled(hasActiveCloudLink)
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

            if hasActiveCloudLink {
                Text("sync_center_encryption_locked_hint".localized)
                    .font(.caption)
                    .oakSecondaryText()
            }

            if isCloudEncryptionEnabled || hasActiveCloudLink {
                if !exportedCloudSyncKey.isEmpty {
                    Text("sync_center_export_key_label".localized)
                        .font(.caption)
                        .oakSecondaryText()
                    HStack {
                        Text(isExportedKeyVisible ? exportedCloudSyncKey : String(repeating: "•", count: 32))
                            .font(.caption.monospaced())
                            .oakSecondaryText()
                            .lineLimit(2)
                        Spacer()
                        Button {
                            isExportedKeyVisible.toggle()
                        } label: {
                            Image(systemName: isExportedKeyVisible ? "eye.slash" : "eye")
                        }
                        .buttonStyle(.borderless)
                    }
                }
                
                HStack(spacing: 12) {
                    Button("sync_center_action_copy_key".localized) {
                        copyToClipboard(exportedCloudSyncKey, sensitive: true)
                        showToast("sync_center_toast_key_copied".localized)
                    }
                    .disabled(exportedCloudSyncKey.isEmpty)
                    .buttonStyle(.borderless)

                    Button("sync_center_refresh_key_action".localized) {
                        Task { await refreshExportedCloudKey() }
                    }
                    .buttonStyle(.borderless)
                }
                
                HStack(spacing: 8) {
                    Group {
                        if isImportKeyVisible {
                            TextField("sync_center_import_key_placeholder".localized, text: $importCloudSyncKeyInput)
                        } else {
                            SecureField("sync_center_import_key_placeholder".localized, text: $importCloudSyncKeyInput)
                        }
                    }
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .textFieldStyle(.roundedBorder)
                    Button {
                        isImportKeyVisible.toggle()
                    } label: {
                        Image(systemName: isImportKeyVisible ? "eye.slash" : "eye")
                    }
                    .buttonStyle(.borderless)
                    Button("sync_center_action_paste".localized) {
                        let pasted = (UIPasteboard.general.string ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
                        if !pasted.isEmpty { importCloudSyncKeyInput = pasted }
                    }
                    .font(.caption)
                    .buttonStyle(.borderless)
                    .disabled(isCloudSyncLoading)
                }
                
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
                    .oakSecondaryText()
            }
        } header: {
            Text("sync_center_security_header".localized)
        }
        .listRowBackground(syncRowBackground)
    }
    
    @ViewBuilder
    private var logsSection: some View {
        Section {
            if activeBinId.isEmpty {
                Text("sync_center_logs_hint".localized)
                    .font(.caption)
                    .oakSecondaryText()
            } else {
                Button {
                    isLogsVisible.toggle()
                } label: {
                    HStack {
                        Text("sync_center_logs_header".localized)
                        Spacer()
                        Image(systemName: isLogsVisible ? "chevron.up" : "chevron.down")
                    }
                }
                .buttonStyle(.plain)
                .oakTouchTarget()
                if isLogsVisible {
                    logControls
                    if filteredLogEntries.isEmpty {
                        Text("sync_center_no_logs".localized)
                            .font(.caption)
                            .oakSecondaryText()
                    } else {
                        ForEach(filteredLogEntries) { entry in
                            VStack(alignment: .leading, spacing: OAKSpacing.xs) {
                                Text(entry.title).font(.caption).oakSecondaryText()
                                Text(entry.message).font(.caption)
                            }
                            .padding(.vertical, OAKSpacing.xs)
                        }
                    }
                }
            }
        }
        .listRowBackground(syncRowBackground)
    }

    private var logControls: some View {
        VStack(alignment: .leading, spacing: OAKSpacing.sm) {
            TextField("sync_center_logs_search_placeholder".localized, text: $logQuery)
                .textFieldStyle(.roundedBorder)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            Picker("sync_center_logs_phase".localized, selection: $logPhaseFilter) {
                Text("sync_center_logs_phase_all".localized).tag("ALL")
                ForEach(availableLogPhases, id: \.self) { phase in
                    Text(CloudSyncLogEntry.displayText(for: phase)).tag(phase)
                }
            }
            .pickerStyle(.menu)
            .font(.caption)
            HStack {
                ShareLink(item: prettyLogText) { Text("sync_center_logs_export".localized) }
                    .font(.caption)
                    .buttonStyle(.borderless)
                Spacer()
                Button(role: .destructive) { isShowingClearLogConfirm = true } label: {
                    Text("sync_center_logs_clear".localized)
                }
                .confirmationDialog(
                    "sync_center_clear_log_confirm".localized,
                    isPresented: $isShowingClearLogConfirm,
                    titleVisibility: .visible
                ) {
                    Button("sync_center_clear_log_action".localized, role: .destructive) { clearLogs() }
                    Button("cancel".localized, role: .cancel) {}
                }
                .buttonStyle(.borderless)
            }
        }
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
    
    private var prettyLogText: String {
        guard !logEntries.isEmpty else { return "[]" }
        return logEntries.map { "\($0.title) — \($0.message)" }.joined(separator: "\n")
    }
    
    private var activeBinId: String {
        let hosted = hostedBinId.trimmingCharacters(in: .whitespacesAndNewlines)
        if !hosted.isEmpty { return hosted }
        return linkedBinId.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var hasActiveCloudLink: Bool { !activeBinId.isEmpty }

    private var syncRowBackground: some View {
        OAKPalette.surface(for: colorScheme)
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
        guard let clientId = activeClientManager.currentClientId else {
            showToast("sync_center_toast_missing_client".localized)
            return
        }
        await withLoading {
            await reloadCaches()
            guard activeClientManager.currentClientId == clientId else { return }
            isBinIdVisible = false
            do {
                let old = hostedBinId.trimmingCharacters(in: .whitespacesAndNewlines)
                let newId = try await uploadHostedBackup()
                cloudSyncProfileStore.setHostedBinId(newId, clientId: clientId)
                if activeClientManager.currentClientId == clientId { hostedBinId = newId }
                if !old.isEmpty, old != newId {
                    try? await revokeOldHostedBinIfNeeded(oldBinId: old)
                }
                appendLog(binId: newId, phase: "HOST", message: "DONE")
                showToast("sync_center_toast_host_success".localized)
            } catch {
                showToast(String(format: "sync_center_toast_host_failed_format".localized, error.localizedDescription))
            }
        }
    }
    
    private func revokeHostedBin() async {
        guard let clientId = activeClientManager.currentClientId else { return }
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
            cloudSyncProfileStore.setHostedBinId(nil, clientId: clientId)
            if activeClientManager.currentClientId == clientId { hostedBinId = "" }
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
        let binId = linkCodeInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard FirebaseCloudStore.isValidBinId(binId) else {
            showToast("sync_center_toast_invalid_link_code".localized)
            return
        }
        await withLoading {
            if await runSyncFlow(binId: binId, clientId: clientId, label: "LINK") {
                guard activeClientManager.currentClientId == clientId else { return }
                cloudSyncProfileStore.setLinkedBinId(binId, clientId: clientId)
                linkedBinId = binId
            }
        }
    }
    
    @MainActor
    private func syncNow(label: String) async {
        guard let clientId = activeClientManager.currentClientId else { return }
        let id = activeBinId
        guard !id.isEmpty else { return }
        await withLoading {
            _ = await runSyncFlow(binId: id, clientId: clientId, label: label)
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
        UserDefaults.standard.removeObject(forKey: "cloudSyncStackBinId_\(oldBinId)")
        UserDefaults.standard.removeObject(forKey: "cloudSyncHistoryBinId_\(oldBinId)")
        UserDefaults.standard.removeObject(forKey: "cloudSyncEtagV2_\(oldBinId)")
        UserDefaults.standard.removeObject(forKey: "cloudSyncEtagStackV2_\(oldBinId)")
        UserDefaults.standard.removeObject(forKey: "cloudSyncEtagHistoryV2_\(oldBinId)")
        UserDefaults.standard.removeObject(forKey: "cloudSyncLastSeenRevV2_\(oldBinId)")
    }
    
    @MainActor
    private func uploadHostedBackup() async throws -> String {
        let stackPayload = try SupplementExportCodec.encodeBackup(supplements: cachedSupplements, records: [])
        let historyPayload = try SupplementExportCodec.encodeBackup(supplements: [], records: cachedRecords)
        var createdIds: [String] = []
        do {
            let stackId = try await CloudSyncManager.shared.uploadBackup(jsonData: stackPayload)
            createdIds.append(stackId)
            let historyId = try await CloudSyncManager.shared.uploadBackup(jsonData: historyPayload)
            createdIds.append(historyId)
            let manifest: Data
            do {
                manifest = try CloudSyncManifestCodec.encode(stackBinId: stackId, historyBinId: historyId)
            } catch let error as CloudSyncManifestCodecError {
                throw CloudSyncError.manifestCodec(error)
            }
            let manifestId = try await CloudSyncManager.shared.uploadBackup(jsonData: manifest)
            UserDefaults.standard.set(stackId, forKey: "cloudSyncStackBinId_\(manifestId)")
            UserDefaults.standard.set(historyId, forKey: "cloudSyncHistoryBinId_\(manifestId)")
            UserDefaults.standard.set(Double(Date().timeIntervalSince1970 * 1000), forKey: "cloudSyncLastSyncEpochMs_\(manifestId)")
            return manifestId
        } catch {
            for id in createdIds.reversed() { try? await CloudSyncManager.shared.deleteBackup(binId: id) }
            throw error
        }
    }
    
    @MainActor
    @discardableResult
    private func runSyncFlow(binId: String, clientId: UUID, label: String) async -> Bool {
        appendLog(binId: binId, phase: "DIAG", message: FirebaseBootstrap.firebaseDiag)
        appendLog(binId: binId, phase: "SYNC", message: "\(label) START")
        syncPhase = .pulling
        let result = await CloudSyncAutoSync.syncNow(
            modelContext: modelContext,
            clientId: clientId,
            binId: binId
        )
        switch result {
        case .success:
            activeClientManager.setCurrentClientId(clientId)
            isSafeModeEnabled = false
            syncPhase = .done
            await reloadCaches()
            await rescheduleNotificationsIfEnabled()
            appendLog(binId: binId, phase: "SYNC", message: "\(label) DONE")
            if label != "AUTO" { showToast("sync_center_toast_sync_success".localized) }
            return true
        case .failure(let error):
            syncPhase = .error
            appendLog(binId: binId, phase: "SYNC", message: "\(label) ERROR: \(error.localizedDescription)")
            if label != "AUTO" { showSyncFailure(error) }
            return false
        }
    }

    @MainActor
    private func showSyncFailure(_ error: Error) {
        let message = String(
            format: "sync_center_toast_sync_failed_format".localized,
            error.localizedDescription
        )
        guard !(error is FirebaseBootstrapError) else {
            showToast(message)
            return
        }
        showToast(message, retryAction: { Task { await syncNow(label: "MANUAL") } })
    }
    
    @MainActor
    private func rescheduleNotificationsIfEnabled() async {
        guard isNotificationEnabledByUser else { return }
        do {
            try await NotificationService.shared.requestAuthorization()
        } catch {
            return
        }
        let active = cachedSupplements.filter { $0.deletedAtEpochMs == nil }
        await NotificationService.shared.replaceAllSchedules(supplements: active)
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
    private func importCloudSyncKey() async {
        do {
            let input = importCloudSyncKeyInput
            importCloudSyncKeyInput = ""
            _ = try CloudSyncKeyManager.importKey(exported: input)
            try CloudSyncKeyManager.setEncryptionEnabled(true)
            isCloudEncryptionEnabled = true
            await refreshExportedCloudKey()
            showToast("sync_center_toast_import_key_done".localized)
        } catch {
            showToast(String(format: "sync_center_toast_import_key_failed_format".localized, error.localizedDescription))
        }
    }
    
    @MainActor
    private func reloadCaches() async {
        guard let clientId = activeClientManager.currentClientId else {
            clearCaches()
            return
        }
        let container = modelContext.container
        let cutoff = Calendar.current.date(byAdding: .day, value: -90, to: .now) ?? .now
        do {
            let (suppIds, recordIds) = try await Task.detached {
                let ctx = ModelContext(container)
                let supplements = try ClientScopedStore.supplements(
                    modelContext: ctx,
                    clientId: clientId
                )
                let records = try ClientScopedStore.recentHistoryRecords(
                    modelContext: ctx,
                    clientId: clientId,
                    cutoff: cutoff,
                    limit: 5_000
                )
                return (supplements.map(\.persistentModelID), records.map(\.persistentModelID))
            }.value

            cachedSupplements = suppIds.compactMap { modelContext.model(for: $0) as? UserSupplement }
            cachedRecords = recordIds.compactMap { modelContext.model(for: $0) as? IntakeRecord }
        } catch {
            clearCaches()
        }
    }

    private func loadProfileCloudLinks() {
        let links = cloudSyncProfileStore.links(clientId: activeClientManager.currentClientId)
        hostedBinId = links.hostedBinId ?? ""
        linkedBinId = links.linkedBinId ?? ""
        linkCodeInput = linkedBinId
    }

    private func unlinkCurrentProfile() {
        guard let clientId = activeClientManager.currentClientId else { return }
        cloudSyncProfileStore.setLinkedBinId(nil, clientId: clientId)
        linkedBinId = ""
        linkCodeInput = ""
        if hostedBinId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            isAutoSyncEnabled = false
            CloudSyncAutoSync.stopRealtimeSync()
        }
    }

    private func clearCaches() {
        cachedSupplements = []
        cachedRecords = []
    }
    
    @MainActor
    private func showToast(_ message: String) {
        importErrorMessage = message
        toastRetryAction = nil
        showImportErrorAlert = true
    }

    @MainActor
    private func showToast(_ message: String, retryAction: (() -> Void)?) {
        importErrorMessage = message
        toastRetryAction = retryAction
        showImportErrorAlert = true
    }
    
    @MainActor
    private func appendLog(binId: String, phase: String, message: String) {
        let resolvedBinId = binId == "pending" ? activeBinId : binId
        guard !resolvedBinId.isEmpty else { return }
        let current = CloudSyncLogStore.append(
            manifestId: resolvedBinId,
            phase: phase,
            message: message,
            nowEpochMs: Int64(Date().timeIntervalSince1970 * 1000)
        )
        if activeBinId == resolvedBinId { logEntries = current }
    }

    @MainActor
    private func copyToClipboard(_ text: String, sensitive: Bool = false) {
        let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return }
        guard sensitive else {
            UIPasteboard.general.string = value
            return
        }
        UIPasteboard.general.setItems(
            [["public.utf8-plain-text": value]],
            options: [.localOnly: true, .expirationDate: Date().addingTimeInterval(120)]
        )
    }
    
    @MainActor
    private func clearLogs() {
        let id = activeBinId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !id.isEmpty else { return }
        CloudSyncLogStore.clear(manifestId: id)
        logEntries = []
        showToast("sync_center_toast_log_cleared".localized)
    }
}

private enum SyncCenterTab: String {
    case host
    case link
}

private extension CloudSyncLogEntry {
    static func displayText(for phase: String) -> String {
        switch phase.uppercased() {
        case "HOST":
            return "sync_log_phase_host".localized
        case "SYNC":
            return "sync_log_phase_sync".localized
        case "PULL":
            return "sync_center_chip_pull".localized
        case "MERGE":
            return "sync_center_chip_merge".localized
        case "PUSH":
            return "sync_center_chip_push".localized
        case "CONFLICT":
            return "sync_log_phase_conflict".localized
        case "DONE":
            return "sync_log_phase_done".localized
        case "ERROR":
            return "sync_log_phase_error".localized
        default:
            return phase
        }
    }
    
    var title: String {
        let date = Date(timeIntervalSince1970: Double(epochMs) / 1000.0)
        return "\(date.formatted(date: .abbreviated, time: .standard)) • \(Self.displayText(for: phase))"
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
                .oakSecondaryText()
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
