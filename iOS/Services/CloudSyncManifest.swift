import Foundation

struct CloudSyncManifest: Codable, Sendable {
    var v: Int
    var stackBinId: String
    var historyBinId: String
}

enum CloudSyncManifestCodec {
    static func encode(stackBinId: String, historyBinId: String) -> Data? {
        let manifest = CloudSyncManifest(v: 1, stackBinId: stackBinId, historyBinId: historyBinId)
        return try? JSONEncoder().encode(manifest)
    }
    
    static func decode(_ data: Data) -> CloudSyncManifest? {
        try? JSONDecoder().decode(CloudSyncManifest.self, from: data)
    }
}

