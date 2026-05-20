import CryptoKit
import Foundation

public enum DoseEventKey: Sendable {
    public static func make(supplementId: UUID, scheduledAtEpochMs: Int64) -> String {
        "\(supplementId.uuidString.lowercased())-\(scheduledAtEpochMs)"
    }
    
    public static func stableUUID(from key: String) -> UUID {
        let data = Data(key.utf8)
        let digest = SHA256.hash(data: data)
        let bytes = Array(digest)
        return UUID(uuid: (
            bytes[0], bytes[1], bytes[2], bytes[3],
            bytes[4], bytes[5],
            bytes[6], bytes[7],
            bytes[8], bytes[9],
            bytes[10], bytes[11], bytes[12], bytes[13], bytes[14], bytes[15]
        ))
    }
    
    public static func intakeTimeString(from scheduledAt: Date) -> String {
        let calendar = Calendar.current
        let hour = calendar.component(.hour, from: scheduledAt)
        let minute = calendar.component(.minute, from: scheduledAt)
        return String(format: "%02d:%02d", hour, minute)
    }
    
    
}
