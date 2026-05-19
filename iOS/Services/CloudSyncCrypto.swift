import CryptoKit
import Foundation
import Security

public enum CloudSyncCryptoError: Error, Sendable {
    case invalidKeyFormat
    case missingKey(keyId: String)
    case cryptoFailed(message: String)
    case invalidPayload
}

public enum CloudSyncKeyManager {
    private static let service = "com.oakhealthy.cloudsync"
    private static let enabledKey = "cloudSyncEncryptionEnabled"
    private static let currentKeyIdKey = "cloudSyncEncCurrentKeyId"
    private static let previousKeyIdKey = "cloudSyncEncPreviousKeyId"
    
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
        guard !keyId.isEmpty else { return nil }
        guard let keyData = readKeyData(keyId: keyId) else { return nil }
        let b64 = keyData.base64EncodedString()
        return "\(keyId):\(b64)"
    }
    
    public static func importKey(exported: String) throws(CloudSyncCryptoError) -> String {
        let raw = exported.trimmingCharacters(in: .whitespacesAndNewlines)
        let parts = raw.split(separator: ":", maxSplits: 1).map { String($0) }
        guard parts.count == 2 else { throw .invalidKeyFormat }
        let keyId = parts[0].trimmingCharacters(in: .whitespacesAndNewlines)
        let b64 = parts[1].trimmingCharacters(in: .whitespacesAndNewlines)
        guard !keyId.isEmpty, let data = Data(base64Encoded: b64), data.count == 32 else { throw .invalidKeyFormat }
        let current = (UserDefaults.standard.string(forKey: currentKeyIdKey) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if !current.isEmpty, current != keyId { UserDefaults.standard.set(current, forKey: previousKeyIdKey) }
        try writeKeyData(data, keyId: keyId)
        UserDefaults.standard.set(keyId, forKey: currentKeyIdKey)
        return keyId
    }
    
    public static func rotateKey() throws(CloudSyncCryptoError) -> String {
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
        if !keyId.isEmpty, readKeyData(keyId: keyId) != nil { return keyId }
        return try rotateKey()
    }
    
    public static func keyData(for keyId: String) -> Data? {
        readKeyData(keyId: keyId)
    }
    
    private static func readKeyData(keyId: String) -> Data? {
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
        guard status == errSecSuccess else { return nil }
        return item as? Data
    }
    
    private static func writeKeyData(_ data: Data, keyId: String) throws(CloudSyncCryptoError) {
        let account = "cloudSyncEncKey_\(keyId)"
        let add: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock
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
    
    private static func deleteKeyData(keyId: String) {
        let account = "cloudSyncEncKey_\(keyId)"
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)
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
        guard let keyData = CloudSyncKeyManager.keyData(for: keyId) else { throw .missingKey(keyId: keyId) }
        let nonce = AES.GCM.Nonce()
        let nonceData = nonce.withUnsafeBytes { Data($0) }
        let key = SymmetricKey(data: keyData)
        let sealed = try AES.GCM.seal(plaintext, using: key, nonce: nonce)
        var ctData = Data()
        ctData.append(sealed.ciphertext)
        ctData.append(sealed.tag)
        let payload = EncryptedPayload(
            enc: .init(
                v: 1,
                alg: "A256GCM",
                kid: keyId,
                nonce: nonceData.base64EncodedString(),
                ct: ctData.base64EncodedString()
            )
        )
        return try JSONEncoder().encode(payload)
    }
    
    public static func decryptIfNeeded(_ payload: Data) throws(CloudSyncCryptoError) -> Data {
        let decoded = try? JSONDecoder().decode(EncryptedPayload.self, from: payload)
        guard let decoded else { return payload }
        guard decoded.enc.v == 1, decoded.enc.alg == "A256GCM" else { throw .invalidPayload }
        let kid = decoded.enc.kid.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !kid.isEmpty else { throw .invalidPayload }
        let nonceData = Data(base64Encoded: decoded.enc.nonce)
        let ctData = Data(base64Encoded: decoded.enc.ct)
        guard let nonceData, let ctData else { throw .invalidPayload }
        guard nonceData.count == 12 else { throw .invalidPayload }
        guard ctData.count >= 16 else { throw .invalidPayload }
        guard let keyData = CloudSyncKeyManager.keyData(for: kid) else { throw .missingKey(keyId: kid) }
        let key = SymmetricKey(data: keyData)
        let nonce = try AES.GCM.Nonce(data: nonceData)
        let tag = ctData.suffix(16)
        let ciphertext = ctData.dropLast(16)
        let box = try AES.GCM.SealedBox(nonce: nonce, ciphertext: Data(ciphertext), tag: tag)
        return try AES.GCM.open(box, using: key)
    }
}
