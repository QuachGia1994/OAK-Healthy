import XCTest
@testable import OAKHealthy

final class CloudSyncPayloadCodecTests: XCTestCase {
    func testDecompressIfNeeded_returnsInputForJSONArray() throws {
        let data = try JSONSerialization.data(withJSONObject: [1, 2, 3], options: [])
        let out = try CloudSyncPayloadCodec.decompressIfNeeded(data)
        XCTAssertEqual(out, data)
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
}

