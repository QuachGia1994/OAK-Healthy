import XCTest
import SwiftData
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

    func testHistoryZlibRejectsInvalidChecksum() throws {
        var compressed = try XCTUnwrap(Data(base64Encoded: androidHistoryZlib))
        let lastIndex = compressed.index(before: compressed.endIndex)
        compressed[lastIndex] ^= 0x01

        XCTAssertThrowsError(try ZlibBase64Codec.decodeArray(base64: compressed.base64EncodedString()))
    }
}

final class SupplementMergeRegressionTests: XCTestCase {
    @MainActor
    func testMergeDoesNotResurrectNewerLocalDeletion() throws {
        let schema = Schema([ClientProfile.self, UserSupplement.self, IntakeRecord.self])
        let config = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
        let container = try ModelContainer(for: schema, configurations: config)
        let context = ModelContext(container)
        let client = ClientProfile(name: "Test")
        let id = UUID()
        let local = UserSupplement(
            id: id, name: "Local", startDate: .now, cycleConfig: .continuous,
            dailyDose: "1", intakeTime: "08:00", updatedAtEpochMs: 100,
            deletedAtEpochMs: 300, client: client
        )
        context.insert(client)
        context.insert(local)
        try context.save()
        let cycle = SupplementExportCycle(
            isContinuous: true, daysOn: 1, daysOff: 0, durationMonths: nil,
            weeklyWeekdaysMask: nil, weeklyIntervalWeeks: nil, weeklyAnchorDate: nil, intervalDays: nil
        )
        let remote = OAKBackupSupplement(
            id: id.uuidString, name: "Remote", dailyDose: "2", intakeTime: "09:00",
            startDate: "2026-01-01", cycle: cycle, lastTakenLocalDate: nil,
            updatedAtEpochMs: 200, deletedAtEpochMs: nil, modifiedFields: ["name"]
        )
        let backup = OAKBackupData(version: "2.0", meta: nil, stack: [remote], history: [], historyZlibBase64: nil)
        try SupplementExportCodec.mergeBackupDataSafely(backup, client: client, context: context)

        XCTAssertEqual(local.name, "Local")
        XCTAssertEqual(local.deletedAtEpochMs, 300)
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

    func testTelemetryFieldsExposeOnlyCoarseFailureMetadata() async {
        let clientId = UUID(uuidString: "00000000-0000-0000-0000-000000000001")!
        let fields = await MainActor.run {
            CloudSyncAutoSync.telemetryFields(
                binId: "sensitive-bin",
                clientId: clientId,
                error: CloudSyncError.serverError(statusCode: 500, body: "sensitive server body")
            )
        }
        XCTAssertEqual(fields["status_code"], "500")
        XCTAssertEqual(fields["error_type"], "server_error")
        XCTAssertNil(fields["server_body"])
        XCTAssertNil(fields["bin_id"])
        XCTAssertNil(fields["client_id"])
        XCTAssertNil(fields["error"])
    }

    func testSuccessfulTelemetryContainsNoIdentifiers() async {
        let clientId = UUID(uuidString: "00000000-0000-0000-0000-000000000001")!
        let fields = await MainActor.run {
            CloudSyncAutoSync.telemetryFields(binId: "sensitive-bin", clientId: clientId, error: nil)
        }
        XCTAssertTrue(fields.isEmpty)
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

    func testRealtimeListenerRejectsStaleSessionCompletion() async {
        let clientA = UUID(uuidString: "00000000-0000-0000-0000-000000000001")!
        let clientB = UUID(uuidString: "00000000-0000-0000-0000-000000000002")!
        let sessionA = CloudSyncRealtimeSession(clientId: clientA, manifestId: "shared")
        let sessionB = CloudSyncRealtimeSession(clientId: clientB, manifestId: "shared")
        let results = await MainActor.run {
            [
                FirebaseRealtimeSyncListener.shouldAcceptSyncResult(
                    startGeneration: 3, currentGeneration: 3,
                    expectedSession: sessionA, activeSession: sessionA, isCancelled: false
                ),
                FirebaseRealtimeSyncListener.shouldAcceptSyncResult(
                    startGeneration: 3, currentGeneration: 4,
                    expectedSession: sessionA, activeSession: sessionA, isCancelled: false
                ),
                FirebaseRealtimeSyncListener.shouldAcceptSyncResult(
                    startGeneration: 3, currentGeneration: 3,
                    expectedSession: sessionA, activeSession: sessionB, isCancelled: false
                ),
                FirebaseRealtimeSyncListener.shouldAcceptSyncResult(
                    startGeneration: 3, currentGeneration: 3,
                    expectedSession: sessionA, activeSession: sessionA, isCancelled: true
                )
            ]
        }
        XCTAssertEqual(results, [true, false, false, false])
    }
}

final class CloudSyncCoordinatorRegressionTests: XCTestCase {
    @MainActor
    func testLegacyFallbackOnlyForLegacyPayloadShapes() {
        XCTAssertTrue(CloudSyncAutoSync.shouldUseLegacyFallback(.invalidResponse))
        XCTAssertTrue(CloudSyncAutoSync.shouldUseLegacyFallback(.manifestCodec(.decodeFailed)))
        XCTAssertFalse(CloudSyncAutoSync.shouldUseLegacyFallback(.networkError(message: "offline")))
    }

    @MainActor
    func testConflictRetryOnlyForRevisionConflicts() {
        XCTAssertTrue(CloudSyncAutoSync.isConflictError(CloudSyncError.serverError(statusCode: 409, body: "")))
        XCTAssertTrue(CloudSyncAutoSync.isConflictError(CloudSyncError.serverError(statusCode: 412, body: "")))
        XCTAssertFalse(CloudSyncAutoSync.isConflictError(CloudSyncError.serverError(statusCode: 500, body: "")))
    }

    @MainActor
    func testRealtimeSessionReuseRequiresSameProfileAndManifest() {
        let clientA = UUID(uuidString: "00000000-0000-0000-0000-000000000001")!
        let clientB = UUID(uuidString: "00000000-0000-0000-0000-000000000002")!
        let sessionA = CloudSyncRealtimeSession(clientId: clientA, manifestId: "shared")
        let sessionB = CloudSyncRealtimeSession(clientId: clientB, manifestId: "shared")

        XCTAssertTrue(CloudSyncAutoSync.shouldReuseRealtimeSession(current: sessionA, requested: sessionA, hasTask: true))
        XCTAssertFalse(CloudSyncAutoSync.shouldReuseRealtimeSession(current: sessionA, requested: sessionB, hasTask: true))
        XCTAssertFalse(CloudSyncAutoSync.shouldReuseRealtimeSession(current: sessionA, requested: sessionA, hasTask: false))
    }

    @MainActor
    func testSuccessfulSyncCleanupRemovesStaleFailureState() throws {
        let suite = "CloudSyncCoordinatorRegressionTests.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        let binId = "test-bin"
        defaults.set("stale", forKey: "cloudSyncLastError_\(binId)")
        defaults.set(123.0, forKey: "cloudSyncLastFailureEpoch")

        CloudSyncAutoSync.clearFailureState(binId: binId, defaults: defaults)

        XCTAssertNil(defaults.string(forKey: "cloudSyncLastError_\(binId)"))
        XCTAssertEqual(defaults.double(forKey: "cloudSyncLastFailureEpoch"), 0)
        defaults.removePersistentDomain(forName: suite)
    }

    @MainActor
    func testRunGateSerializesManualBehindAuto() async {
        let gate = CloudSyncRunGate(waitInterval: .milliseconds(1))
        XCTAssertTrue(gate.beginAuto())
        XCTAssertFalse(gate.beginAuto())
        XCTAssertTrue(gate.takeAutoRerun())
        let release = Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(20))
            gate.finish()
        }
        let clock = ContinuousClock()
        let started = clock.now
        let acquired = await gate.beginManual()
        let elapsed = started.duration(to: clock.now)

        XCTAssertTrue(acquired)
        XCTAssertTrue(elapsed >= Duration.milliseconds(10))
        gate.finish()
        _ = await release.value
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
