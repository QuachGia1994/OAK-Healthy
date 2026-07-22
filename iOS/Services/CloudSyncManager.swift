import Foundation
import SwiftData
import UIKit

public enum CloudSyncError: Error, Sendable, LocalizedError {
    case invalidBinId
    case invalidResponse
    case missingAccessKey
    case serverError(statusCode: Int, body: String)
    case networkError(message: String)
    case decodingError(message: String)
    case cryptoError(message: String)
    case payloadCodec(CloudSyncPayloadCodecError)
    case manifestCodec(CloudSyncManifestCodecError)

    public var errorDescription: String? {
        switch self {
        case .invalidBinId:
            return "Mã liên kết không hợp lệ."
        case .invalidResponse:
            return "Phản hồi máy chủ không hợp lệ."
        case .missingAccessKey:
            return "Thiếu API Key (JSONBIN_API_KEY) từ xcconfig."
        case .serverError(let statusCode, let body):
            if statusCode == 522 {
                return "Máy chủ phản hồi quá lâu (522). Vui lòng thử lại sau."
            }
            let trimmed = body.trimmingCharacters(in: .whitespacesAndNewlines)
            let message = trimmed.isEmpty ? "Unknown server error" : String(trimmed.prefix(240))
            return "Lỗi máy chủ (\(statusCode)): \(message)"
        case .networkError(let message):
            return "Lỗi mạng: \(message)"
        case .decodingError(let message):
            return "Lỗi dữ liệu: \(message)"
        case .cryptoError(let message):
            return "Lỗi mã hoá: \(message)"
        case .payloadCodec(let error):
            return "Lỗi giải mã payload: \(payloadCodecMessage(error))"
        case .manifestCodec(let error):
            return "Lỗi manifest: \(manifestCodecMessage(error))"
        }
    }
    
    private func payloadCodecMessage(_ error: CloudSyncPayloadCodecError) -> String {
        switch error {
        case .wrapperJSONInvalid: return "wrapper_json_invalid"
        case let .missingCompressedField(field): return "missing_field_\(field)"
        case .base64DecodeFailed: return "base64_decode_failed"
        case .inflateFailed: return "inflate_failed"
        }
    }
    
    private func manifestCodecMessage(_ error: CloudSyncManifestCodecError) -> String {
        switch error {
        case .encodeFailed: return "encode_failed"
        case .decodeFailed: return "decode_failed"
        }
    }
}

struct CloudSyncDownload: Sendable {
    let data: Data?
    let revision: String?
}

public actor CloudSyncManager {
    public static let baseURL: URL = {
        URL(string: "https://api.jsonbin.io/v3/b") ?? URL(fileURLWithPath: "/")
    }()
    
    public static let shared = CloudSyncManager()
    
    private let session: URLSession

    public init() {
        self.session = Self.makeSession()
    }

    private static func makeSession() -> URLSession {
        let config = URLSessionConfiguration.ephemeral
        config.waitsForConnectivity = false
        config.timeoutIntervalForRequest = 15
        config.timeoutIntervalForResource = 20
        config.requestCachePolicy = .reloadIgnoringLocalCacheData
        return URLSession(configuration: config)
    }
    
    public func upsertBackup(
        binId: String,
        jsonData: Data,
        ifMatchEtag: String? = nil
    ) async throws(CloudSyncError) {
        let id = binId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard FirebaseCloudStore.isValidBinId(id) else { throw CloudSyncError.invalidBinId }
        let payload = try encodePayloadForCloud(jsonData)
        do {
            let newRev = try await FirebaseCloudStore.write(id: id, payload: payload, expectedRev: ifMatchEtag)
            saveEtag(newRev, binId: id)
        } catch is FirebaseConflictError {
            throw CloudSyncError.serverError(statusCode: 412, body: "Conflict")
        } catch {
            throw CloudSyncError.networkError(message: error.localizedDescription)
        }
    }
    
    public func uploadBackup(
        jsonData: Data
    ) async throws(CloudSyncError) -> String {
        let payload = try encodePayloadForCloud(jsonData)
        do {
            let result = try await FirebaseCloudStore.createBin(payload: payload)
            saveEtag(result.rev, binId: result.id)
            return result.id
        } catch {
            throw CloudSyncError.networkError(message: error.localizedDescription)
        }
    }
    
    public func downloadBackup(
        binId: String
    ) async throws(CloudSyncError) -> Data {
        let downloaded = try await downloadBackupWithRevision(binId: binId)
        guard let data = downloaded.data else { throw CloudSyncError.invalidResponse }
        commitRevision(downloaded.revision, binId: binId)
        return data
    }

    func downloadBackupWithRevision(
        binId: String
    ) async throws(CloudSyncError) -> CloudSyncDownload {
        let id = binId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard FirebaseCloudStore.isValidBinId(id) else { throw CloudSyncError.invalidBinId }
        do {
            let node = try await FirebaseCloudStore.readNode(id: id)
            let payload = (node.payload ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            if payload.isEmpty { throw CloudSyncError.invalidResponse }
            let data = try decodePayloadFromCloud(payload)
            return CloudSyncDownload(data: data, revision: node.rev)
        } catch let error as CloudSyncError {
            throw error
        } catch {
            throw CloudSyncError.networkError(message: error.localizedDescription)
        }
    }

    public func downloadBackupIfChanged(
        binId: String
    ) async throws(CloudSyncError) -> Data? {
        let downloaded = try await downloadBackupIfChangedWithRevision(binId: binId)
        guard let data = downloaded.data else { return nil }
        commitRevision(downloaded.revision, binId: binId)
        return data
    }

    func downloadBackupIfChangedWithRevision(
        binId: String
    ) async throws(CloudSyncError) -> CloudSyncDownload {
        let id = binId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard FirebaseCloudStore.isValidBinId(id) else { throw CloudSyncError.invalidBinId }
        do {
            let known = storedEtag(binId: id)
            let node = try await FirebaseCloudStore.readNode(id: id)
            if let rev = node.rev, rev == known {
                return CloudSyncDownload(data: nil, revision: rev)
            }
            let payload = (node.payload ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            if payload.isEmpty { throw CloudSyncError.invalidResponse }
            let data = try decodePayloadFromCloud(payload)
            return CloudSyncDownload(data: data, revision: node.rev)
        } catch let error as CloudSyncError {
            throw error
        } catch {
            throw CloudSyncError.networkError(message: error.localizedDescription)
        }
    }

    func commitRevision(_ revision: String?, binId: String) {
        guard let revision else { return }
        let id = binId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !id.isEmpty else { return }
        saveEtag(revision, binId: id)
    }
    
    public func deleteBackup(
        binId: String
    ) async throws(CloudSyncError) {
        let id = binId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard FirebaseCloudStore.isValidBinId(id) else { throw CloudSyncError.invalidBinId }
        do {
            try await FirebaseCloudStore.delete(id: id)
        } catch {
            throw CloudSyncError.networkError(message: error.localizedDescription)
        }
    }

    private func fetch(request: URLRequest) async throws(CloudSyncError) -> (Data, HTTPURLResponse) {
        let plan = makeFetchPlan(request: request)
        var lastError: CloudSyncError?
        for attempt in 1...plan.maxAttempts {
            let result = await attemptFetch(request: request, plan: plan, attempt: attempt)
            switch result {
            case let .success(data, http):
                return (data, http)
            case let .retry(error):
                lastError = error
                try? await Task.sleep(for: backoffDelay(attempt: attempt))
            case let .failure(error):
                throw error
            }
        }
        throw lastError ?? CloudSyncError.networkError(message: "Unknown network error")
    }
    
    private struct FetchPlan: Sendable {
        let canRetry: Bool
        let maxAttempts: Int
    }
    
    private enum FetchAttemptResult: Sendable {
        case success(Data, HTTPURLResponse)
        case retry(CloudSyncError)
        case failure(CloudSyncError)
    }
    
    private func makeFetchPlan(request: URLRequest) -> FetchPlan {
        let method = (request.httpMethod ?? "GET").uppercased()
        let canRetry = method != "POST"
        return FetchPlan(canRetry: canRetry, maxAttempts: canRetry ? 4 : 1)
    }
    
    private func attemptFetch(request: URLRequest, plan: FetchPlan, attempt: Int) async -> FetchAttemptResult {
        do {
            let working = preparedRequest(request)
            let (data, http) = try await performSessionRequest(working)
            if plan.canRetry, attempt < plan.maxAttempts, shouldRetryHTTPStatus(statusCode: http.statusCode) {
                let body = String(data: data, encoding: .utf8) ?? ""
                return .retry(.serverError(statusCode: http.statusCode, body: body))
            }
            return .success(data, http)
        } catch let error as CloudSyncError {
            if plan.canRetry, attempt < plan.maxAttempts, shouldRetry(error: error) { return .retry(error) }
            return .failure(error)
        } catch let urlError as URLError {
            let mapped = mapURLError(urlError)
            if plan.canRetry, attempt < plan.maxAttempts { return .retry(mapped) }
            return .failure(mapped)
        } catch {
            let mapped = CloudSyncError.networkError(message: error.localizedDescription)
            if plan.canRetry, attempt < plan.maxAttempts { return .retry(mapped) }
            return .failure(mapped)
        }
    }
    
    private func preparedRequest(_ request: URLRequest) -> URLRequest {
        var working = request
        if working.timeoutInterval <= 0 { working.timeoutInterval = 15 }
        return working
    }
    
    private func performSessionRequest(_ request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw CloudSyncError.invalidResponse }
        return (data, http)
    }
    
    private func shouldRetryHTTPStatus(statusCode: Int) -> Bool {
        statusCode == 408 || statusCode == 429 || statusCode == 522 || (500...599).contains(statusCode)
    }
    
    private func shouldRetry(error: CloudSyncError) -> Bool {
        switch error {
        case .networkError:
            return true
        case .serverError(let statusCode, _):
            return statusCode == 408 || statusCode == 429 || statusCode == 522 || (500...599).contains(statusCode)
        default:
            return false
        }
    }
    
    private func backoffDelay(attempt: Int) -> Duration {
        let baseMs: Int
        switch attempt {
        case 1: baseMs = 400
        case 2: baseMs = 900
        case 3: baseMs = 1500
        default: baseMs = 2000
        }
        return .milliseconds(baseMs + Int.random(in: 0...200))
    }
    
    private func mapURLError(_ error: URLError) -> CloudSyncError {
        switch error.code {
        case .timedOut:
            return .networkError(message: "Kết nối quá thời gian")
        case .notConnectedToInternet:
            return .networkError(message: "Không có internet")
        case .cannotFindHost, .cannotConnectToHost, .dnsLookupFailed:
            return .networkError(message: "Không thể kết nối máy chủ")
        default:
            return .networkError(message: error.localizedDescription)
        }
    }

    private func decodeJSONObject(_ data: Data) throws(CloudSyncError) -> Any {
        do {
            return try JSONSerialization.jsonObject(with: data)
        } catch {
            throw CloudSyncError.decodingError(message: error.localizedDescription)
        }
    }

    private func encodeJSONObject(_ object: Any) throws(CloudSyncError) -> Data {
        do {
            return try JSONSerialization.data(withJSONObject: object, options: [])
        } catch {
            throw CloudSyncError.decodingError(message: error.localizedDescription)
        }
    }
    
    private func recordData(from data: Data) throws(CloudSyncError) -> Data {
        let obj = try decodeJSONObject(data) as? [String: Any]
        let record = obj?["record"]
        guard let record else { throw CloudSyncError.invalidResponse }
        return try encodeJSONObject(record)
    }
    
    private func encryptPayloadIfNeeded(_ data: Data) throws(CloudSyncError) -> Data {
        do {
            return try CloudSyncCrypto.encryptIfEnabled(data)
        } catch {
            throw CloudSyncError.cryptoError(message: error.localizedDescription)
        }
    }

    private func decryptPayloadIfNeeded(_ data: Data) throws(CloudSyncError) -> Data {
        do {
            return try CloudSyncCrypto.decryptIfNeeded(data)
        } catch {
            throw CloudSyncError.cryptoError(message: error.localizedDescription)
        }
    }
    
    private func encodePayloadForCloud(_ jsonData: Data) throws(CloudSyncError) -> String {
        let payload = try encryptPayloadIfNeeded(jsonData)
        guard let text = String(data: payload, encoding: .utf8) else { throw CloudSyncError.invalidResponse }
        return text
    }
    
    private func decodePayloadFromCloud(_ payload: String) throws(CloudSyncError) -> Data {
        guard let data = payload.data(using: .utf8) else { throw CloudSyncError.invalidResponse }
        return try decryptPayloadIfNeeded(data)
    }
    
    private func responseEtag(http: HTTPURLResponse) -> String? {
        let raw = http.value(forHTTPHeaderField: "ETag") ?? http.value(forHTTPHeaderField: "Etag")
        let trimmed = (raw ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
    
    private func storedEtag(binId: String) -> String? {
        let key = "cloudSyncEtagV2_\(binId)"
        let raw = UserDefaults.standard.string(forKey: key) ?? ""
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
    
    private func saveEtag(_ etag: String, binId: String) {
        let trimmed = etag.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        UserDefaults.standard.set(trimmed, forKey: "cloudSyncEtagV2_\(binId)")
    }
}

@MainActor
enum CloudSyncAutoSync {
    private static var realtimeTask: Task<Void, Never>?
    private static var pendingSyncTask: Task<Void, Never>?
    private static var realtimeListener: FirebaseRealtimeSyncListener?
    private static var realtimeManifestId: String?
    private static var isSyncing = false
    private static var syncAgainAfterCurrent = false
    private static let lastActivityKey = "cloudSyncLastActivityEpoch"
    private static let lastFailureKey = "cloudSyncLastFailureEpoch"
    private static let immediateConsistencyWindow: Duration = .milliseconds(180)
    private static let realtimeStartupGrace: Duration = .milliseconds(250)

    static func startRealtimeSync(
        modelContext: ModelContext,
        activeClientManager: ActiveClientManager
    ) {
        let requestedManifestId = activeBinId()
        guard realtimeTask == nil || realtimeManifestId != requestedManifestId else { return }
        markActivity()
        guard realtimeTask == nil else {
            realtimeManifestId = requestedManifestId
            if let requestedManifestId, !requestedManifestId.isEmpty {
                Task { await realtimeListener?.start(manifestId: requestedManifestId) }
            } else {
                realtimeListener?.stop()
            }
            return
        }
        realtimeManifestId = requestedManifestId
        let listener = FirebaseRealtimeSyncListener(modelContext: modelContext, activeClientManager: activeClientManager)
        realtimeListener = listener
        realtimeTask = Task { @MainActor in
            await realtimeLoop(
                modelContext: modelContext,
                activeClientManager: activeClientManager,
                listener: listener
            )
        }
    }

    static func stopRealtimeSync() {
        realtimeTask?.cancel()
        realtimeTask = nil
        pendingSyncTask?.cancel()
        pendingSyncTask = nil
        syncAgainAfterCurrent = false
        realtimeListener?.stop()
        realtimeListener = nil
        realtimeManifestId = nil
    }
    
    static func requestSyncSoon(modelContext: ModelContext, clientId: UUID?) {
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
            await syncIfEnabled(modelContext: modelContext, clientId: clientId)
        }
    }
    
    private static func realtimeLoop(
        modelContext: ModelContext,
        activeClientManager: ActiveClientManager,
        listener: FirebaseRealtimeSyncListener
    ) async {
        do {
            try await Task.sleep(for: realtimeStartupGrace)
        } catch {
            return
        }
        guard !Task.isCancelled else { return }
        await syncIfEnabled(modelContext: modelContext, clientId: activeClientManager.currentClientId)
        guard !Task.isCancelled else { return }
        if let binId = activeBinId(), !binId.isEmpty {
            await listener.start(manifestId: binId)
        }
        while !Task.isCancelled {
            do {
                try await Task.sleep(for: pollInterval())
            } catch {
                return
            }
            await syncIfEnabled(modelContext: modelContext, clientId: activeClientManager.currentClientId)
        }
    }
    
    static func syncIfEnabled(modelContext: ModelContext, clientId: UUID?) async -> Bool {
        guard !isSyncing else {
            syncAgainAfterCurrent = true
            return false
        }
        isSyncing = true
        defer { isSyncing = false }
        var didSucceed = true
        repeat {
            syncAgainAfterCurrent = false
            didSucceed = await performSyncIfEnabled(modelContext: modelContext, clientId: clientId) && didSucceed
        } while syncAgainAfterCurrent && !Task.isCancelled
        return didSucceed
    }

    private static func performSyncIfEnabled(modelContext: ModelContext, clientId: UUID?) async -> Bool {
        guard let ctx = makeSyncContext(modelContext: modelContext, clientId: clientId) else { return false }
        do {
            markAttempt(ctx: ctx)
            guard let client = fetchClient(modelContext: modelContext, clientId: ctx.clientId) else { return false }
            let parts = try await resolveManifestParts(manifestId: ctx.id)
            let (stackDownload, historyDownload) = try await downloadPartsForImmediateConsistency(parts: parts)
            let decodedParts = try await decodeRemoteParts(stackData: stackDownload.data, historyData: historyDownload.data)
            try await mergeRemotePartIfNeeded(decodedParts.stack, client: client, modelContext: modelContext)
            try await mergeRemotePartIfNeeded(decodedParts.history, client: client, modelContext: modelContext)
            await commitRevisionIfDownloaded(stackDownload, binId: parts.stackBinId)
            await commitRevisionIfDownloaded(historyDownload, binId: parts.historyBinId)
            let remoteChanged = stackDownload.data != nil || historyDownload.data != nil
            if shouldExitEarly(remoteChanged: remoteChanged, localChanged: ctx.localStackChanged || ctx.localHistoryChanged) {
                markSuccess(ctx: ctx)
                return true
            }
            if ctx.localStackChanged || ctx.localHistoryChanged {
                try await uploadPartsIfNeeded(ctx: ctx, modelContext: modelContext, client: client, parts: parts)
            }
            markSuccess(ctx: ctx)
            return true
        } catch {
            markFailure(ctx: ctx, error: error)
            return false
        }
    }

    private struct SyncContext: Sendable {
        let binId: String
        let id: String
        let clientId: UUID
        let lastSyncKey: String
        let lastAttemptKey: String
        let lastErrorKey: String
        let localStackChanged: Bool
        let localHistoryChanged: Bool
    }

    private static func makeSyncContext(modelContext: ModelContext, clientId: UUID?) -> SyncContext? {
        guard UserDefaults.standard.bool(forKey: "isAutoSyncEnabled") else { return nil }
        guard let binId = activeBinId() else { return nil }
        guard let clientId else { return nil }
        let id = binId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !id.isEmpty else { return nil }
        let lastSyncKey = "cloudSyncLastSyncEpochMs_\(id)"
        let lastAttemptKey = "cloudSyncLastAttemptEpochMs_\(id)"
        let lastErrorKey = "cloudSyncLastError_\(id)"
        let lastSyncEpochMs = Int64(UserDefaults.standard.double(forKey: lastSyncKey))
        let localStackChanged = hasLocalStackChangesSince(modelContext: modelContext, clientId: clientId, lastSyncEpochMs: lastSyncEpochMs)
        let localHistoryChanged = hasLocalHistoryChangesSince(modelContext: modelContext, clientId: clientId, lastSyncEpochMs: lastSyncEpochMs)
        return SyncContext(
            binId: binId,
            id: id,
            clientId: clientId,
            lastSyncKey: lastSyncKey,
            lastAttemptKey: lastAttemptKey,
            lastErrorKey: lastErrorKey,
            localStackChanged: localStackChanged,
            localHistoryChanged: localHistoryChanged
        )
    }

    private static func markAttempt(ctx: SyncContext) {
        UserDefaults.standard.set(Double(Date().timeIntervalSince1970 * 1000), forKey: ctx.lastAttemptKey)
    }

    private static func fetchClient(modelContext: ModelContext, clientId: UUID) -> ClientProfile? {
        (try? modelContext.fetch(FetchDescriptor<ClientProfile>()))?.first { $0.id == clientId }
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
    
    private static func isConflictError(_ error: Error) -> Bool {
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
        try await mergeRemoteDataIfNeeded(data, client: client, modelContext: modelContext)
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
            try await mergeRemoteDataIfNeeded(data, client: client, modelContext: modelContext)
            await CloudSyncManager.shared.commitRevision(latest.revision, binId: binId)
            try await upsertPart(binId: binId, payload: retryPayload())
        }
    }
    
    private static func upsertPart(binId: String, payload: Data) async throws {
        let etag = UserDefaults.standard.string(forKey: "cloudSyncEtagV2_\(binId)")
        try await CloudSyncManager.shared.upsertBackup(binId: binId, jsonData: payload, ifMatchEtag: etag)
    }
    
    private static func hasLocalStackChangesSince(modelContext: ModelContext, clientId: UUID, lastSyncEpochMs: Int64) -> Bool {
        guard lastSyncEpochMs > 0 else { return true }
        do {
            var descriptor = FetchDescriptor<UserSupplement>(
                predicate: #Predicate {
                    $0.updatedAtEpochMs > lastSyncEpochMs ||
                        ($0.deletedAtEpochMs != nil && $0.deletedAtEpochMs! > lastSyncEpochMs)
                }
            )
            descriptor.fetchLimit = 50
            return try modelContext.fetch(descriptor).contains { $0.client?.id == clientId }
        } catch {
            return true
        }
    }
    
    private static func hasLocalHistoryChangesSince(modelContext: ModelContext, clientId: UUID, lastSyncEpochMs: Int64) -> Bool {
        guard lastSyncEpochMs > 0 else { return true }
        do {
            var descriptor = FetchDescriptor<IntakeRecord>(
                predicate: #Predicate { $0.updatedAtEpochMs > lastSyncEpochMs },
                sortBy: [SortDescriptor(\IntakeRecord.updatedAtEpochMs, order: .reverse)]
            )
            descriptor.fetchLimit = 100
            return try modelContext.fetch(descriptor).contains { $0.supplement?.client?.id == clientId }
        } catch {
            return true
        }
    }

    private static func markSuccess(ctx: SyncContext) {
        let now = Double(Date().timeIntervalSince1970 * 1000)
        UserDefaults.standard.set(now, forKey: ctx.lastSyncKey)
        UserDefaults.standard.set(now, forKey: "oakLastSyncEpochMs")
        DebugReporter.report("cloud_sync_success", fields: telemetryFields(binId: ctx.id, clientId: ctx.clientId, error: nil))
    }

    private static func markFailure(ctx: SyncContext, error: Error) {
        let nowMs = Double(Date().timeIntervalSince1970 * 1000)
        let message = error.localizedDescription
        UserDefaults.standard.set(nowMs, forKey: ctx.lastAttemptKey)
        UserDefaults.standard.set(message, forKey: ctx.lastErrorKey)
        UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: lastFailureKey)
        
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
    
    static func telemetryFields(binId: String, clientId: UUID, error: Error?) -> [String: String] {
        var fields: [String: String] = [
            "binId": binId,
            "clientId": clientId.uuidString,
            "bin_id": binId,
            "client_id": clientId.uuidString
        ]
        
        guard let error else { return fields }
        let message = truncated(error.localizedDescription)
        fields["error"] = message
        fields["error_message"] = message
        fields["error_type"] = errorType(error)
        
        if let cloudError = error as? CloudSyncError,
           case let .serverError(statusCode, body) = cloudError {
            fields["status_code"] = "\(statusCode)"
            fields["server_body"] = truncated(body.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines))
        }
        
        return fields
    }
    
    private static func errorType(_ error: Error) -> String {
        guard let error = error as? CloudSyncError else { return "unknown" }
        switch error {
        case .invalidBinId: return "invalid_bin_id"
        case .invalidResponse: return "invalid_response"
        case .missingAccessKey: return "missing_access_key"
        case .serverError: return "server_error"
        case .networkError: return "network_error"
        case .decodingError: return "decoding_error"
        case .cryptoError: return "crypto_error"
        case .payloadCodec: return "payload_codec"
        case .manifestCodec: return "manifest_codec"
        }
    }
    
    private static func truncated(_ value: String, limit: Int = 240) -> String {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count > limit else { return trimmed }
        let end = trimmed.index(trimmed.startIndex, offsetBy: limit)
        return String(trimmed[..<end])
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
        
        if activityElapsed < 20 { return .seconds(5) }
        if activityElapsed < 2 * 60 { return .seconds(30) }
        if activityElapsed < 10 * 60 { return .seconds(120) }
        return .seconds(600)
    }
    
    private static func activeBinId() -> String? {
        let hosted = UserDefaults.standard.string(forKey: "cloudSyncHostedBinId") ?? ""
        let linked = UserDefaults.standard.string(forKey: "cloudSyncLinkedBinId") ?? ""
        let trimmed = (hosted.isEmpty ? linked : hosted).trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
    
    private static func makeStackBackup(modelContext: ModelContext, clientId: UUID) throws -> Data {
        let supplements = try modelContext.fetch(FetchDescriptor<UserSupplement>())
            .filter { $0.client?.id == clientId }
        return try SupplementExportCodec.encodeBackup(supplements: supplements, records: [])
    }
    
    private static func makeHistoryBackup(modelContext: ModelContext, clientId: UUID) throws -> Data {
        let cutoff = Calendar.current.date(byAdding: .day, value: -90, to: .now) ?? .now
        var descriptor = FetchDescriptor<IntakeRecord>(
            predicate: #Predicate { $0.date >= cutoff },
            sortBy: [SortDescriptor(\IntakeRecord.date, order: .reverse)]
        )
        descriptor.fetchLimit = 8_000
        let records = try modelContext.fetch(descriptor)
            .filter { $0.supplement?.client?.id == clientId }
            .prefix(5_000)
        return try SupplementExportCodec.encodeBackup(supplements: [], records: Array(records))
    }
}
