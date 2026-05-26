import FirebaseDatabase
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
        let snap = try await get(path: [id, "meta", "rev"])
        let num = snap.value as? NSNumber
        return num?.stringValue
    }
    
    static func readPayload(id: String) async throws -> String? {
        try await FirebaseBootstrap.ensureSignedIn()
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
        try await withCheckedThrowingContinuation { cont in
            root().child(id).removeValue { error, _ in
                if let error { cont.resume(throwing: error); return }
                cont.resume(returning: ())
            }
        }
    }
    
    private static func get(path: [String]) async throws -> DataSnapshot {
        try await withCheckedThrowingContinuation { cont in
            root().child(path.joined(separator: "/")).getData { error, snapshot in
                if let error { cont.resume(throwing: error); return }
                if let snapshot { cont.resume(returning: snapshot); return }
                cont.resume(throwing: FirebaseMissingSnapshotError())
            }
        }
    }
    
    private static func update(id: String, values: [AnyHashable: Any]) async throws {
        try await withCheckedThrowingContinuation { cont in
            root().child(id).updateChildValues(values) { error, _ in
                if let error { cont.resume(throwing: error); return }
                cont.resume(returning: ())
            }
        }
    }
}

struct FirebaseConflictError: Error {}
struct FirebaseMissingSnapshotError: Error {}
