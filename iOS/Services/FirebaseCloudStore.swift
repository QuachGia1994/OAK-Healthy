@preconcurrency import FirebaseDatabase
import Foundation
import SwiftData

enum FirebaseCloudStore {
    private static let rootKey = "oakBins"
    nonisolated(unsafe) private static let validBinIdPattern = /^[A-Za-z0-9_-]{1,64}$/

    private static func root() -> DatabaseReference {
        Database.database(url: FirebaseBootstrap.databaseURL).reference().child(rootKey)
    }

    static func isValidBinId(_ id: String) -> Bool {
        !id.isEmpty && id.firstMatch(of: validBinIdPattern) != nil
    }

    static func createBin(payload: String) async throws -> (id: String, rev: String) {
        try await FirebaseBootstrap.ensureSignedIn()
        let id = root().childByAutoId().key ?? UUID().uuidString
        let rev = Int64(Date().timeIntervalSince1970 * 1000)
        try await update(id: id, values: ["payload": payload, "meta/rev": rev])
        return (id, "\(rev)")
    }

    static func readNode(id: String) async throws -> FirebaseCloudNode {
        try await FirebaseBootstrap.ensureSignedIn()
        guard isValidBinId(id) else { throw FirebaseInvalidBinIdError() }
        root().child(id).keepSynced(true)
        let snapshot = try await get(path: [id])
        let payload = snapshot.childSnapshot(forPath: "payload").value as? String
        let revisionValue = snapshot.childSnapshot(forPath: "meta/rev").value
        return FirebaseCloudNode(payload: payload, rev: revision(from: revisionValue)?.description)
    }

    static func write(id: String, payload: String, expectedRev: String?) async throws -> String {
        try await FirebaseBootstrap.ensureSignedIn()
        guard isValidBinId(id) else { throw FirebaseInvalidBinIdError() }
        let expected = (expectedRev ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if !expected.isEmpty, try await readNode(id: id).rev != expected { throw FirebaseConflictError() }
        let (committed, snapshot) = try await root().child(id).runTransactionBlock { data in
            let currentValue = data.childData(byAppendingPath: "meta/rev").value
            let current = revision(from: currentValue)
            guard matchesExpected(current: current, expected: expected) else { return .abort() }
            data.childData(byAppendingPath: "payload").value = payload
            data.childData(byAppendingPath: "meta/rev").value = nextRevision(current: current)
            return .success(withValue: data)
        }
        let value = snapshot.childSnapshot(forPath: "meta/rev").value
        guard committed, let rev = revision(from: value) else { throw FirebaseConflictError() }
        return rev.description
    }

    static func delete(id: String) async throws {
        try await FirebaseBootstrap.ensureSignedIn()
        guard isValidBinId(id) else { throw FirebaseInvalidBinIdError() }
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, any Error>) in
            root().child(id).removeValue { error, _ in
                if let error { cont.resume(throwing: error); return }
                cont.resume(returning: ())
            }
        }
    }

    private static func get(path: [String]) async throws -> DataSnapshot {
        for attempt in 1...3 {
            do {
                return try await getOnce(path: path)
            } catch {
                if isOfflineError(error), attempt == 3 { throw FirebaseOfflineError() }
                if attempt == 3 || !shouldRetryGet(error) { throw error }
                try await Task.sleep(for: retryDelay(attempt: attempt))
            }
        }
        throw FirebaseMissingSnapshotError()
    }

    private static func getOnce(path: [String]) async throws -> DataSnapshot {
        return try await withCheckedThrowingContinuation { (cont: CheckedContinuation<DataSnapshot, any Error>) in
            root().child(path.joined(separator: "/")).observeSingleEvent(
                of: .value,
                with: { snapshot in
                    cont.resume(returning: snapshot)
                },
                withCancel: { error in
                    cont.resume(throwing: error)
                }
            )
        }
    }

    private static func shouldRetryGet(_ error: Error) -> Bool {
        isOfflineError(error) || error.localizedDescription.lowercased().contains("timed out")
    }

    private static func isOfflineError(_ error: Error) -> Bool {
        let message = (error as NSError).localizedDescription.lowercased()
        return message.contains("client offline") || message.contains("offline")
    }

    private static func retryDelay(attempt: Int) -> Duration {
        switch attempt {
        case 1: return .milliseconds(350)
        case 2: return .milliseconds(900)
        default: return .milliseconds(1500)
        }
    }

    private static func update(id: String, values: [AnyHashable: Any]) async throws {
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, any Error>) in
            root().child(id).updateChildValues(values) { error, _ in
                if let error { cont.resume(throwing: error); return }
                cont.resume(returning: ())
            }
        }
    }

    static func revision(from value: Any?) -> Int64? {
        if let number = value as? NSNumber { return number.int64Value }
        if let string = value as? String { return Int64(string.trimmingCharacters(in: .whitespacesAndNewlines)) }
        return nil
    }

    static func matchesExpected(current: Int64?, expected: String) -> Bool {
        expected.isEmpty || current?.description == expected
    }

    static func nextRevision(current: Int64?, now: Int64? = nil) -> Int64 {
        let timestamp = now ?? Int64(Date().timeIntervalSince1970 * 1000)
        let incremented = current.map { $0 == Int64.max ? $0 : $0 + 1 } ?? 1
        return max(timestamp, incremented)
    }
}

struct FirebaseCloudNode: Sendable {
    let payload: String?
    let rev: String?
}

struct FirebaseConflictError: Error {}
struct FirebaseInvalidBinIdError: Error {}
struct FirebaseMissingSnapshotError: Error {}
struct FirebaseOfflineError: LocalizedError {
    var errorDescription: String? { "Không có kết nối Internet hoặc Firebase đang tạm lỗi. Vui lòng thử lại." }
}

@MainActor
final class FirebaseRealtimeSyncListener {
    private typealias Observation = (reference: DatabaseReference, handle: DatabaseHandle)
    private var stackObservation: Observation?
    private var historyObservation: Observation?
    private var manifestObservation: Observation?
    private var generation = 0
    private let modelContext: ModelContext
    private let activeClientManager: ActiveClientManager

    init(modelContext: ModelContext, activeClientManager: ActiveClientManager) {
        self.modelContext = modelContext
        self.activeClientManager = activeClientManager
    }

    func start(manifestId: String) async {
        stop()
        guard !manifestId.isEmpty else { return }
        let startGeneration = generation
        do {
            try await FirebaseBootstrap.ensureSignedIn()
        } catch {
            DebugReporter.report("realtime_sync_auth_failed", fields: ["error": error.localizedDescription])
            return
        }
        guard startGeneration == generation else { return }
        let stackKey = "cloudSyncStackBinId_\(manifestId)"
        let historyKey = "cloudSyncHistoryBinId_\(manifestId)"
        let stackBinId = (UserDefaults.standard.string(forKey: stackKey) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let historyBinId = (UserDefaults.standard.string(forKey: historyKey) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if !stackBinId.isEmpty {
            stackObservation = observeRevChange(binId: stackBinId)
        }
        if !historyBinId.isEmpty {
            historyObservation = observeRevChange(binId: historyBinId)
        }
        if stackBinId.isEmpty || historyBinId.isEmpty {
            manifestObservation = observeRevChange(binId: manifestId)
        }
    }

    func stop() {
        generation += 1
        removeObservations()
        stackObservation = nil
        historyObservation = nil
        manifestObservation = nil
    }

    private func observeRevChange(binId: String) -> Observation? {
        guard FirebaseCloudStore.isValidBinId(binId) else { return nil }
        let ref = Database.database(url: FirebaseBootstrap.databaseURL).reference().child("oakBins").child(binId).child("meta").child("rev")
        let handle = ref.observe(.value) { [weak self] snapshot, _ in
            guard let self, let newRev = snapshot.value as? NSNumber else { return }
            let key = "cloudSyncLastSeenRev_\(binId)"
            let oldRev = UserDefaults.standard.string(forKey: key)
            let newRevStr = newRev.stringValue
            if oldRev == newRevStr { return }
            UserDefaults.standard.set(newRevStr, forKey: key)
            Task { @MainActor in
                await CloudSyncAutoSync.syncIfEnabled(modelContext: self.modelContext, clientId: self.activeClientManager.currentClientId)
            }
        }
        return (ref, handle)
    }

    private func removeObservations() {
        for observation in [stackObservation, historyObservation, manifestObservation].compactMap({ $0 }) {
            observation.reference.removeObserver(withHandle: observation.handle)
        }
    }

}
