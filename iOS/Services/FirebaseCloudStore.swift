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
    private var activeManifestId: String?
    private var boundBinIds = Set<String>()
    private var pendingRevisions: [String: String] = [:]
    private var syncTask: Task<Void, Never>?
    private var activeSession: CloudSyncRealtimeSession?
    private let modelContext: ModelContext

    init(modelContext: ModelContext) {
        self.modelContext = modelContext
    }

    func start(session: CloudSyncRealtimeSession) async {
        stop()
        guard !session.manifestId.isEmpty else { return }
        let startGeneration = generation
        do {
            try await FirebaseBootstrap.ensureSignedIn()
        } catch {
            DebugReporter.report("realtime_sync_auth_failed", fields: ["error": error.localizedDescription])
            return
        }
        guard startGeneration == generation else { return }
        activeSession = session
        activeManifestId = session.manifestId
        bindListeners(manifestId: session.manifestId)
    }

    func stop() {
        generation += 1
        syncTask?.cancel()
        syncTask = nil
        pendingRevisions.removeAll()
        activeSession = nil
        activeManifestId = nil
        boundBinIds.removeAll()
        removeObservations()
        stackObservation = nil
        historyObservation = nil
        manifestObservation = nil
    }

    private func observeRevChange(binId: String) -> Observation? {
        guard FirebaseCloudStore.isValidBinId(binId) else { return nil }
        let ref = Database.database(url: FirebaseBootstrap.databaseURL).reference().child("oakBins").child(binId).child("meta").child("rev")
        let handle = ref.observe(.value) { [weak self] snapshot, error in
            guard let self else { return }
            if error != nil {
                Task { @MainActor in self.scheduleListenerRestart() }
                return
            }
            guard let newRev = FirebaseCloudStore.revision(from: snapshot.value) else { return }
            Task { @MainActor in
                self.queueRevision(binId: binId, revision: newRev.description)
            }
        }
        return (ref, handle)
    }

    private func queueRevision(binId: String, revision: String) {
        let key = "cloudSyncLastSeenRevV2_\(binId)"
        let oldRevision = UserDefaults.standard.string(forKey: key)
        let appliedRevision = UserDefaults.standard.string(forKey: "cloudSyncEtagV2_\(binId)")
        guard Self.shouldQueueRevision(
            lastProcessed: oldRevision,
            applied: appliedRevision,
            pending: pendingRevisions[binId],
            incoming: revision
        ) else {
            if appliedRevision == revision { UserDefaults.standard.set(revision, forKey: key) }
            return
        }
        pendingRevisions[binId] = revision
        guard syncTask == nil, let session = activeSession else { return }
        let startGeneration = generation
        syncTask = Task { @MainActor in
            await processPendingRevisions(startGeneration: startGeneration, session: session)
        }
    }

    static func shouldQueueRevision(
        lastProcessed: String?,
        applied: String?,
        pending: String?,
        incoming: String
    ) -> Bool {
        incoming != lastProcessed && incoming != applied && incoming != pending
    }

    private func processPendingRevisions(
        startGeneration: Int,
        session: CloudSyncRealtimeSession
    ) async {
        var failureAttempt = 0
        while shouldContinueProcessing(startGeneration: startGeneration, session: session) {
            let targets = pendingRevisions
            let success = await CloudSyncAutoSync.syncIfEnabled(
                modelContext: modelContext,
                clientId: session.clientId
            )
            guard shouldAcceptSyncResult(startGeneration: startGeneration, session: session) else { break }
            guard success else {
                failureAttempt = min(failureAttempt + 1, 4)
                await waitBeforeRetry(attempt: failureAttempt)
                continue
            }
            failureAttempt = 0
            markProcessed(targets)
            refreshBindingsIfNeeded()
        }
        if generation == startGeneration { syncTask = nil }
    }

    private func shouldContinueProcessing(
        startGeneration: Int,
        session: CloudSyncRealtimeSession
    ) -> Bool {
        !Task.isCancelled && generation == startGeneration && activeSession == session && !pendingRevisions.isEmpty
    }

    private func shouldAcceptSyncResult(
        startGeneration: Int,
        session: CloudSyncRealtimeSession
    ) -> Bool {
        Self.shouldAcceptSyncResult(
            startGeneration: startGeneration,
            currentGeneration: generation,
            expectedSession: session,
            activeSession: activeSession,
            isCancelled: Task.isCancelled
        )
    }

    static func shouldAcceptSyncResult(
        startGeneration: Int,
        currentGeneration: Int,
        expectedSession: CloudSyncRealtimeSession,
        activeSession: CloudSyncRealtimeSession?,
        isCancelled: Bool
    ) -> Bool {
        !isCancelled && startGeneration == currentGeneration && expectedSession == activeSession
    }

    private func markProcessed(_ targets: [String: String]) {
        for (binId, revision) in targets where pendingRevisions[binId] == revision {
            UserDefaults.standard.set(revision, forKey: "cloudSyncLastSeenRevV2_\(binId)")
            pendingRevisions.removeValue(forKey: binId)
        }
    }

    private func waitBeforeRetry(attempt: Int) async {
        let delay: Duration
        switch attempt {
        case 1: delay = .seconds(1)
        case 2: delay = .seconds(3)
        case 3: delay = .seconds(10)
        default: delay = .seconds(30)
        }
        do {
            try await Task.sleep(for: delay)
        } catch {
            return
        }
    }

    private func bindListeners(manifestId: String) {
        let stackKey = "cloudSyncStackBinId_\(manifestId)"
        let historyKey = "cloudSyncHistoryBinId_\(manifestId)"
        let stackBinId = trimmedBinId(forKey: stackKey)
        let historyBinId = trimmedBinId(forKey: historyKey)
        if FirebaseCloudStore.isValidBinId(stackBinId) {
            stackObservation = observeRevChange(binId: stackBinId)
            boundBinIds.insert(stackBinId)
        }
        if FirebaseCloudStore.isValidBinId(historyBinId) {
            historyObservation = observeRevChange(binId: historyBinId)
            boundBinIds.insert(historyBinId)
        }
        if !FirebaseCloudStore.isValidBinId(stackBinId) || !FirebaseCloudStore.isValidBinId(historyBinId) {
            manifestObservation = observeRevChange(binId: manifestId)
            boundBinIds.insert(manifestId)
        }
    }

    private func refreshBindingsIfNeeded(force: Bool = false) {
        guard let manifestId = activeManifestId else { return }
        let stackBinId = trimmedBinId(forKey: "cloudSyncStackBinId_\(manifestId)")
        let historyBinId = trimmedBinId(forKey: "cloudSyncHistoryBinId_\(manifestId)")
        var desired = Set<String>()
        if FirebaseCloudStore.isValidBinId(stackBinId) { desired.insert(stackBinId) }
        if FirebaseCloudStore.isValidBinId(historyBinId) { desired.insert(historyBinId) }
        if !FirebaseCloudStore.isValidBinId(stackBinId) || !FirebaseCloudStore.isValidBinId(historyBinId) {
            desired.insert(manifestId)
        }
        guard force || desired != boundBinIds else { return }
        removeObservations()
        stackObservation = nil
        historyObservation = nil
        manifestObservation = nil
        boundBinIds.removeAll()
        bindListeners(manifestId: manifestId)
    }

    private func scheduleListenerRestart() {
        let expectedGeneration = generation
        Task { @MainActor in
            do {
                try await Task.sleep(for: .seconds(1))
            } catch {
                return
            }
            guard self.generation == expectedGeneration else { return }
            self.refreshBindingsIfNeeded(force: true)
        }
    }

    private func trimmedBinId(forKey key: String) -> String {
        (UserDefaults.standard.string(forKey: key) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func removeObservations() {
        for observation in [stackObservation, historyObservation, manifestObservation].compactMap({ $0 }) {
            observation.reference.removeObserver(withHandle: observation.handle)
        }
    }

}
