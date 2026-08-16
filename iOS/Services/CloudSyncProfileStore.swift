import Foundation

struct CloudSyncProfileLinks: Equatable {
    let hostedBinId: String?
    let linkedBinId: String?

    var activeManifestId: String? {
        hostedBinId ?? linkedBinId
    }
}

struct CloudSyncProfileStore {
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func links(clientId: UUID?) -> CloudSyncProfileLinks {
        guard let clientId else { return CloudSyncProfileLinks(hostedBinId: nil, linkedBinId: nil) }
        migrateLegacyLinks(clientId: clientId)
        return CloudSyncProfileLinks(
            hostedBinId: readScoped(prefix: Self.hostedPrefix, clientId: clientId),
            linkedBinId: readScoped(prefix: Self.linkedPrefix, clientId: clientId)
        )
    }

    func activeManifestId(clientId: UUID?) -> String? {
        links(clientId: clientId).activeManifestId
    }

    func setHostedBinId(_ binId: String?, clientId: UUID) {
        writeScoped(prefix: Self.hostedPrefix, clientId: clientId, binId: binId)
    }

    func setLinkedBinId(_ binId: String?, clientId: UUID) {
        writeScoped(prefix: Self.linkedPrefix, clientId: clientId, binId: binId)
    }

    func clearLinks(clientId: UUID) {
        defaults.removeObject(forKey: scopedKey(prefix: Self.hostedPrefix, clientId: clientId))
        defaults.removeObject(forKey: scopedKey(prefix: Self.linkedPrefix, clientId: clientId))
    }

    private func migrateLegacyLinks(clientId: UUID) {
        migrateLegacyValue(
            legacyKey: Self.legacyHostedKey,
            prefix: Self.hostedPrefix,
            clientId: clientId
        )
        migrateLegacyValue(
            legacyKey: Self.legacyLinkedKey,
            prefix: Self.linkedPrefix,
            clientId: clientId
        )
    }

    private func migrateLegacyValue(legacyKey: String, prefix: String, clientId: UUID) {
        let key = scopedKey(prefix: prefix, clientId: clientId)
        let scopedValue = normalize(defaults.string(forKey: key))
        let legacyValue = normalize(defaults.string(forKey: legacyKey))
        if scopedValue == nil, let legacyValue {
            defaults.set(legacyValue, forKey: key)
        }
        defaults.removeObject(forKey: legacyKey)
    }

    private func readScoped(prefix: String, clientId: UUID) -> String? {
        normalize(defaults.string(forKey: scopedKey(prefix: prefix, clientId: clientId)))
    }

    private func writeScoped(prefix: String, clientId: UUID, binId: String?) {
        let key = scopedKey(prefix: prefix, clientId: clientId)
        guard let value = normalize(binId) else {
            defaults.removeObject(forKey: key)
            return
        }
        defaults.set(value, forKey: key)
    }

    private func scopedKey(prefix: String, clientId: UUID) -> String {
        prefix + clientId.uuidString.lowercased()
    }

    private func normalize(_ value: String?) -> String? {
        value?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
    }

    private static let hostedPrefix = "cloudSyncHostedBinId_client_"
    private static let linkedPrefix = "cloudSyncLinkedBinId_client_"
    private static let legacyHostedKey = "cloudSyncHostedBinId"
    private static let legacyLinkedKey = "cloudSyncLinkedBinId"
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
