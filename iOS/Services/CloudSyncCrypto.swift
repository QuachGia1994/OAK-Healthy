import CryptoKit
import Foundation
import Security

public enum CloudSyncCryptoError: Error, Sendable, LocalizedError {
    case invalidKeyFormat
    case missingKey(keyId: String)
    case cryptoFailed(message: String)
    case invalidPayload
    case unencryptedPayloadRejected

    public var errorDescription: String? {
        switch self {
        case .invalidKeyFormat: return "The encryption key format is invalid."
        case .missingKey(let keyId): return "Missing cloud sync key: \(keyId)"
        case .cryptoFailed(let message): return "Cloud encryption failed: \(message)"
        case .invalidPayload: return "The encrypted cloud payload is invalid."
        case .unencryptedPayloadRejected: return "Unencrypted cloud data was rejected because encryption is enabled."
        }
    }
}

public enum CloudSyncKeyManager {
    private static let service = "com.oakhealthy.cloudsync"
    private static let enabledKey = "cloudSyncEncryptionEnabled"
    private static let currentKeyIdKey = "cloudSyncEncCurrentKeyId"
    private static let previousKeyIdKey = "cloudSyncEncPreviousKeyId"
    nonisolated(unsafe) private static let validKeyIdPattern = /^[A-Za-z0-9_-]{1,64}$/

    static func isValidKeyId(_ keyId: String) -> Bool {
        keyId.firstMatch(of: validKeyIdPattern) != nil
    }
    
    public static func isEncryptionEnabled() -> Bool {
        UserDefaults.standard.bool(forKey: enabledKey)
    }
    
    public static func setEncryptionEnabled(_ enabled: Bool) throws(CloudSyncCryptoError) {
        let defaults = UserDefaults.standard
        let previous = defaults.bool(forKey: enabledKey)
        defaults.set(enabled, forKey: enabledKey)
        guard enabled else { return }
        do {
            _ = try ensureKeyExists()
        } catch {
            defaults.set(previous, forKey: enabledKey)
            throw error
        }
    }
    
    public static func exportCurrentKey() throws(CloudSyncCryptoError) -> String? {
        let keyId = (UserDefaults.standard.string(forKey: currentKeyIdKey) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard isValidKeyId(keyId) else { return nil }
        guard let keyData = try readKeyData(keyId: keyId) else { return nil }
        let b64 = keyData.base64EncodedString()
        return "\(keyId):\(b64)"
    }
    
    public static func importKey(exported: String) throws(CloudSyncCryptoError) -> String {
        let raw = exported.trimmingCharacters(in: .whitespacesAndNewlines)
        let parts = raw.split(separator: ":", maxSplits: 1).map { String($0) }
        guard parts.count == 2 else { throw .invalidKeyFormat }
        let keyId = parts[0].trimmingCharacters(in: .whitespacesAndNewlines)
        let b64 = parts[1].trimmingCharacters(in: .whitespacesAndNewlines)
        guard isValidKeyId(keyId), let data = Data(base64Encoded: b64), data.count == 32 else { throw .invalidKeyFormat }
        let current = (UserDefaults.standard.string(forKey: currentKeyIdKey) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if !current.isEmpty, current != keyId { UserDefaults.standard.set(current, forKey: previousKeyIdKey) }
        try writeKeyData(data, keyId: keyId)
        UserDefaults.standard.set(keyId, forKey: currentKeyIdKey)
        return keyId
    }
    
    private static func rotateKey() throws(CloudSyncCryptoError) -> String {
        let old = (UserDefaults.standard.string(forKey: currentKeyIdKey) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let keyId = UUID().uuidString
        var bytes = [UInt8](repeating: 0, count: 32)
        let status = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        guard status == errSecSuccess else { throw .cryptoFailed(message: "Random failed: \(status)") }
        let keyData = Data(bytes)
        try writeKeyData(keyData, keyId: keyId)
        if !old.isEmpty { UserDefaults.standard.set(old, forKey: previousKeyIdKey) }
        UserDefaults.standard.set(keyId, forKey: currentKeyIdKey)
        return keyId
    }
    
    public static func ensureKeyExists() throws(CloudSyncCryptoError) -> String {
        let keyId = (UserDefaults.standard.string(forKey: currentKeyIdKey) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if isValidKeyId(keyId), try readKeyData(keyId: keyId) != nil { return keyId }
        return try rotateKey()
    }

    public static func clearLocalKeyMaterial() throws(CloudSyncCryptoError) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service
        ]
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw .cryptoFailed(message: "Keychain delete failed: \(status)")
        }
    }
    
    public static func keyData(for keyId: String) throws(CloudSyncCryptoError) -> Data? {
        guard isValidKeyId(keyId) else { return nil }
        return try readKeyData(keyId: keyId)
    }
    
    private static func readKeyData(keyId: String) throws(CloudSyncCryptoError) -> Data? {
        let account = "cloudSyncEncKey_\(keyId)"
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess else { throw .cryptoFailed(message: "Keychain read failed: \(status)") }
        guard let data = item as? Data else { throw .cryptoFailed(message: "Keychain returned invalid data") }
        return data
    }
    
    private static func writeKeyData(_ data: Data, keyId: String) throws(CloudSyncCryptoError) {
        let account = "cloudSyncEncKey_\(keyId)"
        let add: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        ]
        let status = SecItemAdd(add as CFDictionary, nil)
        if status == errSecSuccess { return }
        if status != errSecDuplicateItem { throw .cryptoFailed(message: "Keychain add failed: \(status)") }
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        let update: [String: Any] = [kSecValueData as String: data]
        let updateStatus = SecItemUpdate(query as CFDictionary, update as CFDictionary)
        guard updateStatus == errSecSuccess else { throw .cryptoFailed(message: "Keychain update failed: \(updateStatus)") }
    }
    
}

public enum CloudSyncCrypto {
    private struct EncryptedPayload: Codable {
        struct Enc: Codable {
            let v: Int
            let alg: String
            let kid: String
            let nonce: String
            let ct: String
        }
        
        let enc: Enc
    }
    
    public static func encryptIfEnabled(_ plaintext: Data) throws(CloudSyncCryptoError) -> Data {
        guard CloudSyncKeyManager.isEncryptionEnabled() else { return plaintext }
        let keyId = try CloudSyncKeyManager.ensureKeyExists()
        guard let keyData = try CloudSyncKeyManager.keyData(for: keyId) else { throw .missingKey(keyId: keyId) }
        let nonce = AES.GCM.Nonce()
        let nonceData = nonce.withUnsafeBytes { Data($0) }
        let ctData = try seal(plaintext: plaintext, keyData: keyData, nonce: nonce)
        return try encodeEncryptedPayload(keyId: keyId, nonceData: nonceData, ctData: ctData)
    }
    
    public static func decryptIfNeeded(_ payload: Data) throws(CloudSyncCryptoError) -> Data {
        let parsed = try parseEncryptedPayloadIfPresent(payload)
        let localUsesEncryption = CloudSyncKeyManager.isEncryptionEnabled()
        try validateEncryptionMode(localUsesEncryption: localUsesEncryption, cloudUsesEncryption: parsed != nil)
        guard let parsed else { return payload }
        let plaintext = try openEncryptedPayload(kid: parsed.kid, nonceData: parsed.nonceData, ctData: parsed.ctData)
        if !localUsesEncryption {
            try CloudSyncKeyManager.setEncryptionEnabled(true)
        }
        return plaintext
    }

    static func validateEncryptionMode(
        localUsesEncryption: Bool,
        cloudUsesEncryption: Bool
    ) throws(CloudSyncCryptoError) {
        if localUsesEncryption && !cloudUsesEncryption { throw .unencryptedPayloadRejected }
    }
    
    private static func seal(
        plaintext: Data,
        keyData: Data,
        nonce: AES.GCM.Nonce
    ) throws(CloudSyncCryptoError) -> Data {
        let key = SymmetricKey(data: keyData)
        let sealed: AES.GCM.SealedBox
        do {
            sealed = try AES.GCM.seal(plaintext, using: key, nonce: nonce)
        } catch {
            throw .cryptoFailed(message: String(describing: error))
        }
        var ctData = Data()
        ctData.append(sealed.ciphertext)
        ctData.append(sealed.tag)
        return ctData
    }
    
    private static func encodeEncryptedPayload(
        keyId: String,
        nonceData: Data,
        ctData: Data
    ) throws(CloudSyncCryptoError) -> Data {
        let payload = EncryptedPayload(
            enc: .init(
                v: 1,
                alg: "A256GCM",
                kid: keyId,
                nonce: nonceData.base64EncodedString(),
                ct: ctData.base64EncodedString()
            )
        )
        do {
            return try JSONEncoder().encode(payload)
        } catch {
            throw .cryptoFailed(message: String(describing: error))
        }
    }
    
    private static func parseEncryptedPayloadIfPresent(
        _ payload: Data
    ) throws(CloudSyncCryptoError) -> (kid: String, nonceData: Data, ctData: Data)? {
        guard let object = (try? JSONSerialization.jsonObject(with: payload)) as? [String: Any] else { return nil }
        guard object["enc"] != nil else { return nil }
        guard let decoded = try? JSONDecoder().decode(EncryptedPayload.self, from: payload) else { throw .invalidPayload }
        guard decoded.enc.v == 1, decoded.enc.alg == "A256GCM" else { throw .invalidPayload }
        let kid = decoded.enc.kid.trimmingCharacters(in: .whitespacesAndNewlines)
        guard CloudSyncKeyManager.isValidKeyId(kid) else { throw .invalidPayload }
        guard let nonceData = Data(base64Encoded: decoded.enc.nonce) else { throw .invalidPayload }
        guard let ctData = Data(base64Encoded: decoded.enc.ct) else { throw .invalidPayload }
        guard nonceData.count == 12, ctData.count >= 16 else { throw .invalidPayload }
        return (kid, nonceData, ctData)
    }
    
    private static func openEncryptedPayload(
        kid: String,
        nonceData: Data,
        ctData: Data
    ) throws(CloudSyncCryptoError) -> Data {
        guard let keyData = try CloudSyncKeyManager.keyData(for: kid) else { throw .missingKey(keyId: kid) }
        do {
            return try openAESGCM(keyData: keyData, nonceData: nonceData, ctData: ctData)
        } catch let firstError as CloudSyncCryptoError {
            guard ctData.count >= 28, ctData.starts(with: nonceData) else { throw firstError }
            return try openAESGCM(keyData: keyData, nonceData: nonceData, ctData: Data(ctData.dropFirst(12)))
        }
    }

    private static func openAESGCM(
        keyData: Data,
        nonceData: Data,
        ctData: Data
    ) throws(CloudSyncCryptoError) -> Data {
        let key = SymmetricKey(data: keyData)
        let nonce: AES.GCM.Nonce
        do {
            nonce = try AES.GCM.Nonce(data: nonceData)
        } catch {
            throw .cryptoFailed(message: String(describing: error))
        }
        let tag = Data(ctData.suffix(16))
        let ciphertext = ctData.dropLast(16)
        let box: AES.GCM.SealedBox
        do {
            box = try AES.GCM.SealedBox(nonce: nonce, ciphertext: Data(ciphertext), tag: tag)
        } catch {
            throw .cryptoFailed(message: String(describing: error))
        }
        do {
            return try AES.GCM.open(box, using: key)
        } catch {
            throw .cryptoFailed(message: String(describing: error))
        }
    }
}
