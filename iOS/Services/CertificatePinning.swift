import Foundation
import Security
import CryptoKit

// ponytail: cert pinning via URLSessionDelegate.
// In DEBUG, logs real SPKI hashes on first connection so you can populate pinnedHashes.
// In RELEASE, validates against the hardcoded pins. If no pins are set, rejects connections (fail-closed).
final class PinnedSessionDelegate: NSObject, URLSessionDelegate {
    // ponytail: capture real hashes by running a DEBUG build once, then paste them here.
    // Format: Base64(SHA-256(DER(SubjectPublicKeyInfo)))
    private static let pinnedHashes: [String: Set<String>] = [:]

    private static let pinExpiration = Date(timeIntervalSince1970: 1830768000) // 2028-01-01

    func urlSession(
        _ session: URLSession,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
              let serverTrust = challenge.protectionSpace.serverTrust,
              SecTrustEvaluateWithError(serverTrust, nil),
              let certificate = SecTrustGetCertificateAtIndex(serverTrust, 0)
        else {
            completionHandler(.cancelAuthenticationChallenge, nil)
            return
        }
        let host = challenge.protectionSpace.host, serverHash = sha256SPKIHash(of: certificate)
        #if DEBUG
        NSLog("[CertPin] host=%@ spki_sha256=%@", host, serverHash)
        completionHandler(.useCredential, URLCredential(trust: serverTrust))
        return
        #endif
        guard let pins = Self.pinnedHashes[host], !pins.isEmpty,
              Date() < Self.pinExpiration else {
            completionHandler(.cancelAuthenticationChallenge, nil)
            return
        }
        let matched = pins.contains(serverHash)
        #if DEBUG
        NSLog("[CertPin] Pin %@ for %@ — got: %@", matched ? "match" : "mismatch", host, serverHash)
        #endif
        completionHandler(matched ? .useCredential : .cancelAuthenticationChallenge, matched ? URLCredential(trust: serverTrust) : nil)
    }

    private func sha256SPKIHash(of certificate: SecCertificate) -> String {
        guard let publicKey = SecCertificateCopyKey(certificate),
              let publicKeyData = SecKeyCopyExternalRepresentation(publicKey, nil) as Data? else {
            return ""
        }
        let digest = SHA256.hash(data: publicKeyData)
        return Data(digest).base64EncodedString()
    }
}
