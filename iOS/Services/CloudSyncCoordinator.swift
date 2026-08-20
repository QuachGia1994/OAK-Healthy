import Foundation
import SwiftData
import UIKit

@MainActor
final class CloudSyncRunGate {
    private var isRunning = false
    private var autoRerunRequested = false
    private let waitInterval: Duration

    init(waitInterval: Duration = .milliseconds(50)) {
        self.waitInterval = waitInterval
    }

    func beginAuto() -> Bool {
        guard !isRunning else {
            autoRerunRequested = true
            return false
        }
        isRunning = true
        autoRerunRequested = false
        return true
    }

    func takeAutoRerun() -> Bool {
        defer { autoRerunRequested = false }
        return autoRerunRequested
    }

    func beginManual() async -> Bool {
        while isRunning && !Task.isCancelled {
            do { try await Task.sleep(for: waitInterval) } catch { return false }
        }
        guard !Task.isCancelled else { return false }
        isRunning = true
        return true
    }

    func finish() {
        isRunning = false
    }

    func clearQueuedAutoRerun() {
        autoRerunRequested = false
    }
}

struct CloudSyncRealtimeSession: Equatable, Sendable {
    let clientId: UUID
    let manifestId: String
}

@MainActor
enum CloudSyncAutoSync {
    private static var realtimeTask: Task<Void, Never>?
    private static var pendingSyncTask: Task<Void, Never>?
    private static var realtimeListener: FirebaseRealtimeSyncListener?
    private static var realtimeSession: CloudSyncRealtimeSession?
    private static let runGate = CloudSyncRunGate()
    private static let lastActivityKey = "cloudSyncLastActivityEpoch"
    private static let lastFailureKey = "cloudSyncLastFailureEpoch"
    private static let immediateConsistencyWindow: Duration = .milliseconds(180)
    private static let realtimeStartupGrace: Duration = .milliseconds(250)

    static func startRealtimeSync(
        modelContext: ModelContext,
        activeClientManager: ActiveClientManager
    ) {
        let requested = makeRealtimeSession(clientId: activeClientManager.currentClientId)
        if shouldReuseRealtimeSession(current: realtimeSession, requested: requested, hasTask: realtimeTask != nil) {
            return
        }
        stopRealtimeSync()
        guard let requested else { return }
        markActivity()
        realtimeSession = requested
        let listener = FirebaseRealtimeSyncListener(modelContext: modelContext)
        realtimeListener = listener
        realtimeTask = Task { @MainActor in
            await realtimeLoop(modelContext: modelContext, session: requested, listener: listener)
        }
    }

    static func stopRealtimeSync() {
        realtimeTask?.cancel()
        realtimeTask = nil
        pendingSyncTask?.cancel()
        pendingSyncTask = nil
        runGate.clearQueuedAutoRerun()
        realtimeListener?.stop()
        realtimeListener = nil
        realtimeSession = nil
    }

    static func requestSyncSoon(modelContext: ModelContext, clientId: UUID?) {
        guard let clientId else { return }
        markActivity()
        pendingSyncTask?.cancel()
        guard UIApplication.shared.applicationState == .active else { return }
        pendingSyncTask = Task { @MainActor in
            do {
                try await Task.sleep(for: .milliseconds(350))
            } catch {
                return
            }
            guard UIApplication.shared.applicationState == .active else { return }
            _ = await syncIfEnabled(modelContext: modelContext, clientId: clientId)
        }
    }

    private static func realtimeLoop(
        modelContext: ModelContext,
        session: CloudSyncRealtimeSession,
        listener: FirebaseRealtimeSyncListener
    ) async {
        do {
            try await Task.sleep(for: realtimeStartupGrace)
        } catch {
            return
        }
        guard ownsRealtimeSession(session) else { return }
        _ = await syncIfEnabled(modelContext: modelContext, clientId: session.clientId)
        guard ownsRealtimeSession(session) else { return }
        await listener.start(session: session)
        while ownsRealtimeSession(session) {
            do {
                try await Task.sleep(for: pollInterval())
            } catch {
                return
            }
            guard ownsRealtimeSession(session) else { return }
            _ = await syncIfEnabled(modelContext: modelContext, clientId: session.clientId)
        }
    }

    static func syncIfEnabled(modelContext: ModelContext, clientId: UUID?) async -> Bool {
        guard runGate.beginAuto() else { return false }
        defer { runGate.finish() }
        var didSucceed = true
        repeat {
            didSucceed = await performSyncIfEnabled(modelContext: modelContext, clientId: clientId) && didSucceed
        } while runGate.takeAutoRerun() && !Task.isCancelled
        return didSucceed
    }

    static func syncNow(
        modelContext: ModelContext,
        clientId: UUID,
        binId: String
    ) async -> Result<Void, Error> {
        guard await runGate.beginManual() else { return .failure(CancellationError()) }
        defer { runGate.finish() }
        do {
            try await performSync(modelContext: modelContext, clientId: clientId, binId: binId)
            return .success(())
        } catch {
            return .failure(error)
        }
    }

    private static func performSyncIfEnabled(modelContext: ModelContext, clientId: UUID?) async -> Bool {
        guard UserDefaults.standard.bool(forKey: "isAutoSyncEnabled") else { return false }
        guard let clientId, let binId = activeBinId(clientId: clientId) else { return false }
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        let retryState = SyncRetryStore.state(manifestId: binId)
        guard SyncBackoffPolicy.canAttempt(retryState, nowEpochMs: nowMs) else {
            recordBackoff(manifestId: binId, state: retryState, nowEpochMs: nowMs)
            return false
        }
        do {
            try await performSync(modelContext: modelContext, clientId: clientId, binId: binId)
            return true
        } catch {
            return false
        }
    }

    private static func performSync(
        modelContext: ModelContext,
        clientId: UUID,
        binId: String
    ) async throws {
        guard let ctx = makeSyncContext(modelContext: modelContext, clientId: clientId, binId: binId) else {
            throw CloudSyncError.invalidBinId
        }
        let startedAt = Date()
        do {
            resetSyncMetrics(ctx: ctx)
            markAttempt(ctx: ctx)
            recordSyncStart(ctx: ctx)
            setPhase("pulling", ctx: ctx)
            guard let client = try fetchClient(modelContext: modelContext, clientId: clientId) else {
                throw CloudSyncError.invalidResponse
            }
            if let parts = try await resolveManifestPartsOrNil(manifestId: ctx.id) {
                try await performManifestSync(ctx: ctx, modelContext: modelContext, client: client, parts: parts)
            } else {
                try await performLegacySync(ctx: ctx, modelContext: modelContext, client: client)
            }
            markSuccess(ctx: ctx, startedAt: startedAt)
        } catch {
            markFailure(ctx: ctx, error: error)
            throw error
        }
    }

    private struct SyncContext: Sendable {
        let id: String
        let clientId: UUID
        let lastSyncKey: String
        let lastAttemptKey: String
        let lastErrorKey: String
        let localStackChanged: Bool
        let localHistoryChanged: Bool
        let syncStartedEpochMs: Int64
    }

    private static func makeSyncContext(
        modelContext: ModelContext,
        clientId: UUID,
        binId: String
    ) -> SyncContext? {
        let id = binId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !id.isEmpty else { return nil }
        let lastSyncKey = "cloudSyncLastSyncEpochMs_\(id)"
        let lastAttemptKey = "cloudSyncLastAttemptEpochMs_\(id)"
        let lastErrorKey = "cloudSyncLastError_\(id)"
        let lastSyncEpochMs = Int64(UserDefaults.standard.double(forKey: lastSyncKey))
        let localStackChanged = hasLocalStackChangesSince(modelContext: modelContext, clientId: clientId, lastSyncEpochMs: lastSyncEpochMs)
        let localHistoryChanged = hasLocalHistoryChangesSince(modelContext: modelContext, clientId: clientId, lastSyncEpochMs: lastSyncEpochMs)
        let syncStartedEpochMs = recordPendingMutations(
            clientId: clientId,
            stackDirty: localStackChanged,
            historyDirty: localHistoryChanged
        )
        return SyncContext(
            id: id,
            clientId: clientId,
            lastSyncKey: lastSyncKey,
            lastAttemptKey: lastAttemptKey,
            lastErrorKey: lastErrorKey,
            localStackChanged: localStackChanged,
            localHistoryChanged: localHistoryChanged,
            syncStartedEpochMs: syncStartedEpochMs
        )
    }

    private static func resetSyncMetrics(ctx: SyncContext) {
        let defaults = UserDefaults.standard
        let keys = [
            "cloudSyncConflictRetryCount_\(ctx.id)", "cloudSyncBytesDownloaded_\(ctx.id)",
            "cloudSyncBytesUploaded_\(ctx.id)", "cloudSyncPullMs_\(ctx.id)",
            "cloudSyncMergeMs_\(ctx.id)", "cloudSyncPushMs_\(ctx.id)",
            "cloudSyncTotalMs_\(ctx.id)"
        ]
        keys.forEach { defaults.set(0, forKey: $0) }
    }

    private static func setPhase(_ phase: String, ctx: SyncContext) {
        UserDefaults.standard.set(phase, forKey: "cloudSyncPhase_\(ctx.id)")
    }

    private static func resolveManifestPartsOrNil(manifestId: String) async throws -> CloudSyncManifest? {
        do {
            return try await resolveManifestParts(manifestId: manifestId)
        } catch let error as CloudSyncError {
            guard shouldUseLegacyFallback(error) else { throw error }
            return nil
        }
    }

    static func shouldUseLegacyFallback(_ error: CloudSyncError) -> Bool {
        switch error {
        case .invalidResponse:
            return true
        case .manifestCodec(.decodeFailed):
            return true
        default:
            return false
        }
    }

    private static func performManifestSync(
        ctx: SyncContext,
        modelContext: ModelContext,
        client: ClientProfile,
        parts: CloudSyncManifest
    ) async throws {
        let (stackDownload, historyDownload) = try await downloadPartsForImmediateConsistency(parts: parts)
        let decoded = try await decodeRemoteParts(stackData: stackDownload.data, historyData: historyDownload.data)
        try await mergeRemotePartIfNeeded(decoded.stack, client: client, modelContext: modelContext)
        try await mergeRemotePartIfNeeded(decoded.history, client: client, modelContext: modelContext)
        await commitRevisionIfDownloaded(stackDownload, binId: parts.stackBinId)
        await commitRevisionIfDownloaded(historyDownload, binId: parts.historyBinId)
        let remoteChanged = stackDownload.data != nil || historyDownload.data != nil
        guard !shouldExitEarly(remoteChanged: remoteChanged, localChanged: ctx.localStackChanged || ctx.localHistoryChanged) else { return }
        guard ctx.localStackChanged || ctx.localHistoryChanged else { return }
        try await uploadPartsIfNeeded(ctx: ctx, modelContext: modelContext, client: client, parts: parts)
    }

    private static func performLegacySync(
        ctx: SyncContext,
        modelContext: ModelContext,
        client: ClientProfile
    ) async throws {
        let downloaded = try await CloudSyncManager.shared.downloadBackupWithRevision(binId: ctx.id)
        if let data = downloaded.data {
            try await mergeRemoteDataIfNeeded(data, client: client, modelContext: modelContext)
            await CloudSyncManager.shared.commitRevision(downloaded.revision, binId: ctx.id)
        }
        guard ctx.localStackChanged || ctx.localHistoryChanged else { return }
        let payload = try makeFullBackup(modelContext: modelContext, clientId: ctx.clientId)
        try await upsertPartWithRetry(binId: ctx.id, modelContext: modelContext, client: client) {
            payload
        } retryPayload: {
            try makeFullBackup(modelContext: modelContext, clientId: ctx.clientId)
        }
    }

    private static func markAttempt(ctx: SyncContext) {
        UserDefaults.standard.set(Double(Date().timeIntervalSince1970 * 1000), forKey: ctx.lastAttemptKey)
    }

    private static func fetchClient(modelContext: ModelContext, clientId: UUID) throws -> ClientProfile? {
        try modelContext.fetch(FetchDescriptor<ClientProfile>()).first { $0.id == clientId }
    }

    private static func decodeRemoteParts(
        stackData: Data?,
        historyData: Data?
    ) async throws -> (stack: OAKBackupData?, history: OAKBackupData?) {
        async let stack = decodeRemotePart(stackData)
        async let history = decodeRemotePart(historyData)
        return try await (stack, history)
    }

    private static func decodeRemotePart(_ data: Data?) async throws -> OAKBackupData? {
        guard let data else { return nil }
        return try await SupplementExportCodec.decodeBackupOffMain(data: data)
    }

    private static func mergeRemotePartIfNeeded(
        _ backup: OAKBackupData?,
        client: ClientProfile,
        modelContext: ModelContext
    ) async throws {
        guard let backup else { return }
        try await SupplementExportCodec.mergeBackupDataCooperatively(backup, client: client, context: modelContext)
    }

    private static func mergeRemoteDataIfNeeded(
        _ data: Data?,
        client: ClientProfile,
        modelContext: ModelContext
    ) async throws {
        let backup = try await decodeRemotePart(data)
        try await mergeRemotePartIfNeeded(backup, client: client, modelContext: modelContext)
    }

    private static func previewAndMergeConflict(
        _ data: Data,
        client: ClientProfile,
        modelContext: ModelContext
    ) async throws {
        guard let backup = try await decodeRemotePart(data) else { throw CloudSyncError.invalidResponse }
        let preview = try SyncConflictAnalyzer.analyze(
            backup: backup,
            clientId: client.id,
            context: modelContext
        )
        let manifestId = activeBinId(clientId: client.id) ?? ""
        recordConflictPreview(manifestId: manifestId, preview: preview)
        try await SupplementExportCodec.mergeBackupDataCooperatively(
            backup,
            client: client,
            context: modelContext
        )
    }

    private static func shouldExitEarly(remoteChanged: Bool, localChanged: Bool) -> Bool {
        !remoteChanged && !localChanged
    }

    private static func uploadPartsIfNeeded(
        ctx: SyncContext,
        modelContext: ModelContext,
        client: ClientProfile,
        parts: CloudSyncManifest
    ) async throws {
        if ctx.localStackChanged, ctx.localHistoryChanged {
            try await uploadBothPartsInParallel(ctx: ctx, modelContext: modelContext, client: client, parts: parts)
            return
        }
        if ctx.localStackChanged {
            let payload = try makeStackBackup(modelContext: modelContext, clientId: ctx.clientId)
            try await upsertPartWithRetry(binId: parts.stackBinId, modelContext: modelContext, client: client) {
                payload
            } retryPayload: {
                try makeStackBackup(modelContext: modelContext, clientId: ctx.clientId)
            }
        }
        if ctx.localHistoryChanged {
            let payload = try makeHistoryBackup(modelContext: modelContext, clientId: ctx.clientId)
            try await upsertPartWithRetry(binId: parts.historyBinId, modelContext: modelContext, client: client) {
                payload
            } retryPayload: {
                try makeHistoryBackup(modelContext: modelContext, clientId: ctx.clientId)
            }
        }
    }

    private static func uploadBothPartsInParallel(
        ctx: SyncContext,
        modelContext: ModelContext,
        client: ClientProfile,
        parts: CloudSyncManifest
    ) async throws {
        let stackPayload = try makeStackBackup(modelContext: modelContext, clientId: ctx.clientId)
        let historyPayload = try makeHistoryBackup(modelContext: modelContext, clientId: ctx.clientId)
        let (stackResult, historyResult) = await parallelUpserts(parts: parts, stackPayload: stackPayload, historyPayload: historyPayload)
        try await handleUpsertResult(stackResult, partId: parts.stackBinId, modelContext: modelContext, client: client) {
            try makeStackBackup(modelContext: modelContext, clientId: ctx.clientId)
        }
        try await handleUpsertResult(historyResult, partId: parts.historyBinId, modelContext: modelContext, client: client) {
            try makeHistoryBackup(modelContext: modelContext, clientId: ctx.clientId)
        }
    }

    private static func parallelUpserts(
        parts: CloudSyncManifest,
        stackPayload: Data,
        historyPayload: Data
    ) async -> (Result<Void, Error>, Result<Void, Error>) {
        let stackEtag = UserDefaults.standard.string(forKey: "cloudSyncEtagV2_\(parts.stackBinId)")
        let historyEtag = UserDefaults.standard.string(forKey: "cloudSyncEtagV2_\(parts.historyBinId)")
        let stackManager = CloudSyncManager()
        let historyManager = CloudSyncManager()
        async let stackResult = attemptUpsert(manager: stackManager, binId: parts.stackBinId, payload: stackPayload, etag: stackEtag)
        async let historyResult = attemptUpsert(manager: historyManager, binId: parts.historyBinId, payload: historyPayload, etag: historyEtag)
        return await (stackResult, historyResult)
    }

    private static func attemptUpsert(
        manager: CloudSyncManager,
        binId: String,
        payload: Data,
        etag: String?
    ) async -> Result<Void, Error> {
        do {
            try await manager.upsertBackup(binId: binId, jsonData: payload, ifMatchEtag: etag)
            return .success(())
        } catch {
            return .failure(error)
        }
    }

    private static func handleUpsertResult(
        _ result: Result<Void, Error>,
        partId: String,
        modelContext: ModelContext,
        client: ClientProfile,
        retryPayload: () throws -> Data
    ) async throws {
        switch result {
        case .success:
            return
        case .failure(let error):
            if isConflictError(error) {
                try await resolveConflictAndRetry(partId: partId, modelContext: modelContext, client: client, retryPayload: retryPayload)
                return
            }
            throw error
        }
    }

    static func isConflictError(_ error: Error) -> Bool {
        guard let sync = error as? CloudSyncError else { return false }
        if case let .serverError(statusCode, _) = sync {
            return statusCode == 412 || statusCode == 409
        }
        return false
    }

    private static func resolveConflictAndRetry(
        partId: String,
        modelContext: ModelContext,
        client: ClientProfile,
        retryPayload: () throws -> Data
    ) async throws {
        let latest = try await CloudSyncManager.shared.downloadBackupWithRevision(binId: partId)
        guard let data = latest.data else { throw CloudSyncError.invalidResponse }
        try await previewAndMergeConflict(data, client: client, modelContext: modelContext)
        await CloudSyncManager.shared.commitRevision(latest.revision, binId: partId)
        try await upsertPart(binId: partId, payload: retryPayload())
    }

    private static func downloadPartsIfChanged(parts: CloudSyncManifest) async throws -> (CloudSyncDownload, CloudSyncDownload) {
        async let stackTask = CloudSyncManager.shared.downloadBackupIfChangedWithRevision(binId: parts.stackBinId)
        async let historyTask = CloudSyncManager.shared.downloadBackupIfChangedWithRevision(binId: parts.historyBinId)
        return try await (stackTask, historyTask)
    }

    private static func downloadPartsForImmediateConsistency(parts: CloudSyncManifest) async throws -> (CloudSyncDownload, CloudSyncDownload) {
        let first = try await downloadPartsIfChanged(parts: parts)
        let onlyOnePartChanged = (first.0.data != nil) != (first.1.data != nil)
        guard onlyOnePartChanged else { return first }
        do {
            try await Task.sleep(for: immediateConsistencyWindow)
        } catch {
            return first
        }
        let second = try await downloadPartsIfChanged(parts: parts)
        let stack = first.0.data == nil ? second.0 : first.0
        let history = first.1.data == nil ? second.1 : first.1
        return (stack, history)
    }

    private static func commitRevisionIfDownloaded(_ download: CloudSyncDownload, binId: String) async {
        guard download.data != nil else { return }
        await CloudSyncManager.shared.commitRevision(download.revision, binId: binId)
    }

    private static func resolveManifestParts(manifestId: String) async throws -> CloudSyncManifest {
        let stackKey = "cloudSyncStackBinId_\(manifestId)"
        let historyKey = "cloudSyncHistoryBinId_\(manifestId)"
        let stack = (UserDefaults.standard.string(forKey: stackKey) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let history = (UserDefaults.standard.string(forKey: historyKey) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if !stack.isEmpty, !history.isEmpty { return CloudSyncManifest(v: 1, stackBinId: stack, historyBinId: history) }
        let downloaded = try await CloudSyncManager.shared.downloadBackupWithRevision(binId: manifestId)
        guard let data = downloaded.data else { throw CloudSyncError.invalidResponse }
        let decoded: CloudSyncManifest
        do {
            decoded = try CloudSyncManifestCodec.decode(data)
        } catch let error as CloudSyncManifestCodecError {
            throw CloudSyncError.manifestCodec(error)
        }
        UserDefaults.standard.set(decoded.stackBinId, forKey: stackKey)
        UserDefaults.standard.set(decoded.historyBinId, forKey: historyKey)
        await CloudSyncManager.shared.commitRevision(downloaded.revision, binId: manifestId)
        return decoded
    }

    private static func upsertPartWithRetry(
        binId: String,
        modelContext: ModelContext,
        client: ClientProfile,
        payload: () throws -> Data,
        retryPayload: () throws -> Data
    ) async throws {
        do {
            try await upsertPart(binId: binId, payload: payload())
        } catch CloudSyncError.serverError(let statusCode, _) where statusCode == 412 || statusCode == 409 {
            let latest = try await CloudSyncManager.shared.downloadBackupWithRevision(binId: binId)
            guard let data = latest.data else { throw CloudSyncError.invalidResponse }
            try await previewAndMergeConflict(data, client: client, modelContext: modelContext)
            await CloudSyncManager.shared.commitRevision(latest.revision, binId: binId)
            try await upsertPart(binId: binId, payload: retryPayload())
        }
    }

    private static func upsertPart(binId: String, payload: Data) async throws {
        let etag = UserDefaults.standard.string(forKey: "cloudSyncEtagV2_\(binId)")
        try await CloudSyncManager.shared.upsertBackup(binId: binId, jsonData: payload, ifMatchEtag: etag)
    }

    static func hasLocalChangesSince(
        modelContext: ModelContext,
        clientId: UUID,
        lastSyncEpochMs: Int64
    ) -> Bool {
        if hasLocalStackChangesSince(modelContext: modelContext, clientId: clientId, lastSyncEpochMs: lastSyncEpochMs) {
            return true
        }
        return hasLocalHistoryChangesSince(modelContext: modelContext, clientId: clientId, lastSyncEpochMs: lastSyncEpochMs)
    }

    private static func hasLocalStackChangesSince(modelContext: ModelContext, clientId: UUID, lastSyncEpochMs: Int64) -> Bool {
        guard lastSyncEpochMs > 0 else { return true }
        do {
            return try ClientScopedStore.hasSupplementChanges(
                modelContext: modelContext,
                clientId: clientId,
                since: lastSyncEpochMs
            )
        } catch {
            return true
        }
    }

    private static func hasLocalHistoryChangesSince(modelContext: ModelContext, clientId: UUID, lastSyncEpochMs: Int64) -> Bool {
        guard lastSyncEpochMs > 0 else { return true }
        do {
            return try ClientScopedStore.hasHistoryChanges(
                modelContext: modelContext,
                clientId: clientId,
                since: lastSyncEpochMs
            )
        } catch {
            return true
        }
    }

    private static func markSuccess(ctx: SyncContext, startedAt: Date) {
        let defaults = UserDefaults.standard
        let now = Double(Date().timeIntervalSince1970 * 1000)
        defaults.set(now, forKey: ctx.lastSyncKey)
        defaults.set(now, forKey: "oakLastSyncEpochMs")
        defaults.set(Int(startedAt.distance(to: Date()) * 1000), forKey: "cloudSyncTotalMs_\(ctx.id)")
        clearFailureState(binId: ctx.id)
        SyncRetryStore.clear(manifestId: ctx.id)
        SyncMutationQueueStore.clearSynced(
            clientId: ctx.clientId,
            parts: Set(SyncMutationPart.allCases),
            syncStartedEpochMs: ctx.syncStartedEpochMs
        )
        SyncOperationJournalStore.append(
            manifestId: ctx.id,
            entry: SyncJournalEntry(epochMs: Int64(now), event: .success)
        )
        setPhase("done", ctx: ctx)
        DebugReporter.report("cloud_sync_success", fields: telemetryFields(binId: ctx.id, clientId: ctx.clientId, error: nil))
    }

    static func clearFailureState(
        binId: String,
        defaults: UserDefaults = .standard
    ) {
        defaults.removeObject(forKey: "cloudSyncLastError_\(binId)")
        defaults.removeObject(forKey: lastFailureKey)
    }

    private static func markFailure(ctx: SyncContext, error: Error) {
        let nowEpochMs = Int64(Date().timeIntervalSince1970 * 1000)
        let nowMs = Double(nowEpochMs)
        let message = error.localizedDescription
        let retry = SyncRetryStore.recordFailure(manifestId: ctx.id, nowEpochMs: nowEpochMs)
        SyncOperationJournalStore.append(
            manifestId: ctx.id,
            entry: SyncJournalEntry(
                epochMs: nowEpochMs,
                event: .failure,
                retryCount: retry.failureCount,
                nextRetryEpochMs: retry.nextRetryEpochMs
            )
        )
        UserDefaults.standard.set(nowMs, forKey: ctx.lastAttemptKey)
        UserDefaults.standard.set(message, forKey: ctx.lastErrorKey)
        UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: lastFailureKey)
        setPhase("error", ctx: ctx)

        let logTsKey = "cloudSyncLastFailureLogEpochMs_\(ctx.id)"
        let logMsgKey = "cloudSyncLastFailureLogMessage_\(ctx.id)"
        let lastLoggedMs = UserDefaults.standard.double(forKey: logTsKey)
        let lastLoggedMsg = UserDefaults.standard.string(forKey: logMsgKey) ?? ""
        if lastLoggedMsg == message, (nowMs - lastLoggedMs) < 60_000 {
            return
        }
        UserDefaults.standard.set(nowMs, forKey: logTsKey)
        UserDefaults.standard.set(message, forKey: logMsgKey)
        DebugReporter.report("cloud_sync_failure", fields: telemetryFields(binId: ctx.id, clientId: ctx.clientId, error: error))
    }

    static func telemetryFields(
        binId _: String,
        clientId _: UUID,
        error: Error?
    ) -> [String: String] {
        guard let error else { return [:] }
        var fields = ["error_type": errorType(error)]
        if let cloudError = error as? CloudSyncError,
           case let .serverError(statusCode, _) = cloudError {
            fields["status_code"] = "\(statusCode)"
        }
        return fields
    }

    private static func errorType(_ error: Error) -> String {
        guard let error = error as? CloudSyncError else { return "unknown" }
        switch error {
        case .invalidBinId: return "invalid_bin_id"
        case .invalidResponse: return "invalid_response"
        case .serverError: return "server_error"
        case .networkError: return "network_error"
        case .cryptoError: return "crypto_error"
        case .payloadCodec: return "payload_codec"
        case .manifestCodec: return "manifest_codec"
        }
    }

    private static func recordPendingMutations(
        clientId: UUID,
        stackDirty: Bool,
        historyDirty: Bool
    ) -> Int64 {
        let epochMs = Int64(Date().timeIntervalSince1970 * 1000)
        if stackDirty {
            SyncMutationQueueStore.markDirty(clientId: clientId, part: .stack, nowEpochMs: epochMs)
        }
        if historyDirty {
            SyncMutationQueueStore.markDirty(clientId: clientId, part: .history, nowEpochMs: epochMs)
        }
        return epochMs
    }

    private static func recordSyncStart(ctx: SyncContext) {
        SyncOperationJournalStore.append(
            manifestId: ctx.id,
            entry: SyncJournalEntry(
                epochMs: ctx.syncStartedEpochMs,
                event: .start,
                stackDirty: ctx.localStackChanged,
                historyDirty: ctx.localHistoryChanged
            )
        )
    }

    private static func recordBackoff(manifestId: String, state: SyncRetryState, nowEpochMs: Int64) {
        SyncOperationJournalStore.append(
            manifestId: manifestId,
            entry: SyncJournalEntry(
                epochMs: nowEpochMs,
                event: .backoff,
                retryCount: state.failureCount,
                nextRetryEpochMs: state.nextRetryEpochMs
            )
        )
    }

    private static func recordConflictPreview(manifestId: String, preview: SyncConflictPreview) {
        guard !manifestId.isEmpty else { return }
        let defaults = UserDefaults.standard
        let retryKey = "cloudSyncConflictRetryCount_\(manifestId)"
        defaults.set(defaults.integer(forKey: retryKey) + 1, forKey: retryKey)
        defaults.set(preview.remoteWins, forKey: "cloudSyncConflictRemoteWins_\(manifestId)")
        defaults.set(preview.localWins, forKey: "cloudSyncConflictLocalWins_\(manifestId)")
        defaults.set(preview.tieLocalWins, forKey: "cloudSyncConflictTieLocalWins_\(manifestId)")
        defaults.set("retryingConflict", forKey: "cloudSyncPhase_\(manifestId)")
        SyncOperationJournalStore.append(
            manifestId: manifestId,
            entry: SyncJournalEntry(
                epochMs: Int64(Date().timeIntervalSince1970 * 1000),
                event: .conflict,
                remoteWins: preview.remoteWins,
                localWins: preview.localWins,
                tieLocalWins: preview.tieLocalWins
            )
        )
    }

    private static func markActivity() {
        UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: lastActivityKey)
    }

    static func pollInterval(
        nowEpoch: TimeInterval = Date().timeIntervalSince1970,
        lastFailureEpoch: TimeInterval? = nil,
        lastActivityEpoch: TimeInterval? = nil
    ) -> Duration {
        let now = nowEpoch
        let lastFailure = lastFailureEpoch ?? UserDefaults.standard.double(forKey: lastFailureKey)
        if lastFailure > 0 {
            let elapsed = now - lastFailure
            if elapsed < 30 { return .seconds(15) }
            if elapsed < 2 * 60 { return .seconds(30) }
            return .seconds(120)
        }

        let lastActivity = lastActivityEpoch ?? UserDefaults.standard.double(forKey: lastActivityKey)
        let activityElapsed = lastActivity > 0 ? (now - lastActivity) : 10_000

        if activityElapsed < 2 * 60 { return .seconds(PerformanceBudgets.realtimeActivePollSeconds) }
        if activityElapsed < 10 * 60 { return .seconds(120) }
        return .seconds(PerformanceBudgets.realtimeIdlePollSeconds)
    }

    private static func activeBinId(clientId: UUID?) -> String? {
        CloudSyncProfileStore().activeManifestId(clientId: clientId)
    }

    private static func makeRealtimeSession(clientId: UUID?) -> CloudSyncRealtimeSession? {
        guard let clientId, let manifestId = activeBinId(clientId: clientId) else { return nil }
        return CloudSyncRealtimeSession(clientId: clientId, manifestId: manifestId)
    }

    static func shouldReuseRealtimeSession(
        current: CloudSyncRealtimeSession?,
        requested: CloudSyncRealtimeSession?,
        hasTask: Bool
    ) -> Bool {
        hasTask && current == requested
    }

    private static func ownsRealtimeSession(_ session: CloudSyncRealtimeSession) -> Bool {
        !Task.isCancelled && realtimeSession == session
    }

    private static func makeStackBackup(modelContext: ModelContext, clientId: UUID) throws -> Data {
        let supplements = try ClientScopedStore.supplements(
            modelContext: modelContext,
            clientId: clientId
        )
        return try SupplementExportCodec.encodeBackup(supplements: supplements, records: [])
    }

    private static func makeHistoryBackup(modelContext: ModelContext, clientId: UUID) throws -> Data {
        let records = try historyRecords(modelContext: modelContext, clientId: clientId)
        return try SupplementExportCodec.encodeBackup(supplements: [], records: records)
    }

    private static func makeFullBackup(modelContext: ModelContext, clientId: UUID) throws -> Data {
        let supplements = try ClientScopedStore.supplements(
            modelContext: modelContext,
            clientId: clientId
        )
        let records = try historyRecords(modelContext: modelContext, clientId: clientId)
        return try SupplementExportCodec.encodeBackup(supplements: supplements, records: records)
    }

    private static func historyRecords(modelContext: ModelContext, clientId: UUID) throws -> [IntakeRecord] {
        let cutoff = Calendar.current.date(byAdding: .day, value: -90, to: .now) ?? .now
        return try ClientScopedStore.recentHistoryRecords(
            modelContext: modelContext,
            clientId: clientId,
            cutoff: cutoff,
            limit: PerformanceBudgets.syncHistoryLimit
        )
    }
}
