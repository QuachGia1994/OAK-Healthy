import Foundation

public enum CloudSyncError: Error, Sendable {
    case invalidBinId
    case invalidResponse
    case missingAccessKey
    case serverError(statusCode: Int, body: String)
}

public actor CloudSyncManager {
    public static let baseURL = URL(string: "https://api.jsonbin.io/v3/b")!
    public static let placeholderAccessKey = "<YOUR_JSONBIN_ACCESS_KEY>"
    
    public init() {}
    
    public func uploadBackup(
        jsonData: Data,
        accessKey: String
    ) async throws(CloudSyncError) -> String {
        let key = accessKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty, key != Self.placeholderAccessKey else { throw CloudSyncError.missingAccessKey }
        
        var request = URLRequest(url: Self.baseURL)
        request.httpMethod = "POST"
        request.httpBody = jsonData
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(key, forHTTPHeaderField: "X-Access-Key")
        
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw CloudSyncError.invalidResponse }
        guard (200...299).contains(http.statusCode) else {
            let body = String(data: data, encoding: .utf8) ?? ""
            throw CloudSyncError.serverError(statusCode: http.statusCode, body: body)
        }
        
        let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        let metadata = obj?["metadata"] as? [String: Any]
        let id = metadata?["id"] as? String
        guard let id, !id.isEmpty else { throw CloudSyncError.invalidResponse }
        return id
    }
    
    public func downloadBackup(
        binId: String,
        accessKey: String
    ) async throws(CloudSyncError) -> Data {
        let key = accessKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty, key != Self.placeholderAccessKey else { throw CloudSyncError.missingAccessKey }
        
        let id = binId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !id.isEmpty else { throw CloudSyncError.invalidBinId }
        
        let url = Self.baseURL.appendingPathComponent(id).appendingPathComponent("latest")
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(key, forHTTPHeaderField: "X-Access-Key")
        
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw CloudSyncError.invalidResponse }
        guard (200...299).contains(http.statusCode) else {
            let body = String(data: data, encoding: .utf8) ?? ""
            throw CloudSyncError.serverError(statusCode: http.statusCode, body: body)
        }
        
        let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        let record = obj?["record"]
        guard let record else { throw CloudSyncError.invalidResponse }
        return try JSONSerialization.data(withJSONObject: record, options: [])
    }
}
