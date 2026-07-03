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
        let trimmed = id.trimmingCharacters(in: .whitespacesAndNewlines)
        return !trimmed.isEmpty && trimmed.firstMatch(of: validBinIdPattern) != nil
    }

    static func createBin(payload: String) async throws -> (id: String, rev: String) {
        try await FirebaseBootstrap.ensureSignedIn()
        let id = root().childByAutoId().key ?? UUID().uuidString
        let rev = Int64(Date().timeIntervalSince1970 * 1000)
        try await update(id: id, values: ["payload": payload, "meta/rev": rev])
        return (id, "\(rev)")
    }

    static func readMetaRev(id: String) async throws -> String? {
        try await FirebaseBootstrap.ensureSignedIn()
        guard isValidBinId(id) else { return nil }
        root().child(id).keepSynced(true)
        let snap = try await get(path: [id, "meta", "rev"])
        let num = snap.value as? NSNumber
        return num?.stringValue
    }

    static func readPayload(id: String) async throws -> String? {
        try await FirebaseBootstrap.ensureSignedIn()
        guard isValidBinId(id) else { return nil }
        root().child(id).keepSynced(true)
        let snap = try await get(path: [id, "payload"])
        return snap.value as? String
    }

    static func write(id: String, payload: String, expectedRev: String?) async throws -> String {
        try await FirebaseBootstrap.ensureSignedIn()
        guard isValidBinId(id) else { throw FirebaseConflictError() }
        let expected = (expectedRev ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if !expected.isEmpty {
            let current = (try await readMetaRev(id: id) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            if !current.isEmpty, current != expected { throw FirebaseConflictError() }
        }
        let rev = Int64(Date().timeIntervalSince1970 * 1000)
        try await update(id: id, values: ["payload": payload, "meta/rev": rev])
        return "\(rev)"
    }

    static func delete(id: String) async throws {
        try await FirebaseBootstrap.ensureSignedIn()
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
                try? await Task.sleep(for: retryDelay(attempt: attempt))
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
}

struct FirebaseConflictError: Error {}
struct FirebaseMissingSnapshotError: Error {}
struct FirebaseOfflineError: LocalizedError {
    var errorDescription: String? { "Không có kết nối Internet hoặc Firebase đang tạm lỗi. Vui lòng thử lại." }
}

@MainActor
final class FirebaseRealtimeSyncListener {
    private var stackHandle: DatabaseHandle?
    private var historyHandle: DatabaseHandle?
    private var manifestHandle: DatabaseHandle?
    private let modelContext: ModelContext
    private let activeClientManager: ActiveClientManager

    init(modelContext: ModelContext, activeClientManager: ActiveClientManager) {
        self.modelContext = modelContext
        self.activeClientManager = activeClientManager
    }

    func start(manifestId: String) async {
        stop()
        guard !manifestId.isEmpty else { return }
        try? await FirebaseBootstrap.ensureSignedIn()
        let stackKey = "cloudSyncStackBinId_\(manifestId)"
        let historyKey = "cloudSyncHistoryBinId_\(manifestId)"
        let stackBinId = (UserDefaults.standard.string(forKey: stackKey) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let historyBinId = (UserDefaults.standard.string(forKey: historyKey) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if !stackBinId.isEmpty {
            stackHandle = observeRevChange(binId: stackBinId, manifestId: manifestId)
        }
        if !historyBinId.isEmpty {
            historyHandle = observeRevChange(binId: historyBinId, manifestId: manifestId)
        }
        if stackBinId.isEmpty || historyBinId.isEmpty {
            manifestHandle = observeManifest(manifestId: manifestId)
        }
    }

    func stop() {
        let db = Database.database(url: FirebaseBootstrap.databaseURL)
        if let h = stackHandle { db.reference().removeObserver(withHandle: h) }
        if let h = historyHandle { db.reference().removeObserver(withHandle: h) }
        if let h = manifestHandle { db.reference().removeObserver(withHandle: h) }
        stackHandle = nil
        historyHandle = nil
        manifestHandle = nil
    }

    private func observeRevChange(binId: String, manifestId: String) -> DatabaseHandle {
        guard FirebaseCloudStore.isValidBinId(binId) else { return 0 }
        let ref = Database.database(url: FirebaseBootstrap.databaseURL).reference().child("oakBins").child(binId).child("meta").child("rev")
        return ref.observe(.value) { [weak self] snapshot, _ in
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
    }

    private func observeManifest(manifestId: String) -> DatabaseHandle {
        guard FirebaseCloudStore.isValidBinId(manifestId) else { return 0 }
        let ref = Database.database(url: FirebaseBootstrap.databaseURL).reference().child("oakBins").child(manifestId).child("meta").child("rev")
        return ref.observe(.value) { [weak self] snapshot, _ in
            guard let self, let newRev = snapshot.value as? NSNumber else { return }
            let key = "cloudSyncLastSeenRev_\(manifestId)"
            let oldRev = UserDefaults.standard.string(forKey: key)
            let newRevStr = newRev.stringValue
            if oldRev == newRevStr { return }
            UserDefaults.standard.set(newRevStr, forKey: key)
            Task { @MainActor in
                await CloudSyncAutoSync.syncIfEnabled(modelContext: self.modelContext, clientId: self.activeClientManager.currentClientId)
            }
        }
    }

    deinit {
        let db = Database.database(url: FirebaseBootstrap.databaseURL)
        if let h = stackHandle { db.reference().removeObserver(withHandle: h) }
        if let h = historyHandle { db.reference().removeObserver(withHandle: h) }
        if let h = manifestHandle { db.reference().removeObserver(withHandle: h) }
    }
}
