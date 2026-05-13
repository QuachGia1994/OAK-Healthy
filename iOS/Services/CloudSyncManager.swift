import Foundation

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
    
    public init() {}
    
    public func uploadBackup(
        jsonData: Data
    ) async throws(CloudSyncError) -> String {
        let apiKey = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !apiKey.isEmpty else { throw .missingAccessKey }
        
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
        let apiKey = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !apiKey.isEmpty else { throw .missingAccessKey }
        
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
        let apiKey = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !apiKey.isEmpty else { throw .missingAccessKey }
        
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
