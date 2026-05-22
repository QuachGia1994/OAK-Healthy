import Foundation

struct CloudSyncManifest: Codable, Sendable {
    var v: Int
    var stackBinId: String
    var historyBinId: String
}

enum CloudSyncManifestCodecError: Error, Sendable {
    case encodeFailed
    case decodeFailed
}

enum CloudSyncManifestCodec {
    static func encode(stackBinId: String, historyBinId: String) throws(CloudSyncManifestCodecError) -> Data {
        let manifest = CloudSyncManifest(v: 1, stackBinId: stackBinId, historyBinId: historyBinId)
        do {
            return try JSONEncoder().encode(manifest)
        } catch {
            throw .encodeFailed
        }
    }
    
    static func decode(_ data: Data) throws(CloudSyncManifestCodecError) -> CloudSyncManifest {
        do {
            return try JSONDecoder().decode(CloudSyncManifest.self, from: data)
        } catch {
            throw .decodeFailed
        }
    }
}
