@preconcurrency import FirebaseDatabase
import Foundation

enum FirebaseCloudStore {
    private static let rootKey = "oakBins"
    
    private static func root() -> DatabaseReference {
        Database.database(url: FirebaseBootstrap.databaseURL).reference().child(rootKey)
    }
    
    static func createBin(payload: String) async throws -> String {
        try await FirebaseBootstrap.ensureSignedIn()
        let id = root().childByAutoId().key ?? UUID().uuidString
        let rev = Int64(Date().timeIntervalSince1970 * 1000)
        try await update(id: id, values: ["payload": payload, "meta/rev": rev])
        return id
    }
    
    static func readMetaRev(id: String) async throws -> String? {
        try await FirebaseBootstrap.ensureSignedIn()
        root().child(id).keepSynced(true)
        let snap = try await get(path: [id, "meta", "rev"])
        let num = snap.value as? NSNumber
        return num?.stringValue
    }
    
    static func readPayload(id: String) async throws -> String? {
        try await FirebaseBootstrap.ensureSignedIn()
        root().child(id).keepSynced(true)
        let snap = try await get(path: [id, "payload"])
        return snap.value as? String
    }
    
    static func write(id: String, payload: String, expectedRev: String?) async throws -> String {
        try await FirebaseBootstrap.ensureSignedIn()
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
        try await withCheckedThrowingContinuation { cont in
            root().child(path.joined(separator: "/")).getData { error, snapshot in
                if let error { cont.resume(throwing: error); return }
                if let snapshot { cont.resume(returning: snapshot); return }
                cont.resume(throwing: FirebaseMissingSnapshotError())
            }
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
