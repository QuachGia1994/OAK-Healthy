import Foundation
import SwiftData
import SwiftUI
import UniformTypeIdentifiers
import ImageIO

struct SupplementExportFile: Codable, Sendable {
    var schemaVersion: Int
    var exportedAtEpochMs: Int64
    var supplements: [SupplementExportSupplement]
}

struct SupplementExportSupplement: Codable, Sendable {
    var name: String
    var dailyDose: String
    var intakeTime: String
    var startDate: String
    var category: String?
    var cycle: SupplementExportCycle
    var lastTakenLocalDate: String?
}

struct SupplementExportCycle: Codable, Sendable {
    var isContinuous: Bool
    var daysOn: Int
    var daysOff: Int
    var durationMonths: Int?
    var weeklyWeekdaysMask: Int?
    var weeklyIntervalWeeks: Int?
    var weeklyAnchorDate: String?
    var intervalDays: Int?

    enum CodingKeys: String, CodingKey {
        case isContinuous
        case daysOn
        case daysOff
        case durationMonths
        case weeklyWeekdaysMask
        case weeklyIntervalWeeks
        case weeklyAnchorDate
        case intervalDays
    }
}

struct OAKBackupData: Codable, Sendable {
    var version: String
    var meta: OAKBackupMeta?
    var stack: [OAKBackupSupplement]
    var history: [OAKBackupHistory]
    var historyZlibBase64: String?

    enum CodingKeys: String, CodingKey {
        case version
        case meta
        case stack = "supplements"
        case history = "historyLogs"
        case historyZlibBase64
    }

    enum LegacyCodingKeys: String, CodingKey {
        case stack
        case history
    }

    init(version: String, meta: OAKBackupMeta?, stack: [OAKBackupSupplement], history: [OAKBackupHistory], historyZlibBase64: String?) {
        self.version = version
        self.meta = meta
        self.stack = stack
        self.history = history
        self.historyZlibBase64 = historyZlibBase64
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let legacyContainer = try decoder.container(keyedBy: LegacyCodingKeys.self)

        self.version = try container.decodeIfPresent(String.self, forKey: .version) ?? "1.1"
        self.meta = try? container.decodeIfPresent(OAKBackupMeta.self, forKey: .meta)
        self.historyZlibBase64 = try? container.decodeIfPresent(String.self, forKey: .historyZlibBase64)

        if let supplements = try container.decodeIfPresent([OAKBackupSupplement].self, forKey: .stack) {
            self.stack = supplements
        } else if let legacyStack = try legacyContainer.decodeIfPresent([OAKBackupSupplement].self, forKey: .stack) {
            self.stack = legacyStack
        } else {
            throw DecodingError.keyNotFound(
                CodingKeys.stack,
                DecodingError.Context(
                    codingPath: decoder.codingPath,
                    debugDescription: "Missing supplements/stack"
                )
            )
        }

        var mergedHistory: [OAKBackupHistory] = []
        if let historyLogs = try container.decodeIfPresent([OAKBackupHistory].self, forKey: .history) { mergedHistory.append(contentsOf: historyLogs) }
        else if let legacyHistory = try legacyContainer.decodeIfPresent([OAKBackupHistory].self, forKey: .history) { mergedHistory.append(contentsOf: legacyHistory) }
        if let historyZlibBase64, !historyZlibBase64.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            mergedHistory.append(contentsOf: try ZlibBase64Codec.decodeArray(base64: historyZlibBase64))
        }
        self.history = ZlibBase64Codec.dedupeByIdKeepingNewest(items: mergedHistory)
    }
}

enum ZlibBase64CodecError: Error, Sendable {
    case invalidBase64
    case inflateFailed
    case invalidJSON
}

enum ZlibBase64Codec {
    static func decodeArray(base64: String) throws(ZlibBase64CodecError) -> [OAKBackupHistory] {
        let trimmed = base64.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, let raw = Data(base64Encoded: trimmed) else { throw .invalidBase64 }
        guard let inflated = decompress(data: raw) else { throw .inflateFailed }
        do {
            return try JSONDecoder().decode([OAKBackupHistory].self, from: inflated)
        } catch {
            throw .invalidJSON
        }
    }
    
    static func dedupeByIdKeepingNewest(items: [OAKBackupHistory]) -> [OAKBackupHistory] {
        Dictionary(grouping: items, by: { $0.id })
            .compactMap { $0.value.max(by: { $0.updatedAtEpochMs < $1.updatedAtEpochMs }) }
    }
    
    private static func decompress(data: Data) -> Data? {
        ZlibDataDecoder.decompress(data)
    }
}

struct OAKBackupMeta: Codable, Sendable {
    var schemaVersion: Int
    var updatedAtEpochMs: Int64
    var deviceId: String
}

struct OAKBackupSupplement: Codable, Sendable {
    var id: String
    var name: String
    var dailyDose: String
    var intakeTime: String
    var startDate: String
    var cycle: SupplementExportCycle
    var lastTakenLocalDate: String?
    var updatedAtEpochMs: Int64
    var deletedAtEpochMs: Int64?
    var modifiedFields: Set<String>?

    fileprivate static func stableFieldKey(
        name: String,
        dailyDose: String,
        intakeTime: String,
        startDate: String,
        cycle: SupplementExportCycle
    ) -> String {
        let trimmed: (String) -> String = { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
        let anchor = trimmed(cycle.weeklyAnchorDate ?? "")
        let durationMonths = cycle.durationMonths.map(String.init) ?? ""
        let weeklyWeekdaysMask = cycle.weeklyWeekdaysMask.map(String.init) ?? ""
        let weeklyIntervalWeeks = cycle.weeklyIntervalWeeks.map(String.init) ?? ""
        let intervalDays = cycle.intervalDays.map(String.init) ?? ""
        let parts: [String] = [
            "supplement",
            trimmed(name),
            trimmed(dailyDose),
            trimmed(intakeTime),
            trimmed(startDate),
            String(cycle.isContinuous),
            String(cycle.daysOn),
            String(cycle.daysOff),
            durationMonths,
            weeklyWeekdaysMask,
            weeklyIntervalWeeks,
            intervalDays,
            anchor
        ]
        return parts.joined(separator: "|").lowercased()
    }

    enum CodingKeys: String, CodingKey {
        case id
        case name
        case dailyDose
        case intakeTime
        case startDate
        case cycle
        case lastTakenLocalDate
        case updatedAtEpochMs
        case deletedAtEpochMs
        case modifiedFields
    }

    init(
        id: String,
        name: String,
        dailyDose: String,
        intakeTime: String,
        startDate: String,
        cycle: SupplementExportCycle,
        lastTakenLocalDate: String?,
        updatedAtEpochMs: Int64,
        deletedAtEpochMs: Int64?,
        modifiedFields: Set<String>? = nil
    ) {
        self.id = id
        self.name = name
        self.dailyDose = dailyDose
        self.intakeTime = intakeTime
        self.startDate = startDate
        self.cycle = cycle
        self.lastTakenLocalDate = lastTakenLocalDate
        self.updatedAtEpochMs = updatedAtEpochMs
        self.deletedAtEpochMs = deletedAtEpochMs
        self.modifiedFields = modifiedFields
    }
    
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let name = try c.decodeIfPresent(String.self, forKey: .name) ?? ""
        let dailyDose = try c.decodeIfPresent(String.self, forKey: .dailyDose) ?? ""
        let intakeTime = try c.decodeIfPresent(String.self, forKey: .intakeTime) ?? "08:00"
        let startDate = try c.decodeIfPresent(String.self, forKey: .startDate) ?? "1970-01-01"
        let cycle = (try? c.decode(SupplementExportCycle.self, forKey: .cycle)) ?? SupplementExportCycle(isContinuous: false, daysOn: 1, daysOff: 0, durationMonths: nil, weeklyWeekdaysMask: nil, weeklyIntervalWeeks: nil, weeklyAnchorDate: nil, intervalDays: nil)
        let lastTakenLocalDate = try c.decodeIfPresent(String.self, forKey: .lastTakenLocalDate)
        let updatedAtEpochMs = try c.decodeIfPresent(Int64.self, forKey: .updatedAtEpochMs) ?? 0
        let deletedAtEpochMs = try c.decodeIfPresent(Int64.self, forKey: .deletedAtEpochMs)
        let modifiedFieldsArray = try? c.decodeIfPresent([String].self, forKey: .modifiedFields)
        let rawId = (try c.decodeIfPresent(String.self, forKey: .id) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if rawId.isEmpty {
            let key = Self.stableFieldKey(
                name: name,
                dailyDose: dailyDose,
                intakeTime: intakeTime,
                startDate: startDate,
                cycle: cycle
            )
            self.id = DoseEventKey.stableUUID(from: key).uuidString
        } else {
            self.id = rawId
        }
        self.name = name
        self.dailyDose = dailyDose
        self.intakeTime = intakeTime
        self.startDate = startDate
        self.cycle = cycle
        self.lastTakenLocalDate = lastTakenLocalDate
        self.updatedAtEpochMs = updatedAtEpochMs
        self.deletedAtEpochMs = deletedAtEpochMs
        self.modifiedFields = modifiedFieldsArray.map { Set($0) }
    }
}

struct OAKBackupHistory: Codable, Sendable {
    var id: String
    var supplementId: String
    var dateEpochMs: Int64
    var status: String
    var updatedAtEpochMs: Int64

    enum CodingKeys: String, CodingKey {
        case id
        case supplementId
        case dateEpochMs
        case status
        case updatedAtEpochMs
    }
    
    init(
        id: String,
        supplementId: String,
        dateEpochMs: Int64,
        status: String,
        updatedAtEpochMs: Int64
    ) {
        self.id = id
        self.supplementId = supplementId
        self.dateEpochMs = dateEpochMs
        self.status = status
        self.updatedAtEpochMs = updatedAtEpochMs
    }
    
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let supplementId = try c.decodeIfPresent(String.self, forKey: .supplementId) ?? ""
        let dateEpochMs = try c.decodeIfPresent(Int64.self, forKey: .dateEpochMs) ?? 0
        let status = try c.decodeIfPresent(String.self, forKey: .status) ?? "Taken"
        let updatedAtEpochMs = try c.decodeIfPresent(Int64.self, forKey: .updatedAtEpochMs) ?? 0
        let rawId = (try c.decodeIfPresent(String.self, forKey: .id) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if rawId.isEmpty {
            self.id = "\(supplementId.trimmingCharacters(in: .whitespacesAndNewlines).lowercased())-\(dateEpochMs)"
        } else {
            self.id = rawId
        }
        self.supplementId = supplementId
        self.dateEpochMs = dateEpochMs
        self.status = status
        self.updatedAtEpochMs = updatedAtEpochMs
    }
}

enum SupplementExportError: Error {
    case invalidSchema
    case invalidJSON
    case missingActiveClient
    case invalidDate
    case writeFailed
}

@MainActor
struct SupplementExportCodec {
    static func encodeBackup(
        supplements: [UserSupplement],
        records: [IntakeRecord]
    ) throws -> Data {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let deviceId = loadOrCreateDeviceId()
        let stack = makeBackupStack(from: supplements)
        let history = makeBackupHistory(from: records)
        let file = OAKBackupData(
            version: "2.0",
            meta: OAKBackupMeta(schemaVersion: 2, updatedAtEpochMs: now, deviceId: deviceId),
            stack: stack,
            history: history,
            historyZlibBase64: nil
        )
        return try JSONEncoder().encode(file)
    }

    private static func loadOrCreateDeviceId() -> String {
        let key = "cloudSyncDeviceId"
        let existing = (UserDefaults.standard.string(forKey: key) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if !existing.isEmpty { return existing }
        let created = UUID().uuidString
        UserDefaults.standard.set(created, forKey: key)
        return created
    }

    private static func makeBackupStack(from supplements: [UserSupplement]) -> [OAKBackupSupplement] {
        supplements.map { backupSupplement(from: $0) }
    }

    private static func backupSupplement(from supplement: UserSupplement) -> OAKBackupSupplement {
        let allFields: Set<String> = ["name", "dailyDose", "intakeTime", "startDate", "cycle", "lastTakenLocalDate"]
        return OAKBackupSupplement(
            id: supplement.id.uuidString,
            name: supplement.name,
            dailyDose: supplement.dailyDose,
            intakeTime: supplement.intakeTime,
            startDate: Self.dayString(from: supplement.startDate),
            cycle: SupplementExportCycle(
                isContinuous: supplement.cycleConfig.isContinuous,
                daysOn: supplement.cycleConfig.daysOn,
                daysOff: supplement.cycleConfig.daysOff,
                durationMonths: supplement.cycleConfig.durationMonths,
                weeklyWeekdaysMask: supplement.cycleConfig.weeklyRecurrence?.weekdaysMask,
                weeklyIntervalWeeks: supplement.cycleConfig.weeklyRecurrence?.intervalWeeks,
                weeklyAnchorDate: supplement.cycleConfig.weeklyRecurrence.map { Self.dayString(from: $0.anchorDate) },
                intervalDays: supplement.cycleConfig.intervalDays
            ),
            lastTakenLocalDate: supplement.lastTakenLocalDate,
            updatedAtEpochMs: supplement.updatedAtEpochMs,
            deletedAtEpochMs: supplement.deletedAtEpochMs,
            modifiedFields: allFields
        )
    }

    private static func makeBackupHistory(from records: [IntakeRecord]) -> [OAKBackupHistory] {
        let best = bestRecordsByDoseKey(records)
        return best.compactMap { (key, record) in
            guard let supplementId = record.supplement?.id else { return nil }
            return OAKBackupHistory(
                id: key,
                supplementId: supplementId.uuidString.lowercased(),
                dateEpochMs: Int64(record.date.timeIntervalSince1970 * 1000),
                status: record.status,
                updatedAtEpochMs: record.updatedAtEpochMs
            )
        }
    }

    private static func bestRecordsByDoseKey(_ records: [IntakeRecord]) -> [String: IntakeRecord] {
        var best: [String: IntakeRecord] = [:]
        for record in records {
            guard let supplementId = record.supplement?.id else { continue }
            let dateEpochMs = Int64(record.date.timeIntervalSince1970 * 1000)
            let key = DoseEventKey.make(supplementId: supplementId, scheduledAtEpochMs: dateEpochMs)
            if let existing = best[key] {
                if record.updatedAtEpochMs > existing.updatedAtEpochMs { best[key] = record }
                continue
            }
            best[key] = record
        }
        return best
    }
    
    nonisolated static func decodeBackupCompat(data: Data) throws -> OAKBackupData {
        if let decoded = tryDecodeBackupData(data: data) { return decoded }
        if let stack = tryDecodeBackupStack(data: data) {
            return OAKBackupData(version: "2.0", meta: nil, stack: stack, history: [], historyZlibBase64: nil)
        }
        return convertLegacyToBackup(try decode(data: data))
    }

    nonisolated static func decodeBackupOffMain(data: Data) async throws -> OAKBackupData {
        try await Task.detached(priority: .userInitiated) {
            try decodeBackupCompat(data: data)
        }.value
    }

    static func mergeBackupCooperatively(
        data: Data,
        client: ClientProfile,
        context: ModelContext
    ) async throws {
        let backup = try await decodeBackupOffMain(data: data)
        try await mergeBackupDataCooperatively(backup, client: client, context: context)
    }

    nonisolated private static func tryDecodeBackupData(data: Data) -> OAKBackupData? {
        try? JSONDecoder().decode(OAKBackupData.self, from: data)
    }

    nonisolated private static func tryDecodeBackupStack(data: Data) -> [OAKBackupSupplement]? {
        try? JSONDecoder().decode([OAKBackupSupplement].self, from: data)
    }

    nonisolated private static func convertLegacyToBackup(_ legacy: SupplementExportFile) -> OAKBackupData {
        OAKBackupData(
            version: "2.0",
            meta: nil,
            stack: legacy.supplements.map { legacyBackupSupplement(from: $0) },
            history: [],
            historyZlibBase64: nil
        )
    }

    nonisolated private static func legacyBackupSupplement(from dto: SupplementExportSupplement) -> OAKBackupSupplement {
        let key = OAKBackupSupplement.stableFieldKey(
            name: dto.name,
            dailyDose: dto.dailyDose,
            intakeTime: dto.intakeTime,
            startDate: dto.startDate,
            cycle: dto.cycle
        )
        return OAKBackupSupplement(
            id: DoseEventKey.stableUUID(from: key).uuidString,
            name: dto.name,
            dailyDose: dto.dailyDose,
            intakeTime: dto.intakeTime,
            startDate: dto.startDate,
            cycle: dto.cycle,
            lastTakenLocalDate: dto.lastTakenLocalDate,
            updatedAtEpochMs: 0,
            deletedAtEpochMs: nil
        )
    }
    
    static func importBackup(
        data: Data,
        client: ClientProfile,
        context: ModelContext
    ) throws {
        let backup = try decodeBackupCompat(data: data)
        try importBackupData(backup, client: client, context: context)
    }

    static func mergeBackup(
        data: Data,
        client: ClientProfile,
        context: ModelContext
    ) throws {
        let backup = try decodeBackupCompat(data: data)
        try mergeBackupDataSafely(backup, client: client, context: context)
    }
    
    static func importBackupData(_ backup: OAKBackupData, client: ClientProfile, context: ModelContext) throws {
        let allSupplements = try context.fetch(FetchDescriptor<UserSupplement>())
        let allRecords = try context.fetch(FetchDescriptor<IntakeRecord>())
        try deleteClientData(clientId: client.id, allSupplements: allSupplements, allRecords: allRecords, context: context)

        let supplementOwners = supplementOwnersById(allSupplements)
        var takenSupplementIds = Set(supplementOwners.keys)

        let allExistingRecords = try context.fetch(FetchDescriptor<IntakeRecord>())
        let recordOwners = recordOwnersById(allExistingRecords)
        var takenRecordIds = Set(recordOwners.keys)

        let (supplementById, supplementIdMap) = try importSupplements(backup: backup, client: client, ownersById: supplementOwners, taken: &takenSupplementIds, context: context)
        try context.save()

        try importRecordsBatched(backup: backup, clientId: client.id, supplementById: supplementById, supplementIdMap: supplementIdMap, recordOwners: recordOwners, takenRecordIds: &takenRecordIds, context: context)
    }

    private static func deleteClientData(
        clientId: UUID,
        allSupplements: [UserSupplement],
        allRecords: [IntakeRecord],
        context: ModelContext
    ) throws {
        for record in allRecords where record.supplement?.client?.id == clientId { context.delete(record) }
        for supplement in allSupplements where supplement.client?.id == clientId { context.delete(supplement) }
        try context.save()
    }

    private static func supplementOwnersById(_ allSupplements: [UserSupplement]) -> [UUID: UUID?] {
        Dictionary(allSupplements.map { ($0.id, $0.client?.id) }, uniquingKeysWith: { first, _ in first })
    }

    private static func recordOwnersById(_ allRecords: [IntakeRecord]) -> [UUID: UUID?] {
        Dictionary(allRecords.map { ($0.id, $0.supplement?.client?.id) }, uniquingKeysWith: { first, _ in first })
    }

    private static func importSupplements(
        backup: OAKBackupData,
        client: ClientProfile,
        ownersById: [UUID: UUID?],
        taken: inout Set<UUID>,
        context: ModelContext
    ) throws -> ([UUID: UserSupplement], [UUID: UUID]) {
        var supplementById: [UUID: UserSupplement] = [:]
        var supplementIdMap: [UUID: UUID] = [:]
        for dto in backup.stack {
            let id = resolvedImportId(rawUUIDString: dto.id, clientId: client.id, ownersById: ownersById, taken: &taken)
            if let original = UUID(uuidString: dto.id) { supplementIdMap[original] = id }
            let supplement = try makeSupplement(dto: dto, id: id, client: client)
            context.insert(supplement)
            supplementById[id] = supplement
        }
        return (supplementById, supplementIdMap)
    }

    static func mergeBackupDataSafely(_ backup: OAKBackupData, client: ClientProfile, context: ModelContext) throws {
        let supplements = try prepareSupplementMerge(backup: backup, client: client, context: context)
        guard !backup.history.isEmpty else { return }
        var state = try prepareHistoryMerge(clientId: client.id, supplements: supplements, context: context)
        try mergeHistorySafely(backup: backup, clientId: client.id, context: context, state: &state)
    }

    static func mergeBackupDataCooperatively(
        _ backup: OAKBackupData,
        client: ClientProfile,
        context: ModelContext
    ) async throws {
        let supplements = try prepareSupplementMerge(backup: backup, client: client, context: context)
        guard !backup.history.isEmpty else { return }
        await Task.yield()
        var state = try prepareHistoryMerge(clientId: client.id, supplements: supplements, context: context)
        await Task.yield()
        try await mergeHistoryCooperatively(backup: backup, clientId: client.id, context: context, state: &state)
    }

    private struct SupplementMergePreparation {
        let idMap: [UUID: UUID]
        let supplements: [UUID: UserSupplement]
    }

    private static func prepareSupplementMerge(
        backup: OAKBackupData,
        client: ClientProfile,
        context: ModelContext
    ) throws -> SupplementMergePreparation {
        let all = try context.fetch(FetchDescriptor<UserSupplement>())
        let owners = supplementOwnersById(all)
        var takenIds = Set(owners.keys)
        var idMap: [UUID: UUID] = [:]
        var supplements = supplementsForClient(clientId: client.id, allSupplements: all)
        let changes = try mergeSupplementsSafely(backup: backup, client: client, context: context, supplementOwners: owners, takenSupplementIds: &takenIds, supplementIdMap: &idMap, supplementsForClient: &supplements)
        try saveChanges(changes, context: context)
        return SupplementMergePreparation(idMap: idMap, supplements: supplements)
    }

    private static func prepareHistoryMerge(
        clientId: UUID,
        supplements: SupplementMergePreparation,
        context: ModelContext
    ) throws -> MergeBackupState {
        let all = try context.fetch(FetchDescriptor<IntakeRecord>())
        let owners = recordOwnersById(all)
        var records = recordsForClient(clientId: clientId, allRecords: all)
        let dedupe = dedupeByDoseKey(recordsForClient: &records, context: context)
        try saveChanges(dedupe.removedCount, context: context)
        return makeMergeState(supplementIdMap: supplements.idMap, supplementsForClient: supplements.supplements, recordOwners: owners, recordsForClient: records, recordsForClientByDoseKey: dedupe.recordsByDoseKey)
    }

    private struct MergeBackupState {
        var supplementIdMap: [UUID: UUID]
        var supplementsForClient: [UUID: UserSupplement]
        var recordOwners: [UUID: UUID?]
        var takenRecordIds: Set<UUID>
        var recordsForClient: [UUID: IntakeRecord]
        var recordsForClientByDoseKey: [String: IntakeRecord]
    }

    private static func makeMergeState(
        supplementIdMap: [UUID: UUID],
        supplementsForClient: [UUID: UserSupplement],
        recordOwners: [UUID: UUID?],
        recordsForClient: [UUID: IntakeRecord],
        recordsForClientByDoseKey: [String: IntakeRecord]
    ) -> MergeBackupState {
        MergeBackupState(
            supplementIdMap: supplementIdMap,
            supplementsForClient: supplementsForClient,
            recordOwners: recordOwners,
            takenRecordIds: Set(recordOwners.keys),
            recordsForClient: recordsForClient,
            recordsForClientByDoseKey: recordsForClientByDoseKey
        )
    }

    private static func supplementsForClient(clientId: UUID, allSupplements: [UserSupplement]) -> [UUID: UserSupplement] {
        Dictionary(
            allSupplements.compactMap { s in s.client?.id == clientId ? (s.id, s) : nil },
            uniquingKeysWith: { first, _ in first }
        )
    }

    private static func recordsForClient(clientId: UUID, allRecords: [IntakeRecord]) -> [UUID: IntakeRecord] {
        Dictionary(
            allRecords.compactMap { r in r.supplement?.client?.id == clientId ? (r.id, r) : nil },
            uniquingKeysWith: { first, _ in first }
        )
    }

    private static func mergeSupplementsSafely(
        backup: OAKBackupData,
        client: ClientProfile,
        context: ModelContext,
        supplementOwners: [UUID: UUID?],
        takenSupplementIds: inout Set<UUID>,
        supplementIdMap: inout [UUID: UUID],
        supplementsForClient: inout [UUID: UserSupplement]
    ) throws -> Int {
        var changeCount = 0
        for dto in backup.stack {
            let resolvedId = resolvedImportId(rawUUIDString: dto.id, clientId: client.id, ownersById: supplementOwners, taken: &takenSupplementIds)
            if let original = UUID(uuidString: dto.id) { supplementIdMap[original] = resolvedId }
            if let target = supplementsForClient[resolvedId] {
                if shouldUpdate(local: target, remote: dto) {
                    try apply(dto: dto, to: target, client: client)
                    changeCount += 1
                }
                supplementsForClient[resolvedId] = target
                continue
            }
            let created = try makeSupplement(dto: dto, id: resolvedId, client: client)
            context.insert(created)
            supplementsForClient[resolvedId] = created
            changeCount += 1
        }
        return changeCount
    }

    private static func shouldUpdate(local: UserSupplement, remote: OAKBackupSupplement) -> Bool {
        let localTs = max(local.updatedAtEpochMs, local.deletedAtEpochMs ?? 0)
        let remoteTs = max(remote.updatedAtEpochMs, remote.deletedAtEpochMs ?? 0)
        return remoteTs > localTs
    }

    private static func dedupeByDoseKey(
        recordsForClient: inout [UUID: IntakeRecord],
        context: ModelContext
    ) -> (recordsByDoseKey: [String: IntakeRecord], removedCount: Int) {
        var byDoseKey: [String: IntakeRecord] = [:]
        var removedCount = 0
        for record in Array(recordsForClient.values) {
            guard let key = recordDoseKey(record) else { continue }
            if let existing = byDoseKey[key] {
                if record.updatedAtEpochMs > existing.updatedAtEpochMs {
                    context.delete(existing)
                    recordsForClient.removeValue(forKey: existing.id)
                    byDoseKey[key] = record
                    removedCount += 1
                    continue
                }
                context.delete(record)
                recordsForClient.removeValue(forKey: record.id)
                removedCount += 1
                continue
            }
            byDoseKey[key] = record
        }
        return (byDoseKey, removedCount)
    }

    private static func recordDoseKey(_ record: IntakeRecord) -> String? {
        guard let supplementId = record.supplement?.id else { return nil }
        let dateEpochMs = Int64(record.date.timeIntervalSince1970 * 1000)
        return DoseEventKey.make(supplementId: supplementId, scheduledAtEpochMs: dateEpochMs)
    }

    private static func mergeHistorySafely(
        backup: OAKBackupData,
        clientId: UUID,
        context: ModelContext,
        state: inout MergeBackupState
    ) throws {
        let history = Array(backup.history.suffix(5_000))
        var index = 0
        while index < history.count {
            let end = min(index + 100, history.count)
            var changeCount = 0
            for dto in history[index..<end] {
                if mergeHistoryItem(dto: dto, clientId: clientId, context: context, state: &state) {
                    changeCount += 1
                }
            }
            try saveChanges(changeCount, context: context)
            index = end
        }
    }

    private static func mergeHistoryCooperatively(
        backup: OAKBackupData,
        clientId: UUID,
        context: ModelContext,
        state: inout MergeBackupState
    ) async throws {
        let history = Array(backup.history.suffix(5_000))
        var index = 0
        while index < history.count {
            let end = min(index + 100, history.count)
            var changeCount = 0
            for dto in history[index..<end] {
                if mergeHistoryItem(dto: dto, clientId: clientId, context: context, state: &state) {
                    changeCount += 1
                }
            }
            try saveChanges(changeCount, context: context)
            index = end
            await Task.yield()
        }
    }

    private static func saveChanges(_ changeCount: Int, context: ModelContext) throws {
        guard changeCount > 0 else { return }
        try context.save()
    }

    private static func mergeHistoryItem(dto: OAKBackupHistory, clientId: UUID, context: ModelContext, state: inout MergeBackupState) -> Bool {
        let originalSupplementId = UUID(uuidString: dto.supplementId)
        let resolvedSupplementId = originalSupplementId.flatMap { state.supplementIdMap[$0] ?? $0 }
        guard let resolvedSupplementId, let supplement = state.supplementsForClient[resolvedSupplementId] else { return false }

        let (remoteUpdatedAt, date, intakeTime, doseKey) = recordPayload(dto: dto, supplementId: resolvedSupplementId)
        if let found = state.recordsForClientByDoseKey[doseKey] {
            guard remoteUpdatedAt > found.updatedAtEpochMs else { return false }
            apply(dto: dto, to: found, remoteUpdatedAt: remoteUpdatedAt, date: date, intakeTime: intakeTime)
            return true
        }

        let stableRecordId = DoseEventKey.stableUUID(from: doseKey)
        let resolvedRecordId = resolvedImportId(rawUUIDString: stableRecordId.uuidString, clientId: clientId, ownersById: state.recordOwners, taken: &state.takenRecordIds)
        if let found = state.recordsForClient[resolvedRecordId] {
            guard remoteUpdatedAt > found.updatedAtEpochMs else { return false }
            apply(dto: dto, to: found, remoteUpdatedAt: remoteUpdatedAt, date: date, intakeTime: intakeTime)
            state.recordsForClientByDoseKey[doseKey] = found
            return true
        }

        let record = IntakeRecord(id: resolvedRecordId, date: date, status: dto.status, intakeTime: intakeTime, updatedAtEpochMs: remoteUpdatedAt, supplement: supplement)
        context.insert(record)
        state.recordsForClient[resolvedRecordId] = record
        state.recordsForClientByDoseKey[doseKey] = record
        return true
    }
    
    private static func resolvedImportId(
        rawUUIDString: String,
        clientId: UUID,
        ownersById: [UUID: UUID?],
        taken: inout Set<UUID>
    ) -> UUID {
        let baseKey = "\(clientId.uuidString.lowercased())|\(rawUUIDString.trimmingCharacters(in: .whitespacesAndNewlines).lowercased())"
        if let parsed = UUID(uuidString: rawUUIDString) {
            if let existingOwner = ownersById[parsed] {
                if existingOwner == clientId {
                    taken.insert(parsed)
                    return parsed
                }
                return stableAlternativeId(baseKey: baseKey, taken: &taken)
            }
            if taken.contains(parsed) { return stableAlternativeId(baseKey: baseKey, taken: &taken) }
            taken.insert(parsed)
            return parsed
        }
        return stableAlternativeId(baseKey: baseKey, taken: &taken)
    }
    
    private static func stableAlternativeId(baseKey: String, taken: inout Set<UUID>) -> UUID {
        var attempt = 0
        while true {
            let candidate = DoseEventKey.stableUUID(from: "\(baseKey)|\(attempt)")
            if !taken.contains(candidate) {
                taken.insert(candidate)
                return candidate
            }
            attempt += 1
        }
    }

    private static func importRecordsBatched(
        backup: OAKBackupData,
        clientId: UUID,
        supplementById: [UUID: UserSupplement],
        supplementIdMap: [UUID: UUID],
        recordOwners: [UUID: UUID?],
        takenRecordIds: inout Set<UUID>,
        context: ModelContext
    ) throws {
        let history = Array(backup.history.suffix(5_000))
        var index = 0
        while index < history.count {
            let end = min(index + 500, history.count)
            for dto in history[index..<end] {
                importHistoryRecord(
                    dto: dto,
                    clientId: clientId,
                    supplementById: supplementById,
                    supplementIdMap: supplementIdMap,
                    recordOwners: recordOwners,
                    takenRecordIds: &takenRecordIds,
                    context: context
                )
            }
            try context.save()
            index = end
        }
    }

    private static func importHistoryRecord(
        dto: OAKBackupHistory,
        clientId: UUID,
        supplementById: [UUID: UserSupplement],
        supplementIdMap: [UUID: UUID],
        recordOwners: [UUID: UUID?],
        takenRecordIds: inout Set<UUID>,
        context: ModelContext
    ) {
        let supplementId = UUID(uuidString: dto.supplementId).flatMap { supplementIdMap[$0] ?? $0 }
        guard let supplementId, let supplement = supplementById[supplementId] else { return }
        let (remoteUpdatedAt, date, intakeTime, doseKey) = recordPayload(dto: dto, supplementId: supplementId)
        let stableRecordId = DoseEventKey.stableUUID(from: doseKey)
        let recordId = resolvedImportId(rawUUIDString: stableRecordId.uuidString, clientId: clientId, ownersById: recordOwners, taken: &takenRecordIds)
        let record = IntakeRecord(id: recordId, date: date, status: dto.status, intakeTime: intakeTime, updatedAtEpochMs: remoteUpdatedAt, supplement: supplement)
        context.insert(record)
    }

    private static func makeSupplement(
        dto: OAKBackupSupplement,
        id: UUID,
        client: ClientProfile
    ) throws -> UserSupplement {
        UserSupplement(
            id: id,
            name: dto.name,
            startDate: try dayDate(from: dto.startDate),
            cycleConfig: cycleConfig(from: dto.cycle),
            dailyDose: dto.dailyDose,
            intakeTime: dto.intakeTime,
            lastTakenLocalDate: dto.lastTakenLocalDate,
            updatedAtEpochMs: dto.updatedAtEpochMs,
            deletedAtEpochMs: dto.deletedAtEpochMs,
            client: client
        )
    }
    
    private static func apply(
        dto: OAKBackupSupplement,
        to supplement: UserSupplement,
        client: ClientProfile
    ) throws {
        let fields = dto.modifiedFields
        if fields == nil || fields!.contains("name") { supplement.name = dto.name }
        if fields == nil || fields!.contains("startDate") { supplement.startDate = try dayDate(from: dto.startDate) }
        if fields == nil || fields!.contains("cycle") { supplement.cycleConfig = cycleConfig(from: dto.cycle) }
        if fields == nil || fields!.contains("dailyDose") { supplement.dailyDose = dto.dailyDose }
        if fields == nil || fields!.contains("intakeTime") { supplement.intakeTime = dto.intakeTime }
        if fields == nil || fields!.contains("lastTakenLocalDate") { supplement.lastTakenLocalDate = dto.lastTakenLocalDate }
        supplement.updatedAtEpochMs = dto.updatedAtEpochMs
        supplement.deletedAtEpochMs = dto.deletedAtEpochMs
        supplement.client = client
    }

    private static func recordPayload(dto: OAKBackupHistory, supplementId: UUID) -> (Int64, Date, String, String) {
        let remoteUpdatedAt = dto.updatedAtEpochMs > 0 ? dto.updatedAtEpochMs : dto.dateEpochMs
        let date = Date(timeIntervalSince1970: Double(dto.dateEpochMs) / 1000.0)
        let intakeTime = DoseEventKey.intakeTimeString(from: date)
        let key = DoseEventKey.make(supplementId: supplementId, scheduledAtEpochMs: dto.dateEpochMs)
        return (remoteUpdatedAt, date, intakeTime, key)
    }

    private static func apply(dto: OAKBackupHistory, to record: IntakeRecord, remoteUpdatedAt: Int64, date: Date, intakeTime: String) {
        record.status = dto.status
        record.date = date
        record.intakeTime = intakeTime
        record.updatedAtEpochMs = remoteUpdatedAt
    }

    static func encode(supplements: [UserSupplement]) throws -> Data {
        let file = SupplementExportFile(
            schemaVersion: 1,
            exportedAtEpochMs: Int64(Date().timeIntervalSince1970 * 1000),
            supplements: supplements.map { supplement in
                SupplementExportSupplement(
                    name: supplement.name,
                    dailyDose: supplement.dailyDose,
                    intakeTime: supplement.intakeTime,
                    startDate: Self.dayString(from: supplement.startDate),
                    category: nil,
                    cycle: SupplementExportCycle(
                        isContinuous: supplement.cycleConfig.isContinuous,
                        daysOn: supplement.cycleConfig.daysOn,
                        daysOff: supplement.cycleConfig.daysOff,
                        durationMonths: supplement.cycleConfig.durationMonths,
                        weeklyWeekdaysMask: supplement.cycleConfig.weeklyRecurrence?.weekdaysMask,
                        weeklyIntervalWeeks: supplement.cycleConfig.weeklyRecurrence?.intervalWeeks,
                        weeklyAnchorDate: supplement.cycleConfig.weeklyRecurrence.map { Self.dayString(from: $0.anchorDate) },
                        intervalDays: supplement.cycleConfig.intervalDays
                    ),
                    lastTakenLocalDate: supplement.lastTakenLocalDate
                )
            }
        )
        return try JSONEncoder().encode(file)
    }
    
    nonisolated static func decode(data: Data) throws -> SupplementExportFile {
        let file: SupplementExportFile
        do {
            file = try JSONDecoder().decode(SupplementExportFile.self, from: data)
        } catch {
            throw SupplementExportError.invalidJSON
        }
        guard file.schemaVersion == 1 else { throw SupplementExportError.invalidSchema }
        return file
    }
    
    static func importFile(
        _ file: SupplementExportFile,
        client: ClientProfile,
        context: ModelContext
    ) throws {
        for dto in file.supplements {
            let existing = try findSupplement(named: dto.name, for: client, context: context)
            let target = try existing ?? createSupplement(from: dto, client: client)
            if existing == nil { context.insert(target) }
            try apply(dto: dto, to: target, client: client)
        }
        try context.save()
    }
    
    static func renderShareImageData(
        supplements: [UserSupplement],
        colorScheme: ColorScheme
    ) throws -> Data {
        let items = makeShareItems(from: supplements)
        let renderer = ImageRenderer(
            content: StackShareSnapshotView(items: items)
                .environment(\.colorScheme, colorScheme)
        )
        renderer.scale = 3
        guard let cgImage = renderer.cgImage else { throw SupplementExportError.writeFailed }
        return try pngData(from: cgImage)
    }
    
    private static func findSupplement(
        named name: String,
        for client: ClientProfile,
        context: ModelContext
    ) throws -> UserSupplement? {
        let all = try context.fetch(FetchDescriptor<UserSupplement>())
        return all.first { $0.client?.id == client.id && $0.name == name }
    }
    
    private static func createSupplement(
        from dto: SupplementExportSupplement,
        client: ClientProfile
    ) throws -> UserSupplement {
        UserSupplement(
            name: dto.name,
            startDate: try dayDate(from: dto.startDate),
            cycleConfig: cycleConfig(from: dto.cycle),
            dailyDose: dto.dailyDose,
            intakeTime: dto.intakeTime,
            lastTakenLocalDate: dto.lastTakenLocalDate,
            client: client
        )
    }
    
    private static func apply(
        dto: SupplementExportSupplement,
        to supplement: UserSupplement,
        client: ClientProfile
    ) throws {
        supplement.name = dto.name
        supplement.dailyDose = dto.dailyDose
        supplement.intakeTime = dto.intakeTime
        supplement.startDate = try dayDate(from: dto.startDate)
        supplement.cycleConfig = cycleConfig(from: dto.cycle)
        supplement.lastTakenLocalDate = dto.lastTakenLocalDate
        supplement.client = client
    }
    
    private static func cycleConfig(from dto: SupplementExportCycle) -> CycleConfig {
        let weekly: WeeklyRecurrenceConfig? = {
            guard let mask = dto.weeklyWeekdaysMask else { return nil }
            guard let interval = dto.weeklyIntervalWeeks else { return nil }
            guard let anchorString = dto.weeklyAnchorDate else { return nil }
            guard let anchorDate = try? dayDate(from: anchorString) else { return nil }
            return WeeklyRecurrenceConfig(weekdaysMask: mask, intervalWeeks: interval, anchorDate: anchorDate)
        }()
        return CycleConfig(
            daysOn: dto.daysOn,
            daysOff: dto.daysOff,
            isContinuous: dto.isContinuous,
            durationMonths: dto.durationMonths,
            weeklyRecurrence: weekly,
            intervalDays: dto.intervalDays
        )
    }
    
    private static func makeShareItems(from supplements: [UserSupplement]) -> [StackShareItem] {
        supplements
            .sorted { $0.intakeTime < $1.intakeTime }
            .map { StackShareItem(name: $0.name, dose: $0.dailyDose, time: $0.intakeTime) }
    }
    
    private static func pngData(from cgImage: CGImage) throws -> Data {
        let data = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(data, UTType.png.identifier as CFString, 1, nil) else {
            throw SupplementExportError.writeFailed
        }
        CGImageDestinationAddImage(destination, cgImage, nil)
        guard CGImageDestinationFinalize(destination) else { throw SupplementExportError.writeFailed }
        return data as Data
    }
    
    private static func dayString(from date: Date) -> String {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: date)
    }
    
    private static func dayDate(from string: String) throws -> Date {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        guard let date = formatter.date(from: string) else { throw SupplementExportError.invalidDate }
        return date
    }
}

private struct StackShareItem: Identifiable, Sendable {
    let id = UUID()
    let name: String
    let dose: String
    let time: String
}

private struct StackShareSnapshotView: View {
    let items: [StackShareItem]
    
    @Environment(\.colorScheme) private var colorScheme
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("app_name".localized)
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundStyle(.primary)
                Spacer()
            }
            
            VStack(alignment: .leading, spacing: 10) {
                ForEach(items) { item in
                    HStack(alignment: .firstTextBaseline) {
                        Text(item.time)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .frame(width: 52, alignment: .leading)
                        
                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.name)
                                .font(.headline)
                                .foregroundStyle(.primary)
                            Text(item.dose)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                    }
                }
            }
        }
        .padding(18)
        .background(cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay(
            RoundedRectangle(cornerRadius: 18)
                .stroke(cardBorder, lineWidth: 1)
        )
        .shadow(color: cardShadow, radius: 16, x: 0, y: 10)
        .padding(18)
        .frame(width: 400, alignment: .center)
        .background(gradientBackground)
    }
    
    private var gradientBackground: some View {
        Rectangle()
            .fill(
                LinearGradient(
                    colors: gradientColors,
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            )
    }
    
    private var gradientColors: [Color] {
        switch colorScheme {
        case .dark:
            return [Color(white: 0.1), Color(white: 0.05)]
        default:
            return [Color(white: 0.95), Color(white: 0.90)]
        }
    }
    
    private var cardBackground: Color {
        switch colorScheme {
        case .dark:
            return Color(white: 0.12)
        default:
            return .white
        }
    }
    
    private var cardBorder: Color {
        switch colorScheme {
        case .dark:
            return Color.white.opacity(0.10)
        default:
            return Color.black.opacity(0.06)
        }
    }
    
    private var cardShadow: Color {
        switch colorScheme {
        case .dark:
            return Color.black.opacity(0.50)
        default:
            return Color.black.opacity(0.12)
        }
    }
}
