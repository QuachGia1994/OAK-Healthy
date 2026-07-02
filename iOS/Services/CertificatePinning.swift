import Foundation
import Security
import CryptoKit

// ponytail: cert pinning via URLSessionDelegate.
// In DEBUG, logs real SPKI hashes. In RELEASE, validates pins. No pins = skip (fail-open).
final class PinnedSessionDelegate: NSObject, URLSessionDelegate {
    private static let pinnedHashes: [String: Set<String>] = [:]
    private static let pinExpiration = Date(timeIntervalSince1970: 1830768000) // 2028-01-01

    func urlSession(
        _ session: URLSession,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        guard let (trust, cert) = extractTrust(from: challenge) else {
            return completionHandler(.cancelAuthenticationChallenge, nil)
        }
        let host = challenge.protectionSpace.host
        let hash = sha256SPKIHash(of: cert)
        #if DEBUG
        NSLog("[CertPin] host=%@ spki_sha256=%@", host, hash)
        return completionHandler(.useCredential, URLCredential(trust: trust))
        #endif
        validatePin(host: host, hash: hash, trust: trust, completionHandler: completionHandler)
    }

    private func validatePin(host: String, hash: String, trust: SecTrust,
                             completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void) {
        let credential = URLCredential(trust: trust)
        guard let pins = Self.pinnedHashes[host], !pins.isEmpty else {
            return completionHandler(.useCredential, credential)
        }
        guard Date() < Self.pinExpiration else {
            NSLog("[CertPin] Pins expired for %@", host)
            return completionHandler(.cancelAuthenticationChallenge, nil)
        }
        pins.contains(hash)
            ? completionHandler(.useCredential, credential)
            : { NSLog("[CertPin] Pin mismatch for %@ — got: %@", host, hash)
                completionHandler(.cancelAuthenticationChallenge, nil) }()
    }

    private func extractTrust(from challenge: URLAuthenticationChallenge) -> (SecTrust, SecCertificate)? {
        guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
              let trust = challenge.protectionSpace.serverTrust,
              SecTrustEvaluateWithError(trust, nil),
              let cert = SecTrustGetCertificateAtIndex(trust, 0) else { return nil }
        return (trust, cert)
    }

    private func sha256SPKIHash(of certificate: SecCertificate) -> String {
        guard let key = SecCertificateCopyKey(certificate),
              let data = SecKeyCopyExternalRepresentation(key, nil) as Data? else { return "" }
        return Data(SHA256.hash(data: data)).base64EncodedString()
    }
}
