import XCTest
@testable import OAKHealthy

final class CloudSyncPayloadCodecTests: XCTestCase {
    func testDecompressIfNeeded_returnsInputForJSONArray() throws {
        let data = try JSONSerialization.data(withJSONObject: [1, 2, 3], options: [])
        let out = try CloudSyncPayloadCodec.decompressIfNeeded(data)
        XCTAssertEqual(out, data)
    }
    
    func testDecompressIfNeeded_throwsWhenWrapperMissingCT() throws {
        let wrapper: [String: Any] = ["z": ["v": 1, "alg": "ZLIB"]]
        let data = try JSONSerialization.data(withJSONObject: wrapper, options: [])
        do {
            _ = try CloudSyncPayloadCodec.decompressIfNeeded(data)
            XCTFail("Expected error")
        } catch let error as CloudSyncPayloadCodecError {
            switch error {
            case .missingCompressedField(let field):
                XCTAssertEqual(field, "ct")
            default:
                XCTFail("Unexpected error")
            }
        }
    }
}

final class CloudSyncTelemetryTests: XCTestCase {
    func testPollIntervalBacksOffAfterUserActivityIsStale() async {
        let interval = await MainActor.run {
            CloudSyncAutoSync.pollInterval(
                nowEpoch: 1_000,
                lastFailureEpoch: 0,
                lastActivityEpoch: 975
            )
        }
        XCTAssertEqual(interval, .seconds(30))
    }

    func testTelemetryFields_includesServerErrorFields() async {
        let clientId = UUID(uuidString: "00000000-0000-0000-0000-000000000001")!
        let fields = await MainActor.run {
            CloudSyncAutoSync.telemetryFields(
                binId: "bin",
                clientId: clientId,
                error: CloudSyncError.serverError(statusCode: 500, body: " hi \n")
            )
        }
        XCTAssertEqual(fields["status_code"], "500")
        XCTAssertEqual(fields["server_body"], "hi")
        XCTAssertEqual(fields["bin_id"], "bin")
        XCTAssertEqual(fields["client_id"], clientId.uuidString)
    }
    
    func testTelemetryFields_truncatesServerBody() async {
        let clientId = UUID(uuidString: "00000000-0000-0000-0000-000000000001")!
        let body = String(repeating: "a", count: 400)
        let fields = await MainActor.run {
            CloudSyncAutoSync.telemetryFields(
                binId: "bin",
                clientId: clientId,
                error: CloudSyncError.serverError(statusCode: 500, body: body)
            )
        }
        XCTAssertEqual(fields["server_body"]?.count, 240)
    }
}

final class CloudSyncManifestCodecTests: XCTestCase {
    func testRoundTrip() throws {
        let data = try CloudSyncManifestCodec.encode(stackBinId: "stack", historyBinId: "history")
        let decoded = try CloudSyncManifestCodec.decode(data)
        XCTAssertEqual(decoded.v, 1)
        XCTAssertEqual(decoded.stackBinId, "stack")
        XCTAssertEqual(decoded.historyBinId, "history")
    }
}

final class FirebaseRevisionTests: XCTestCase {
    func testLinkCodeValidationRejectsFirebasePathInjection() {
        XCTAssertTrue(FirebaseCloudStore.isValidBinId("-Oabc_123"))
        XCTAssertFalse(FirebaseCloudStore.isValidBinId("oakBins/other"))
        XCTAssertFalse(FirebaseCloudStore.isValidBinId("bin.with.dot"))
        XCTAssertFalse(FirebaseCloudStore.isValidBinId(" -Oabc_123 "))
        XCTAssertFalse(FirebaseCloudStore.isValidBinId(String(repeating: "x", count: 65)))
    }

    func testRevisionAlwaysIncreasesWhenClockDoesNot() {
        XCTAssertEqual(FirebaseCloudStore.nextRevision(current: 100, now: 99), 101)
        XCTAssertEqual(FirebaseCloudStore.nextRevision(current: 100, now: 150), 150)
    }

    func testRevisionParsesFirebaseNumberAndStringValues() {
        XCTAssertEqual(FirebaseCloudStore.revision(from: Int64(42)), 42)
        XCTAssertEqual(FirebaseCloudStore.revision(from: " 42 "), 42)
        XCTAssertNil(FirebaseCloudStore.revision(from: "not-a-revision"))
    }

    func testExpectedRevisionMustMatchCurrentValue() {
        XCTAssertTrue(FirebaseCloudStore.matchesExpected(current: 7, expected: ""))
        XCTAssertTrue(FirebaseCloudStore.matchesExpected(current: 7, expected: "7"))
        XCTAssertFalse(FirebaseCloudStore.matchesExpected(current: 8, expected: "7"))
        XCTAssertFalse(FirebaseCloudStore.matchesExpected(current: nil, expected: "7"))
    }
}

final class CloudSyncCryptoInteropTests: XCTestCase {
    private let exportedKey = "interop-key:AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
    private let nonce = "AAECAwQFBgcICQoL"
    private let ciphertext = "PCC5eq7H+DnkL+Puw4YIQPXnpUmVBOFtPs/RqbLwTBZTYsL5"

    func testDecryptsCrossPlatformAESGCMFixture() throws {
        _ = try CloudSyncKeyManager.importKey(exported: exportedKey)
        let output = try CloudSyncCrypto.decryptIfNeeded(envelope(ciphertext: ciphertext))
        XCTAssertEqual(String(data: output, encoding: .utf8), "{\"oak\":\"interop-v1\"}")
    }

    func testDecryptsLegacyNoncePrefixedCiphertext() throws {
        _ = try CloudSyncKeyManager.importKey(exported: exportedKey)
        let legacy = "AAECAwQFBgcICQoLPCC5eq7H+DnkL+Puw4YIQPXnpUmVBOFtPs/RqbLwTBZTYsL5"
        let output = try CloudSyncCrypto.decryptIfNeeded(envelope(ciphertext: legacy))
        XCTAssertEqual(String(data: output, encoding: .utf8), "{\"oak\":\"interop-v1\"}")
    }

    func testRejectsInvalidKeyIdentifiers() {
        XCTAssertTrue(CloudSyncKeyManager.isValidKeyId("interop-key"))
        XCTAssertFalse(CloudSyncKeyManager.isValidKeyId("../key"))
        XCTAssertFalse(CloudSyncKeyManager.isValidKeyId(" interop-key "))
        XCTAssertFalse(CloudSyncKeyManager.isValidKeyId(String(repeating: "x", count: 65)))
    }

    func testRejectsPlaintextDowngradeWhenEncryptionIsEnabled() {
        XCTAssertThrowsError(
            try CloudSyncCrypto.validateEncryptionMode(
                localUsesEncryption: true,
                cloudUsesEncryption: false
            )
        )
    }

    private func envelope(ciphertext: String) throws -> Data {
        let json = #"{"enc":{"v":1,"alg":"A256GCM","kid":"interop-key","nonce":"\#(nonce)","ct":"\#(ciphertext)"}}"#
        return try XCTUnwrap(json.data(using: .utf8))
    }
}
