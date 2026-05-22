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
