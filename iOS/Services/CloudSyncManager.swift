import Foundation

public enum CloudSyncError: Error, Sendable, LocalizedError {
    case invalidBinId
    case invalidResponse
    case serverError(statusCode: Int, body: String)
    case networkError(message: String)
    case cryptoError(message: String)
    case payloadCodec(CloudSyncPayloadCodecError)
    case manifestCodec(CloudSyncManifestCodecError)

    public var errorDescription: String? {
        switch self {
        case .invalidBinId:
            return "Mã liên kết không hợp lệ."
        case .invalidResponse:
            return "Phản hồi máy chủ không hợp lệ."
        case .serverError(let statusCode, let body):
            if statusCode == 522 {
                return "Máy chủ phản hồi quá lâu (522). Vui lòng thử lại sau."
            }
            let trimmed = body.trimmingCharacters(in: .whitespacesAndNewlines)
            let message = trimmed.isEmpty ? "Unknown server error" : String(trimmed.prefix(240))
            return "Lỗi máy chủ (\(statusCode)): \(message)"
        case .networkError(let message):
            return "Lỗi mạng: \(message)"
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
    public static let shared = CloudSyncManager()

    public init() {}

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
        let decrypted = try decryptPayloadIfNeeded(data)
        do {
            return try CloudSyncPayloadCodec.decompressIfNeeded(decrypted)
        } catch let error as CloudSyncPayloadCodecError {
            throw CloudSyncError.payloadCodec(error)
        } catch {
            throw CloudSyncError.invalidResponse
        }
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
