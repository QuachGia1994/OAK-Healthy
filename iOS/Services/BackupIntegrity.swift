import CryptoKit
import Foundation

struct OAKBackupIntegrity: Codable, Sendable, Equatable {
    var schemaVersion: Int
    var algorithm: String
    var digest: String
    var supplementCount: Int
    var historyCount: Int
}

struct OAKBackupPreview: Sendable, Equatable {
    let version: String
    let sourceSchema: String
    let supplementCount: Int
    let historyCount: Int
    let existingSupplementCount: Int
    let existingHistoryCount: Int
    let remappedSupplementIdCount: Int
    let duplicateSupplementIdCount: Int
    let duplicateHistoryCount: Int
    let orphanHistoryCount: Int
    let integrityVerified: Bool
    let canRestore: Bool
}

enum OAKBackupIntegrityError: Error, Sendable {
    case unsupportedSchema
    case unsupportedAlgorithm
    case countMismatch
    case digestMismatch
}

enum BackupRestoreError: Error, Sendable {
    case blockedByPreview
    case rollbackFailed
}

enum BackupRestoreTransaction {
    static func run(
        snapshot: OAKBackupData,
        apply: () throws -> Void,
        rollback: (OAKBackupData) throws -> Void
    ) throws {
        do {
            try apply()
        } catch {
            let applyError = error
            do { try rollback(snapshot) } catch { throw BackupRestoreError.rollbackFailed }
            throw applyError
        }
    }
}

enum OAKBackupIntegrityCodec {
    static let schemaVersion = 1
    static let algorithm = "sha256"

    static func create(_ data: OAKBackupData) -> OAKBackupIntegrity {
        OAKBackupIntegrity(
            schemaVersion: schemaVersion,
            algorithm: algorithm,
            digest: digest(data),
            supplementCount: data.stack.count,
            historyCount: data.history.count
        )
    }

    static func validate(_ data: OAKBackupData, manifest: OAKBackupIntegrity) throws {
        guard manifest.schemaVersion == schemaVersion else { throw OAKBackupIntegrityError.unsupportedSchema }
        guard manifest.algorithm.lowercased() == algorithm else { throw OAKBackupIntegrityError.unsupportedAlgorithm }
        guard manifest.supplementCount == data.stack.count,
              manifest.historyCount == data.history.count else { throw OAKBackupIntegrityError.countMismatch }
        guard manifest.digest.lowercased() == digest(data) else { throw OAKBackupIntegrityError.digestMismatch }
    }

    static func digest(_ data: OAKBackupData) -> String {
        let bytes = Data(canonicalText(data).utf8)
        return SHA256.hash(data: bytes).map { String(format: "%02x", $0) }.joined()
    }

    static func canonicalText(_ data: OAKBackupData) -> String {
        var lines = ["version|\(encoded(data.version))", metaLine(data.meta)]
        lines += data.stack.map(supplementLine).sorted()
        lines += data.history.map(historyLine).sorted()
        return lines.joined(separator: "\n")
    }

    private static func metaLine(_ meta: OAKBackupMeta?) -> String {
        guard let meta else { return "meta|-" }
        return ["meta", "\(meta.schemaVersion)", "\(meta.updatedAtEpochMs)", encoded(meta.deviceId)].joined(separator: "|")
    }

    private static func supplementLine(_ dto: OAKBackupSupplement) -> String {
        let cycle = dto.cycle
        return [
            "stack", encoded(dto.id), encoded(dto.name), encoded(dto.dailyDose), encoded(dto.intakeTime), encoded(dto.startDate),
            cycle.isContinuous ? "true" : "false", "\(cycle.daysOn)", "\(cycle.daysOff)", optional(cycle.durationMonths),
            optional(cycle.weeklyWeekdaysMask), optional(cycle.weeklyIntervalWeeks), encoded(cycle.weeklyAnchorDate ?? ""),
            optional(cycle.intervalDays), encoded(dto.lastTakenLocalDate ?? ""), "\(dto.updatedAtEpochMs)",
            optional(dto.deletedAtEpochMs), encoded((dto.modifiedFields ?? []).sorted().joined(separator: ","))
        ].joined(separator: "|")
    }

    private static func historyLine(_ dto: OAKBackupHistory) -> String {
        [
            "history", encoded(dto.id), encoded(dto.supplementId), "\(dto.dateEpochMs)",
            encoded(dto.status), "\(dto.updatedAtEpochMs)"
        ].joined(separator: "|")
    }

    private static func optional<T>(_ value: T?) -> String {
        value.map { String(describing: $0) } ?? ""
    }

    private static func encoded(_ value: String) -> String {
        Data(value.utf8).base64EncodedString()
    }
}
