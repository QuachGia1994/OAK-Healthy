import Foundation
import SwiftData

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

public actor CloudSyncManager {
    public static let baseURL: URL = {
        URL(string: "https://api.jsonbin.io/v3/b") ?? URL(fileURLWithPath: "/")
    }()
    
    public static let shared = CloudSyncManager()
    
    private var autoSyncTask: Task<Void, Never>?
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
    
    public func startAutoSync() {
        guard autoSyncTask == nil else { return }
        autoSyncTask = Task {
            while !Task.isCancelled {
                do {
                    try await Task.sleep(for: .seconds(15 * 60))
                } catch {
                    break
                }
            }
        }
    }
    
    public func stopAutoSync() {
        autoSyncTask?.cancel()
        autoSyncTask = nil
    }
    
    public func upsertBackup(
        binId: String,
        jsonData: Data,
        ifMatchEtag: String? = nil
    ) async throws(CloudSyncError) {
        let id = binId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !id.isEmpty else { throw CloudSyncError.invalidBinId }
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
            let id = try await FirebaseCloudStore.createBin(payload: payload)
            if let rev = try await FirebaseCloudStore.readMetaRev(id: id) { saveEtag(rev, binId: id) }
            return id
        } catch {
            throw CloudSyncError.networkError(message: error.localizedDescription)
        }
    }
    
    public func downloadBackup(
        binId: String
    ) async throws(CloudSyncError) -> Data {
        let id = binId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !id.isEmpty else { throw CloudSyncError.invalidBinId }
        do {
            let payload = (try await FirebaseCloudStore.readPayload(id: id) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            if payload.isEmpty { throw CloudSyncError.invalidResponse }
            if let rev = try await FirebaseCloudStore.readMetaRev(id: id) { saveEtag(rev, binId: id) }
            return try decodePayloadFromCloud(payload)
        } catch let error as CloudSyncError {
            throw error
        } catch {
            throw CloudSyncError.networkError(message: error.localizedDescription)
        }
    }

    public func downloadBackupIfChanged(
        binId: String
    ) async throws(CloudSyncError) -> Data? {
        let id = binId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !id.isEmpty else { throw CloudSyncError.invalidBinId }
        do {
            let known = storedEtag(binId: id)
            let rev = try await FirebaseCloudStore.readMetaRev(id: id)
            if let rev, rev == known { return nil }
            let payload = (try await FirebaseCloudStore.readPayload(id: id) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            if payload.isEmpty { throw CloudSyncError.invalidResponse }
            if let rev { saveEtag(rev, binId: id) }
            return try decodePayloadFromCloud(payload)
        } catch let error as CloudSyncError {
            throw error
        } catch {
            throw CloudSyncError.networkError(message: error.localizedDescription)
        }
    }
    
    public func deleteBackup(
        binId: String
    ) async throws(CloudSyncError) {
        let id = binId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !id.isEmpty else { throw CloudSyncError.invalidBinId }
        do {
            try await FirebaseCloudStore.delete(id: id)
        } catch {
            throw CloudSyncError.networkError(message: error.localizedDescription)
        }
    }

    private var apiKey: String {
        Bundle.main.object(forInfoDictionaryKey: "JSONBIN_API_KEY") as? String ?? ""
    }
    
    private func requireApiKey() throws(CloudSyncError) -> String {
        let trimmed = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            print("CloudSync disabled: JSONBIN_API_KEY is missing/empty in Info.plist")
            throw .missingAccessKey
        }
        return trimmed
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
            let prepared = CloudSyncPayloadCodec.compressIfUseful(data)
            return try CloudSyncCrypto.encryptIfEnabled(prepared)
        } catch {
            throw CloudSyncError.cryptoError(message: error.localizedDescription)
        }
    }
    
    private func decryptPayloadIfNeeded(_ data: Data) throws(CloudSyncError) -> Data {
        do {
            let decrypted = try CloudSyncCrypto.decryptIfNeeded(data)
            return try CloudSyncPayloadCodec.decompressIfNeeded(decrypted)
        } catch let error as CloudSyncPayloadCodecError {
            throw CloudSyncError.payloadCodec(error)
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
        let key = "cloudSyncEtag_\(binId)"
        let raw = UserDefaults.standard.string(forKey: key) ?? ""
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
    
    private func saveEtag(_ etag: String, binId: String) {
        let trimmed = etag.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        UserDefaults.standard.set(trimmed, forKey: "cloudSyncEtag_\(binId)")
    }
}

@MainActor
enum CloudSyncAutoSync {
    private static var realtimeTask: Task<Void, Never>?
    private static let lastActivityKey = "cloudSyncLastActivityEpoch"
    private static let lastFailureKey = "cloudSyncLastFailureEpoch"
    
    static func startRealtimeSync(
        modelContext: ModelContext,
        activeClientManager: ActiveClientManager
    ) {
        guard realtimeTask == nil else { return }
        realtimeTask = Task { @MainActor in
            await realtimeLoop(modelContext: modelContext, activeClientManager: activeClientManager)
        }
    }
    
    static func stopRealtimeSync() {
        realtimeTask?.cancel()
        realtimeTask = nil
    }
    
    private static func realtimeLoop(
        modelContext: ModelContext,
        activeClientManager: ActiveClientManager
    ) async {
        while !Task.isCancelled {
            await syncIfEnabled(modelContext: modelContext, clientId: activeClientManager.currentClientId)
            do {
                try await Task.sleep(for: pollInterval())
            } catch {
                return
            }
        }
    }
    
    static func syncIfEnabled(modelContext: ModelContext, clientId: UUID?) async {
        guard let ctx = makeSyncContext(modelContext: modelContext, clientId: clientId) else { return }
        do {
            markAttempt(ctx: ctx)
            guard let client = fetchClient(modelContext: modelContext, clientId: ctx.clientId) else { return }
            let parts = try await resolveManifestParts(manifestId: ctx.id)
            let (stackData, historyData) = try await downloadPartsIfChanged(parts: parts)
            try mergeRemotePartIfNeeded(stackData, client: client, modelContext: modelContext)
            try mergeRemotePartIfNeeded(historyData, client: client, modelContext: modelContext)
            let remoteChanged = stackData != nil || historyData != nil
            if shouldExitEarly(remoteChanged: remoteChanged, localChanged: ctx.localStackChanged || ctx.localHistoryChanged) { return }
            if ctx.localStackChanged || ctx.localHistoryChanged {
                try await uploadPartsIfNeeded(ctx: ctx, modelContext: modelContext, client: client, parts: parts)
            }
            markSuccess(ctx: ctx)
        } catch {
            markFailure(ctx: ctx, error: error)
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

    private static func mergeRemotePartIfNeeded(_ data: Data?, client: ClientProfile, modelContext: ModelContext) throws {
        guard let data else { return }
        try SupplementExportCodec.mergeBackup(data: data, client: client, context: modelContext)
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
            let stackPayload = try makeStackBackup(modelContext: modelContext, clientId: ctx.clientId)
            let historyPayload = try makeHistoryBackup(modelContext: modelContext, clientId: ctx.clientId)
            let stackEtag = UserDefaults.standard.string(forKey: "cloudSyncEtag_\(parts.stackBinId)")
            let historyEtag = UserDefaults.standard.string(forKey: "cloudSyncEtag_\(parts.historyBinId)")
            let stackManager = CloudSyncManager()
            let historyManager = CloudSyncManager()
            
            let stackTask = Task.detached(priority: .userInitiated) { () -> Result<Void, Error> in
                do {
                    try await stackManager.upsertBackup(binId: parts.stackBinId, jsonData: stackPayload, ifMatchEtag: stackEtag)
                    return .success(())
                } catch {
                    return .failure(error)
                }
            }
            let historyTask = Task.detached(priority: .userInitiated) { () -> Result<Void, Error> in
                do {
                    try await historyManager.upsertBackup(binId: parts.historyBinId, jsonData: historyPayload, ifMatchEtag: historyEtag)
                    return .success(())
                } catch {
                    return .failure(error)
                }
            }
            
            let stackResult = await stackTask.value
            let historyResult = await historyTask.value
            
            func isConflict(_ error: Error) -> Bool {
                guard let sync = error as? CloudSyncError else { return false }
                if case let .serverError(statusCode, _) = sync {
                    return statusCode == 412 || statusCode == 409
                }
                return false
            }
            
            func handleConflict(partId: String, retryPayload: () throws -> Data) async throws {
                let latest = try await CloudSyncManager.shared.downloadBackup(binId: partId)
                try mergeRemotePartIfNeeded(latest, client: client, modelContext: modelContext)
                try await upsertPart(binId: partId, payload: retryPayload())
            }
            
            switch stackResult {
            case .success:
                break
            case .failure(let error):
                if isConflict(error) {
                    try await handleConflict(partId: parts.stackBinId) {
                        try makeStackBackup(modelContext: modelContext, clientId: ctx.clientId)
                    }
                } else {
                    throw error
                }
            }
            
            switch historyResult {
            case .success:
                break
            case .failure(let error):
                if isConflict(error) {
                    try await handleConflict(partId: parts.historyBinId) {
                        try makeHistoryBackup(modelContext: modelContext, clientId: ctx.clientId)
                    }
                } else {
                    throw error
                }
            }
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
    
    private static func downloadPartsIfChanged(parts: CloudSyncManifest) async throws -> (Data?, Data?) {
        async let stackTask = CloudSyncManager.shared.downloadBackupIfChanged(binId: parts.stackBinId)
        async let historyTask = CloudSyncManager.shared.downloadBackupIfChanged(binId: parts.historyBinId)
        return try await (stackTask, historyTask)
    }
    
    private static func resolveManifestParts(manifestId: String) async throws -> CloudSyncManifest {
        let stackKey = "cloudSyncStackBinId_\(manifestId)"
        let historyKey = "cloudSyncHistoryBinId_\(manifestId)"
        let stack = (UserDefaults.standard.string(forKey: stackKey) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let history = (UserDefaults.standard.string(forKey: historyKey) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if !stack.isEmpty, !history.isEmpty { return CloudSyncManifest(v: 1, stackBinId: stack, historyBinId: history) }
        let data = try await CloudSyncManager.shared.downloadBackup(binId: manifestId)
        let decoded: CloudSyncManifest
        do {
            decoded = try CloudSyncManifestCodec.decode(data)
        } catch let error as CloudSyncManifestCodecError {
            throw CloudSyncError.manifestCodec(error)
        }
        UserDefaults.standard.set(decoded.stackBinId, forKey: stackKey)
        UserDefaults.standard.set(decoded.historyBinId, forKey: historyKey)
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
            let latest = try await CloudSyncManager.shared.downloadBackup(binId: binId)
            try SupplementExportCodec.mergeBackup(data: latest, client: client, context: modelContext)
            try await upsertPart(binId: binId, payload: retryPayload())
        }
    }
    
    private static func upsertPart(binId: String, payload: Data) async throws {
        let etag = UserDefaults.standard.string(forKey: "cloudSyncEtag_\(binId)")
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
        markActivity()
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
        print("☁️ Auto-Sync: Failed – \(message)")
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
    
    private static func hasLocalChangesSince(modelContext: ModelContext, clientId: UUID, lastSyncEpochMs: Int64) -> Bool {
        guard lastSyncEpochMs > 0 else { return true }
        do {
            var supplementsDescriptor = FetchDescriptor<UserSupplement>(
                predicate: #Predicate {
                    $0.updatedAtEpochMs > lastSyncEpochMs ||
                        ($0.deletedAtEpochMs != nil && $0.deletedAtEpochMs! > lastSyncEpochMs)
                }
            )
            supplementsDescriptor.fetchLimit = 50
            let changedSupplements = try modelContext.fetch(supplementsDescriptor)
            if changedSupplements.contains(where: { $0.client?.id == clientId }) { return true }
            
            var recordsDescriptor = FetchDescriptor<IntakeRecord>(
                predicate: #Predicate { $0.updatedAtEpochMs > lastSyncEpochMs },
                sortBy: [SortDescriptor(\IntakeRecord.updatedAtEpochMs, order: .reverse)]
            )
            recordsDescriptor.fetchLimit = 100
            let changedRecords = try modelContext.fetch(recordsDescriptor)
            return changedRecords.contains(where: { $0.supplement?.client?.id == clientId })
        } catch {
            return true
        }
    }
    
    private static func markActivity() {
        UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: lastActivityKey)
    }
    
    private static func pollInterval() -> Duration {
        let now = Date().timeIntervalSince1970
        
        let lastFailure = UserDefaults.standard.double(forKey: lastFailureKey)
        if lastFailure > 0 {
            let elapsed = now - lastFailure
            if elapsed < 30 { return .seconds(15) }
            if elapsed < 2 * 60 { return .seconds(30) }
            return .seconds(120)
        }
        
        let lastActivity = UserDefaults.standard.double(forKey: lastActivityKey)
        let activityElapsed = lastActivity > 0 ? (now - lastActivity) : 10_000
        
        if activityElapsed < 20 { return .seconds(5) }
        if activityElapsed < 2 * 60 { return .seconds(30) }
        if activityElapsed < 10 * 60 { return .seconds(120) }
        return .seconds(300)
    }
    
    private static func activeBinId() -> String? {
        let hosted = UserDefaults.standard.string(forKey: "cloudSyncHostedBinId") ?? ""
        let linked = UserDefaults.standard.string(forKey: "cloudSyncLinkedBinId") ?? ""
        let trimmed = (hosted.isEmpty ? linked : hosted).trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
    
    private static func makeStackBackup(modelContext: ModelContext, clientId: UUID) throws -> Data {
        let supplements = try modelContext.fetch(FetchDescriptor<UserSupplement>()).filter { $0.client?.id == clientId }
        return try SupplementExportCodec.encodeBackup(supplements: supplements, records: [])
    }
    
    private static func makeHistoryBackup(modelContext: ModelContext, clientId: UUID) throws -> Data {
        let cutoff = Calendar.current.date(byAdding: .day, value: -90, to: .now) ?? .now
        let records = try modelContext.fetch(FetchDescriptor<IntakeRecord>())
            .filter { $0.supplement?.client?.id == clientId && $0.date >= cutoff }
            .sorted { $0.date > $1.date }
            .prefix(5_000)
        return try SupplementExportCodec.encodeBackup(supplements: [], records: Array(records))
    }
}
