import XCTest
@testable import OAKHealthy

final class CloudSyncPayloadCodecTests: XCTestCase {
    private let androidHistoryZlib = "eJyLrlbKTFGyUipKTc4vStE1VNJRKi4tKMhJzU3NK/EEyRhAgS4WAgpAulISS1JdC/KTM3yLlawMzZFkdZSKSxJLSouVrJRCErNT85R0lEoLQMpTHEuw6DA0MKiNBQArJikc"

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

    func testDecompressIfNeeded_decodesZlibWrapper() throws {
        let compressed = "eJyrVspPzFayUsrMK0ktyi9QqgUAM7YF9w=="
        let wrapper: [String: Any] = ["z": ["v": 1, "alg": "ZLIB", "ct": compressed]]
        let data = try JSONSerialization.data(withJSONObject: wrapper, options: [])

        let output = try CloudSyncPayloadCodec.decompressIfNeeded(data)

        XCTAssertEqual(String(data: output, encoding: .utf8), #"{"oak":"interop"}"#)
    }

    func testBackupDecodeRunsOutsideMainActor() async throws {
        let json = #"{"version":"2.0","supplements":[],"historyLogs":[]}"#
        let data = try XCTUnwrap(json.data(using: .utf8))

        let backup = try await Task.detached {
            try SupplementExportCodec.decodeBackupCompat(data: data)
        }.value

        XCTAssertEqual(backup.version, "2.0")
        XCTAssertTrue(backup.stack.isEmpty)
        XCTAssertTrue(backup.history.isEmpty)
    }

    func testHistoryZlibDecodesAndroidFixture() throws {
        let history = try ZlibBase64Codec.decodeArray(base64: androidHistoryZlib)

        XCTAssertEqual(history.count, 1)
        XCTAssertEqual(history.first?.id, "record-1")
        XCTAssertEqual(history.first?.status, "Taken")
    }

    func testHistoryZlibRejectsTruncatedStream() throws {
        let compressed = try XCTUnwrap(Data(base64Encoded: androidHistoryZlib))
        let truncated = Data(compressed.dropLast(2)).base64EncodedString()

        XCTAssertThrowsError(try ZlibBase64Codec.decodeArray(base64: truncated))
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

    func testRealtimeListenerQueuesOnlyUnappliedRevision() async {
        let shouldQueue = await MainActor.run {
            FirebaseRealtimeSyncListener.shouldQueueRevision(
                lastProcessed: "40",
                applied: "41",
                pending: nil,
                incoming: "42"
            )
        }
        XCTAssertTrue(shouldQueue)
    }

    func testRealtimeListenerSkipsAppliedOrPendingRevision() async {
        let results = await MainActor.run {
            [
                FirebaseRealtimeSyncListener.shouldQueueRevision(
                    lastProcessed: "42", applied: nil, pending: nil, incoming: "42"
                ),
                FirebaseRealtimeSyncListener.shouldQueueRevision(
                    lastProcessed: nil, applied: "42", pending: nil, incoming: "42"
                ),
                FirebaseRealtimeSyncListener.shouldQueueRevision(
                    lastProcessed: nil, applied: nil, pending: "42", incoming: "42"
                )
            ]
        }
        XCTAssertEqual(results, [false, false, false])
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
