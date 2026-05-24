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
            let trimmed = body.trimmingCharacters(in: .whitespacesAndNewlines)
            return "Lỗi máy chủ (\(statusCode)): \(trimmed.isEmpty ? "Unknown server error" : trimmed)"
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
    
    public init() {}
    
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
        let apiKey = try requireApiKey()
        let id = binId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !id.isEmpty else { throw CloudSyncError.invalidBinId }
        
        let url = Self.baseURL.appendingPathComponent(id)
        var request = URLRequest(url: url)
        request.httpMethod = "PUT"
        request.httpBody = try encryptPayloadIfNeeded(jsonData)
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(apiKey, forHTTPHeaderField: "X-Master-Key")
        if let ifMatchEtag {
            let trimmed = ifMatchEtag.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty { request.setValue(trimmed, forHTTPHeaderField: "If-Match") }
        }
        
        let (data, http) = try await fetch(request: request)
        guard (200...299).contains(http.statusCode) else {
            let body = String(data: data, encoding: .utf8) ?? ""
            throw CloudSyncError.serverError(statusCode: http.statusCode, body: body)
        }
        if let etag = responseEtag(http: http) { saveEtag(etag, binId: id) }
    }
    
    public func uploadBackup(
        jsonData: Data
    ) async throws(CloudSyncError) -> String {
        let apiKey = try requireApiKey()
        
        var request = URLRequest(url: Self.baseURL)
        request.httpMethod = "POST"
        request.httpBody = try encryptPayloadIfNeeded(jsonData)
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(apiKey, forHTTPHeaderField: "X-Master-Key")
        
        let (data, http) = try await fetch(request: request)
        guard (200...299).contains(http.statusCode) else {
            let body = String(data: data, encoding: .utf8) ?? ""
            throw CloudSyncError.serverError(statusCode: http.statusCode, body: body)
        }
        
        let obj = try decodeJSONObject(data) as? [String: Any]
        let metadata = obj?["metadata"] as? [String: Any]
        let id = metadata?["id"] as? String
        guard let id, !id.isEmpty else { throw CloudSyncError.invalidResponse }
        if let etag = responseEtag(http: http) { saveEtag(etag, binId: id) }
        return id
    }
    
    public func downloadBackup(
        binId: String
    ) async throws(CloudSyncError) -> Data {
        let apiKey = try requireApiKey()
        
        let id = binId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !id.isEmpty else { throw CloudSyncError.invalidBinId }
        
        let url = Self.baseURL.appendingPathComponent(id).appendingPathComponent("latest")
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(apiKey, forHTTPHeaderField: "X-Master-Key")
        
        let (data, http) = try await fetch(request: request)
        guard (200...299).contains(http.statusCode) else {
            let body = String(data: data, encoding: .utf8) ?? ""
            throw CloudSyncError.serverError(statusCode: http.statusCode, body: body)
        }
        if let etag = responseEtag(http: http) { saveEtag(etag, binId: id) }
        
        let record = try recordData(from: data)
        return try decryptPayloadIfNeeded(record)
    }

    public func downloadBackupIfChanged(
        binId: String
    ) async throws(CloudSyncError) -> Data? {
        let apiKey = try requireApiKey()
        let id = binId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !id.isEmpty else { throw CloudSyncError.invalidBinId }
        
        let url = Self.baseURL.appendingPathComponent(id).appendingPathComponent("latest")
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(apiKey, forHTTPHeaderField: "X-Master-Key")
        if let etag = storedEtag(binId: id) { request.setValue(etag, forHTTPHeaderField: "If-None-Match") }
        
        let (data, http) = try await fetch(request: request)
        if http.statusCode == 304 { return nil }
        guard (200...299).contains(http.statusCode) else {
            let body = String(data: data, encoding: .utf8) ?? ""
            throw CloudSyncError.serverError(statusCode: http.statusCode, body: body)
        }
        if let etag = responseEtag(http: http) { saveEtag(etag, binId: id) }
        return try decryptPayloadIfNeeded(recordData(from: data))
    }
    
    public func deleteBackup(
        binId: String
    ) async throws(CloudSyncError) {
        let apiKey = try requireApiKey()
        
        let id = binId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !id.isEmpty else { throw CloudSyncError.invalidBinId }
        
        let url = Self.baseURL.appendingPathComponent(id)
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(apiKey, forHTTPHeaderField: "X-Master-Key")
        
        let (data, http) = try await fetch(request: request)
        guard (200...299).contains(http.statusCode) else {
            let body = String(data: data, encoding: .utf8) ?? ""
            throw CloudSyncError.serverError(statusCode: http.statusCode, body: body)
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
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse else { throw CloudSyncError.invalidResponse }
            return (data, http)
        } catch let error as CloudSyncError {
            throw error
        } catch {
            throw CloudSyncError.networkError(message: error.localizedDescription)
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
            let remote = try await CloudSyncManager.shared.downloadBackupIfChanged(binId: ctx.binId)
            guard let client = fetchClient(modelContext: modelContext, clientId: ctx.clientId) else { return }
            try mergeRemoteIfNeeded(remote, client: client, modelContext: modelContext)
            if shouldExitEarly(remoteChanged: remote != nil, localChanged: ctx.localChanged) { return }
            if ctx.localChanged { try await uploadWithRetryIfConflicted(ctx: ctx, modelContext: modelContext, client: client) }
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
        let localChanged: Bool
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
        let localChanged = hasLocalChangesSince(modelContext: modelContext, clientId: clientId, lastSyncEpochMs: lastSyncEpochMs)
        return SyncContext(
            binId: binId,
            id: id,
            clientId: clientId,
            lastSyncKey: lastSyncKey,
            lastAttemptKey: lastAttemptKey,
            lastErrorKey: lastErrorKey,
            localChanged: localChanged
        )
    }

    private static func markAttempt(ctx: SyncContext) {
        UserDefaults.standard.set(Double(Date().timeIntervalSince1970 * 1000), forKey: ctx.lastAttemptKey)
    }

    private static func fetchClient(modelContext: ModelContext, clientId: UUID) -> ClientProfile? {
        (try? modelContext.fetch(FetchDescriptor<ClientProfile>()))?.first { $0.id == clientId }
    }

    private static func mergeRemoteIfNeeded(_ data: Data?, client: ClientProfile, modelContext: ModelContext) throws {
        guard let data else { return }
        try SupplementExportCodec.mergeBackup(data: data, client: client, context: modelContext)
    }

    private static func shouldExitEarly(remoteChanged: Bool, localChanged: Bool) -> Bool {
        !remoteChanged && !localChanged
    }

    private static func uploadWithRetryIfConflicted(
        ctx: SyncContext,
        modelContext: ModelContext,
        client: ClientProfile
    ) async throws {
        let etag = UserDefaults.standard.string(forKey: "cloudSyncEtag_\(ctx.id)")
        let backup = try makeBackup(modelContext: modelContext, clientId: ctx.clientId)
        do {
            try await CloudSyncManager.shared.upsertBackup(binId: ctx.id, jsonData: backup, ifMatchEtag: etag)
        } catch CloudSyncError.serverError(let statusCode, _) where statusCode == 412 || statusCode == 409 {
            let latest = try await CloudSyncManager.shared.downloadBackup(binId: ctx.id)
            try SupplementExportCodec.mergeBackup(data: latest, client: client, context: modelContext)
            let retryBackup = try makeBackup(modelContext: modelContext, clientId: ctx.clientId)
            let retryEtag = UserDefaults.standard.string(forKey: "cloudSyncEtag_\(ctx.id)")
            try await CloudSyncManager.shared.upsertBackup(binId: ctx.id, jsonData: retryBackup, ifMatchEtag: retryEtag)
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
        UserDefaults.standard.set(Double(Date().timeIntervalSince1970 * 1000), forKey: ctx.lastAttemptKey)
        UserDefaults.standard.set(error.localizedDescription, forKey: ctx.lastErrorKey)
        print("☁️ Auto-Sync: Failed – \(error.localizedDescription)")
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
        let raw = UserDefaults.standard.double(forKey: lastActivityKey)
        guard raw > 0 else { return .seconds(60) }
        let elapsed = Date().timeIntervalSince1970 - raw
        if elapsed < 30 { return .seconds(3) }
        if elapsed < 120 { return .seconds(10) }
        return .seconds(60)
    }
    
    private static func activeBinId() -> String? {
        let hosted = UserDefaults.standard.string(forKey: "cloudSyncHostedBinId") ?? ""
        let linked = UserDefaults.standard.string(forKey: "cloudSyncLinkedBinId") ?? ""
        let trimmed = (hosted.isEmpty ? linked : hosted).trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
    
    private static func makeBackup(modelContext: ModelContext, clientId: UUID) throws -> Data {
        let supplements = try modelContext.fetch(FetchDescriptor<UserSupplement>()).filter { $0.client?.id == clientId }
        let records = try modelContext.fetch(FetchDescriptor<IntakeRecord>())
            .filter { $0.supplement?.client?.id == clientId }
            .sorted { $0.date > $1.date }
            .prefix(5_000)
        return try SupplementExportCodec.encodeBackup(supplements: supplements, records: Array(records))
    }
}
