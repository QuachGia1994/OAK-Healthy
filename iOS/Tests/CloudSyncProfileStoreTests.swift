import XCTest
@testable import OAKHealthy

final class CloudSyncProfileStoreTests: XCTestCase {
    private let clientA = UUID(uuid: (1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1))
    private let clientB = UUID(uuid: (2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2))
    private var suiteName = ""
    private var defaults: UserDefaults!

    override func setUp() {
        super.setUp()
        suiteName = "CloudSyncProfileStoreTests.\(UUID().uuidString)"
        defaults = UserDefaults(suiteName: suiteName)
    }

    override func tearDown() {
        defaults.removePersistentDomain(forName: suiteName)
        defaults = nil
        super.tearDown()
    }

    func testLinksAreIsolatedAndHostedWins() {
        let store = CloudSyncProfileStore(defaults: defaults)
        store.setLinkedBinId("linked-a", clientId: clientA)
        store.setHostedBinId("hosted-a", clientId: clientA)
        store.setLinkedBinId("linked-b", clientId: clientB)

        XCTAssertEqual(store.activeManifestId(clientId: clientA), "hosted-a")
        XCTAssertEqual(store.activeManifestId(clientId: clientB), "linked-b")
    }

    func testLegacyLinksMigrateOnlyToRequestedClient() {
        defaults.set("legacy-host", forKey: "cloudSyncHostedBinId")
        defaults.set("legacy-link", forKey: "cloudSyncLinkedBinId")
        let store = CloudSyncProfileStore(defaults: defaults)

        XCTAssertEqual(store.links(clientId: clientA).hostedBinId, "legacy-host")
        XCTAssertNil(store.links(clientId: clientB).hostedBinId)
        XCTAssertNil(defaults.object(forKey: "cloudSyncHostedBinId"))
        XCTAssertNil(defaults.object(forKey: "cloudSyncLinkedBinId"))
    }

    func testLegacyLinksDoNotOverwriteScopedValues() {
        let scopedKey = "cloudSyncHostedBinId_client_\(clientA.uuidString.lowercased())"
        defaults.set("legacy-host", forKey: "cloudSyncHostedBinId")
        defaults.set("scoped-host", forKey: scopedKey)
        let store = CloudSyncProfileStore(defaults: defaults)

        XCTAssertEqual(store.links(clientId: clientA).hostedBinId, "scoped-host")
    }

    func testNilClientHasNoCloudLink() {
        let store = CloudSyncProfileStore(defaults: defaults)
        XCTAssertNil(store.activeManifestId(clientId: nil))
    }

    func testClearLinksRemovesOnlyTargetClient() {
        let store = CloudSyncProfileStore(defaults: defaults)
        store.setHostedBinId("hosted-a", clientId: clientA)
        store.setHostedBinId("hosted-b", clientId: clientB)
        store.clearLinks(clientId: clientA)

        XCTAssertNil(store.activeManifestId(clientId: clientA))
        XCTAssertEqual(store.activeManifestId(clientId: clientB), "hosted-b")
    }
}
