import Foundation
import SwiftData

public enum CloudSyncError: Error, Sendable, LocalizedError {
    case invalidBinId
    case invalidResponse
    case missingAccessKey
    case serverError(statusCode: Int, body: String)
    case networkError(message: String)
    case decodingError(message: String)

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
                try? await Task.sleep(for: .seconds(15 * 60))
            }
        }
    }
    
    public func stopAutoSync() {
        autoSyncTask?.cancel()
        autoSyncTask = nil
    }
    
    public func upsertBackup(
        binId: String,
        jsonData: Data
    ) async throws(CloudSyncError) {
        let apiKey = try requireApiKey()
        let id = binId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !id.isEmpty else { throw CloudSyncError.invalidBinId }
        
        let url = Self.baseURL.appendingPathComponent(id)
        var request = URLRequest(url: url)
        request.httpMethod = "PUT"
        request.httpBody = jsonData
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(apiKey, forHTTPHeaderField: "X-Master-Key")
        
        let (data, http) = try await fetch(request: request)
        guard (200...299).contains(http.statusCode) else {
            let body = String(data: data, encoding: .utf8) ?? ""
            throw CloudSyncError.serverError(statusCode: http.statusCode, body: body)
        }
    }
    
    public func uploadBackup(
        jsonData: Data
    ) async throws(CloudSyncError) -> String {
        let apiKey = try requireApiKey()
        
        var request = URLRequest(url: Self.baseURL)
        request.httpMethod = "POST"
        request.httpBody = jsonData
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
        
        let obj = try decodeJSONObject(data) as? [String: Any]
        let record = obj?["record"]
        guard let record else { throw CloudSyncError.invalidResponse }
        return try encodeJSONObject(record)
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
}

@MainActor
enum CloudSyncAutoSync {
    private static var realtimeTask: Task<Void, Never>?
    
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
            await downloadAndMergeIfEnabled(
                modelContext: modelContext,
                clientId: activeClientManager.currentClientId
            )
            try? await Task.sleep(for: .seconds(10))
        }
    }
    
    static func uploadIfEnabled(modelContext: ModelContext, clientId: UUID?) async {
        guard UserDefaults.standard.bool(forKey: "isAutoSyncEnabled") else { return }
        guard let binId = activeBinId() else {
            print("☁️ Auto-Sync Upload: Skipped – no binId")
            return
        }
        guard let clientId else { return }
        print("☁️ Auto-Sync: Uploading to bin \(binId)...")
        let backup = try? makeBackup(modelContext: modelContext, clientId: clientId)
        guard let backup else { return }
        try? await CloudSyncManager.shared.upsertBackup(binId: binId, jsonData: backup)
        print("☁️ Auto-Sync: Upload completed")
    }
    
    static func downloadAndMergeIfEnabled(modelContext: ModelContext, clientId: UUID?) async {
        guard UserDefaults.standard.bool(forKey: "isAutoSyncEnabled") else {
            print("☁️ Auto-Sync: Skipped – auto sync disabled")
            return
        }
        guard let binId = activeBinId() else {
            print("☁️ Auto-Sync: Skipped – no binId configured")
            return
        }
        guard let clientId else {
            print("☁️ Auto-Sync: Skipped – no active client")
            return
        }
        print("☁️ Auto-Sync: Downloading from bin \(binId)...")
        guard let data = try? await CloudSyncManager.shared.downloadBackup(binId: binId) else {
            print("☁️ Auto-Sync: Download failed")
            return
        }
        let client = (try? modelContext.fetch(FetchDescriptor<ClientProfile>()))?.first { $0.id == clientId }
        guard let client else {
            print("☁️ Auto-Sync: Client not found in local DB")
            return
        }
        try? SupplementExportCodec.importBackup(data: data, client: client, context: modelContext)
        print("☁️ Auto-Sync: Download & merge completed")
    }
    
    private static func activeBinId() -> String? {
        let hosted = UserDefaults.standard.string(forKey: "cloudSyncHostedBinId") ?? ""
        let linked = UserDefaults.standard.string(forKey: "cloudSyncLinkedBinId") ?? ""
        let trimmed = (hosted.isEmpty ? linked : hosted).trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
    
    private static func makeBackup(modelContext: ModelContext, clientId: UUID) throws -> Data {
        let supplements = try modelContext.fetch(FetchDescriptor<UserSupplement>()).filter { $0.client?.id == clientId }
        let records = try modelContext.fetch(FetchDescriptor<IntakeRecord>()).filter { $0.supplement?.client?.id == clientId }
        return try SupplementExportCodec.encodeBackup(supplements: supplements, records: records)
    }
}
