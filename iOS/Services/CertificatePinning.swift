import Foundation
import Security
import CryptoKit

// ponytail: cert pinning via URLSessionDelegate.
// In DEBUG, logs real SPKI hashes on first connection so you can populate pinnedHashes.
// In RELEASE, validates against the hardcoded pins. If no pins are set, skips validation (fail-open).
final class PinnedSessionDelegate: NSObject, URLSessionDelegate {
    private static let pinnedHashes: [String: Set<String>] = [:]
    // ponytail: capture real hashes by running a DEBUG build once, then paste them here.
    // Format: Base64(SHA-256(DER(SubjectPublicKeyInfo)))
    private static let pinExpiration = Date(timeIntervalSince1970: 1830768000) // 2028-01-01

    func urlSession(
        _ session: URLSession,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        guard let (serverTrust, certificate) = extractTrust(from: challenge),
              let host = Optional(challenge.protectionSpace.host)
        else { return completionHandler(.cancelAuthenticationChallenge, nil) }

        let serverHash = sha256SPKIHash(of: certificate)

        #if DEBUG
        NSLog("[CertPin] host=%@ spki_sha256=%@", host, serverHash)
        completionHandler(.useCredential, URLCredential(trust: serverTrust))
        return
        #endif

        let credential = URLCredential(trust: serverTrust)
        guard let pins = Self.pinnedHashes[host], !pins.isEmpty else {
            return completionHandler(.useCredential, credential)
        }
        guard Date() < Self.pinExpiration else {
            NSLog("[CertPin] Pins expired for %@", host)
            return completionHandler(.cancelAuthenticationChallenge, nil)
        }
        pins.contains(serverHash)
            ? completionHandler(.useCredential, credential)
            : { NSLog("[CertPin] Pin mismatch for %@ — got: %@", host, serverHash)
                completionHandler(.cancelAuthenticationChallenge, nil) }()
    }

    private func extractTrust(from challenge: URLAuthenticationChallenge) -> (SecTrust, SecCertificate)? {
        guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
              let trust = challenge.protectionSpace.serverTrust,
              SecTrustEvaluateWithError(trust, nil),
              let cert = SecTrustGetCertificateAtIndex(trust, 0)
        else { return nil }
        return (trust, cert)
    }

    private func sha256SPKIHash(of certificate: SecCertificate) -> String {
        guard let publicKey = SecCertificateCopyKey(certificate),
              let keyData = SecKeyCopyExternalRepresentation(publicKey, nil) as Data? else { return "" }
        return Data(SHA256.hash(data: keyData)).base64EncodedString()
    }
}
