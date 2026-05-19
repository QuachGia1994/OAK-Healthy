import Compression
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
}

struct SupplementExportCycle: Codable, Sendable {
    var isContinuous: Bool
    var daysOn: Int
    var daysOff: Int
    var durationMonths: Int?
    var weeklyWeekdaysMask: Int?
    var weeklyIntervalWeeks: Int?
    var weeklyAnchorDate: String?

    enum CodingKeys: String, CodingKey {
        case isContinuous
        case daysOn
        case daysOff
        case durationMonths
        case weeklyWeekdaysMask
        case weeklyIntervalWeeks
        case weeklyAnchorDate
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
        if let historyZlibBase64, let inflated = ZlibBase64Codec.decodeArray(base64: historyZlibBase64) {
            mergedHistory.append(contentsOf: inflated)
        }
        self.history = ZlibBase64Codec.dedupeByIdKeepingNewest(items: mergedHistory)
    }
}

enum ZlibBase64Codec {
    private static let threshold = 200
    
    static func encodeIfLarge<T: Encodable>(items: [T]) -> String? {
        guard items.count > threshold else { return nil }
        guard let data = try? JSONEncoder().encode(items) else { return nil }
        guard let compressed = compress(data: data) else { return nil }
        return compressed.base64EncodedString()
    }
    
    static func decodeArray(base64: String) -> [OAKBackupHistory]? {
        let trimmed = base64.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, let raw = Data(base64Encoded: trimmed) else { return nil }
        guard let inflated = decompress(data: raw) else { return nil }
        return try? JSONDecoder().decode([OAKBackupHistory].self, from: inflated)
    }
    
    static func dedupeByIdKeepingNewest(items: [OAKBackupHistory]) -> [OAKBackupHistory] {
        Dictionary(grouping: items, by: { $0.id })
            .compactMap { $0.value.max(by: { $0.updatedAtEpochMs < $1.updatedAtEpochMs }) }
    }
    
    private static func compress(data: Data) -> Data? {
        process(data: data, operation: COMPRESSION_STREAM_ENCODE)
    }
    
    private static func decompress(data: Data) -> Data? {
        process(data: data, operation: COMPRESSION_STREAM_DECODE)
    }
    
    private static func process(data: Data, operation: compression_stream_operation) -> Data? {
        let bufferSize = 64 * 1024
        var stream = compression_stream(dst_ptr: nil, dst_size: 0, src_ptr: nil, src_size: 0, state: nil)
        guard compression_stream_init(&stream, operation, COMPRESSION_ZLIB) != COMPRESSION_STATUS_ERROR else { return nil }
        defer { compression_stream_destroy(&stream) }
        return data.withUnsafeBytes { (srcPtr: UnsafeRawBufferPointer) -> Data? in
            guard let srcBase = srcPtr.bindMemory(to: UInt8.self).baseAddress else { return nil }
            var dst = Data()
            stream.src_ptr = srcBase
            stream.src_size = data.count
            let dstBuffer = UnsafeMutablePointer<UInt8>.allocate(capacity: bufferSize)
            defer { dstBuffer.deallocate() }
            while true {
                stream.dst_ptr = dstBuffer
                stream.dst_size = bufferSize
                let status = compression_stream_process(&stream, 0)
                let written = bufferSize - stream.dst_size
                if written > 0 { dst.append(dstBuffer, count: written) }
                if status == COMPRESSION_STATUS_END { return dst }
                if status == COMPRESSION_STATUS_ERROR { return nil }
            }
        }
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
    var updatedAtEpochMs: Int64
    var deletedAtEpochMs: Int64?

    enum CodingKeys: String, CodingKey {
        case id
        case name
        case dailyDose
        case intakeTime
        case startDate
        case cycle
        case updatedAtEpochMs
        case deletedAtEpochMs
    }
    
    init(
        id: String,
        name: String,
        dailyDose: String,
        intakeTime: String,
        startDate: String,
        cycle: SupplementExportCycle,
        updatedAtEpochMs: Int64,
        deletedAtEpochMs: Int64?
    ) {
        self.id = id
        self.name = name
        self.dailyDose = dailyDose
        self.intakeTime = intakeTime
        self.startDate = startDate
        self.cycle = cycle
        self.updatedAtEpochMs = updatedAtEpochMs
        self.deletedAtEpochMs = deletedAtEpochMs
    }
    
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decodeIfPresent(String.self, forKey: .id) ?? UUID().uuidString
        self.name = try c.decodeIfPresent(String.self, forKey: .name) ?? ""
        self.dailyDose = try c.decodeIfPresent(String.self, forKey: .dailyDose) ?? ""
        self.intakeTime = try c.decodeIfPresent(String.self, forKey: .intakeTime) ?? "08:00"
        self.startDate = try c.decodeIfPresent(String.self, forKey: .startDate) ?? "1970-01-01"
        self.cycle = (try? c.decode(SupplementExportCycle.self, forKey: .cycle)) ?? SupplementExportCycle(isContinuous: false, daysOn: 1, daysOff: 0, durationMonths: nil, weeklyWeekdaysMask: nil, weeklyIntervalWeeks: nil, weeklyAnchorDate: nil)
        self.updatedAtEpochMs = try c.decodeIfPresent(Int64.self, forKey: .updatedAtEpochMs) ?? 0
        self.deletedAtEpochMs = try c.decodeIfPresent(Int64.self, forKey: .deletedAtEpochMs)
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
        self.id = try c.decodeIfPresent(String.self, forKey: .id) ?? UUID().uuidString
        self.supplementId = try c.decodeIfPresent(String.self, forKey: .supplementId) ?? ""
        self.dateEpochMs = try c.decodeIfPresent(Int64.self, forKey: .dateEpochMs) ?? 0
        self.status = try c.decodeIfPresent(String.self, forKey: .status) ?? "Taken"
        self.updatedAtEpochMs = try c.decodeIfPresent(Int64.self, forKey: .updatedAtEpochMs) ?? 0
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
        let deviceId = {
            let key = "cloudSyncDeviceId"
            let existing = (UserDefaults.standard.string(forKey: key) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            if !existing.isEmpty { return existing }
            let created = UUID().uuidString
            UserDefaults.standard.set(created, forKey: key)
            return created
        }()
        let stack = supplements.map { supplement in
                OAKBackupSupplement(
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
                        weeklyAnchorDate: supplement.cycleConfig.weeklyRecurrence.map { Self.dayString(from: $0.anchorDate) }
                    ),
                    updatedAtEpochMs: supplement.updatedAtEpochMs,
                    deletedAtEpochMs: supplement.deletedAtEpochMs
                )
            }
        let history: [OAKBackupHistory] = records.compactMap { record in
            guard let supplementId = record.supplement?.id else { return nil }
            return OAKBackupHistory(
                id: record.id.uuidString,
                supplementId: supplementId.uuidString,
                dateEpochMs: Int64(record.date.timeIntervalSince1970 * 1000),
                status: record.status,
                updatedAtEpochMs: record.updatedAtEpochMs
            )
        }
        let historyZlibBase64: String? = ZlibBase64Codec.encodeIfLarge(items: history)
        let file = OAKBackupData(
            version: "2.0",
            meta: OAKBackupMeta(schemaVersion: 2, updatedAtEpochMs: now, deviceId: deviceId),
            stack: stack,
            history: historyZlibBase64 == nil ? history : [],
            historyZlibBase64: historyZlibBase64
        )
        return try JSONEncoder().encode(file)
    }
    
    static func decodeBackupCompat(data: Data) throws -> OAKBackupData {
        if let decoded = try? JSONDecoder().decode(OAKBackupData.self, from: data) {
            return decoded
        }

        if let stack = try? JSONDecoder().decode([OAKBackupSupplement].self, from: data) {
            return OAKBackupData(version: "2.0", meta: nil, stack: stack, history: [], historyZlibBase64: nil)
        }
        
        let legacy = try decode(data: data)
        let converted = OAKBackupData(
            version: "2.0",
            meta: nil,
            stack: legacy.supplements.map { dto in
                OAKBackupSupplement(
                    id: UUID().uuidString,
                    name: dto.name,
                    dailyDose: dto.dailyDose,
                    intakeTime: dto.intakeTime,
                    startDate: dto.startDate,
                    cycle: dto.cycle,
                    updatedAtEpochMs: 0,
                    deletedAtEpochMs: nil
                )
            },
            history: [],
            historyZlibBase64: nil
        )
        return converted
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
    
    static func importBackupData(
        _ backup: OAKBackupData,
        client: ClientProfile,
        context: ModelContext
    ) throws {
        let allSupplements = try context.fetch(FetchDescriptor<UserSupplement>())
        let supplementsForClient = allSupplements.filter { $0.client?.id == client.id }
        
        let allRecords = try context.fetch(FetchDescriptor<IntakeRecord>())
        let recordsForClient = allRecords.filter { $0.supplement?.client?.id == client.id }
        
        for record in recordsForClient {
            context.delete(record)
        }
        for supplement in supplementsForClient {
            context.delete(supplement)
        }
        try context.save()
        
        let supplementOwners: [UUID: UUID?] = Dictionary(
            allSupplements.map { ($0.id, $0.client?.id) },
            uniquingKeysWith: { first, _ in first }
        )
        var takenSupplementIds = Set(supplementOwners.keys)
        
        let allExistingRecords = try context.fetch(FetchDescriptor<IntakeRecord>())
        let recordOwners: [UUID: UUID?] = Dictionary(
            allExistingRecords.map { ($0.id, $0.supplement?.client?.id) },
            uniquingKeysWith: { first, _ in first }
        )
        var takenRecordIds = Set(recordOwners.keys)
        
        var supplementById: [UUID: UserSupplement] = [:]
        var supplementIdMap: [UUID: UUID] = [:]
        for dto in backup.stack {
            let id = resolvedImportId(
                rawUUIDString: dto.id,
                clientId: client.id,
                ownersById: supplementOwners,
                taken: &takenSupplementIds
            )
            if let original = UUID(uuidString: dto.id) { supplementIdMap[original] = id }
            let supplement = UserSupplement(
                id: id,
                name: dto.name,
                startDate: try dayDate(from: dto.startDate),
                cycleConfig: cycleConfig(from: dto.cycle),
                dailyDose: dto.dailyDose,
                intakeTime: dto.intakeTime,
                updatedAtEpochMs: dto.updatedAtEpochMs,
                deletedAtEpochMs: dto.deletedAtEpochMs,
                client: client
            )
            context.insert(supplement)
            supplementById[id] = supplement
        }
        try context.save()
        
        try importRecordsBatched(
            backup: backup,
            clientId: client.id,
            supplementById: supplementById,
            supplementIdMap: supplementIdMap,
            recordOwners: recordOwners,
            takenRecordIds: &takenRecordIds,
            context: context
        )
    }

    static func mergeBackupData(
        _ backup: OAKBackupData,
        client: ClientProfile,
        context: ModelContext
    ) throws {
        let existingSupplements = try supplementsById(clientId: client.id, context: context)
        let existingRecords = try recordsById(clientId: client.id, context: context)
        let supplementById = try upsertSupplements(backup: backup, client: client, existing: existingSupplements, context: context)
        upsertRecords(backup: backup, supplementById: supplementById, existing: existingRecords, context: context)
        try context.save()
    }
    
    static func mergeBackupDataSafely(
        _ backup: OAKBackupData,
        client: ClientProfile,
        context: ModelContext
    ) throws {
        let allSupplements = try context.fetch(FetchDescriptor<UserSupplement>())
        let supplementOwners: [UUID: UUID?] = Dictionary(
            allSupplements.map { ($0.id, $0.client?.id) },
            uniquingKeysWith: { first, _ in first }
        )
        var takenSupplementIds = Set(supplementOwners.keys)
        
        let allRecords = try context.fetch(FetchDescriptor<IntakeRecord>())
        let recordOwners: [UUID: UUID?] = Dictionary(
            allRecords.map { ($0.id, $0.supplement?.client?.id) },
            uniquingKeysWith: { first, _ in first }
        )
        var takenRecordIds = Set(recordOwners.keys)
        
        var supplementIdMap: [UUID: UUID] = [:]
        var supplementsForClient: [UUID: UserSupplement] = Dictionary(
            allSupplements.compactMap { s in
                guard s.client?.id == client.id else { return nil }
                return (s.id, s)
            },
            uniquingKeysWith: { first, _ in first }
        )
        
        for dto in backup.stack {
            let resolvedId = resolvedImportId(
                rawUUIDString: dto.id,
                clientId: client.id,
                ownersById: supplementOwners,
                taken: &takenSupplementIds
            )
            if let original = UUID(uuidString: dto.id) { supplementIdMap[original] = resolvedId }
            
            if let target = supplementsForClient[resolvedId] {
                let localTs = max(target.updatedAtEpochMs, target.deletedAtEpochMs ?? 0)
                let remoteTs = max(dto.updatedAtEpochMs, dto.deletedAtEpochMs ?? 0)
                guard remoteTs > localTs else { continue }
                try apply(dto: dto, to: target, client: client)
                supplementsForClient[resolvedId] = target
                continue
            }
            
            let created = try makeSupplement(dto: dto, id: resolvedId, client: client)
            context.insert(created)
            supplementsForClient[resolvedId] = created
        }
        
        try context.save()
        
        var recordsForClient: [UUID: IntakeRecord] = Dictionary(
            allRecords.compactMap { r in
                guard r.supplement?.client?.id == client.id else { return nil }
                return (r.id, r)
            },
            uniquingKeysWith: { first, _ in first }
        )
        
        let history = Array(backup.history.suffix(5_000))
        var index = 0
        while index < history.count {
            let end = min(index + 500, history.count)
            for dto in history[index..<end] {
                let resolvedRecordId = resolvedImportId(
                    rawUUIDString: dto.id,
                    clientId: client.id,
                    ownersById: recordOwners,
                    taken: &takenRecordIds
                )
                if let found = recordsForClient[resolvedRecordId] {
                    guard dto.updatedAtEpochMs > found.updatedAtEpochMs else { continue }
                    found.status = dto.status
                    found.date = Date(timeIntervalSince1970: Double(dto.dateEpochMs) / 1000.0)
                    found.updatedAtEpochMs = dto.updatedAtEpochMs
                    continue
                }
                
                let originalSupplementId = UUID(uuidString: dto.supplementId)
                let resolvedSupplementId = originalSupplementId.flatMap { supplementIdMap[$0] ?? $0 }
                guard let resolvedSupplementId, let supplement = supplementsForClient[resolvedSupplementId] else { continue }
                
                let date = Date(timeIntervalSince1970: Double(dto.dateEpochMs) / 1000.0)
                let record = IntakeRecord(id: resolvedRecordId, date: date, status: dto.status, updatedAtEpochMs: dto.updatedAtEpochMs, supplement: supplement)
                context.insert(record)
                recordsForClient[resolvedRecordId] = record
            }
            try context.save()
            index = end
        }
    }
    
    private static func resolvedImportId(
        rawUUIDString: String,
        clientId: UUID,
        ownersById: [UUID: UUID?],
        taken: inout Set<UUID>
    ) -> UUID {
        if let parsed = UUID(uuidString: rawUUIDString) {
            if let existingOwner = ownersById[parsed] {
                if existingOwner == clientId { return parsed }
                return uniqueId(avoiding: &taken)
            }
            if taken.contains(parsed) { return uniqueId(avoiding: &taken) }
            taken.insert(parsed)
            return parsed
        }
        return uniqueId(avoiding: &taken)
    }
    
    private static func uniqueId(avoiding taken: inout Set<UUID>) -> UUID {
        var id = UUID()
        while taken.contains(id) { id = UUID() }
        taken.insert(id)
        return id
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
                let recordId = resolvedImportId(
                    rawUUIDString: dto.id,
                    clientId: clientId,
                    ownersById: recordOwners,
                    taken: &takenRecordIds
                )
                let supplementId = UUID(uuidString: dto.supplementId).flatMap { supplementIdMap[$0] ?? $0 }
                guard let supplementId, let supplement = supplementById[supplementId] else { continue }
                let date = Date(timeIntervalSince1970: Double(dto.dateEpochMs) / 1000.0)
                let record = IntakeRecord(id: recordId, date: date, status: dto.status, updatedAtEpochMs: dto.updatedAtEpochMs, supplement: supplement)
                context.insert(record)
            }
            try context.save()
            index = end
        }
    }

    private static func supplementsById(
        clientId: UUID,
        context: ModelContext
    ) throws -> [UUID: UserSupplement] {
        let all = try context.fetch(FetchDescriptor<UserSupplement>())
        return Dictionary(all.compactMap { s in
            guard s.client?.id == clientId else { return nil }
            return (s.id, s)
        }, uniquingKeysWith: { first, _ in first })
    }

    private static func recordsById(
        clientId: UUID,
        context: ModelContext
    ) throws -> [UUID: IntakeRecord] {
        let all = try context.fetch(FetchDescriptor<IntakeRecord>())
        let filtered = all.filter { $0.supplement?.client?.id == clientId }
        return Dictionary(filtered.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
    }

    private static func upsertSupplements(
        backup: OAKBackupData,
        client: ClientProfile,
        existing: [UUID: UserSupplement],
        context: ModelContext
    ) throws -> [UUID: UserSupplement] {
        var result = existing
        for dto in backup.stack {
            let id = UUID(uuidString: dto.id) ?? UUID()
            if let target = result[id] {
                let localTs = max(target.updatedAtEpochMs, target.deletedAtEpochMs ?? 0)
                let remoteTs = max(dto.updatedAtEpochMs, dto.deletedAtEpochMs ?? 0)
                if remoteTs > localTs { try apply(dto: dto, to: target, client: client) }
                result[id] = target
                continue
            }
            
            let created = try makeSupplement(dto: dto, id: id, client: client)
            context.insert(created)
            result[id] = created
        }
        return result
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
        supplement.name = dto.name
        supplement.startDate = try dayDate(from: dto.startDate)
        supplement.cycleConfig = cycleConfig(from: dto.cycle)
        supplement.dailyDose = dto.dailyDose
        supplement.intakeTime = dto.intakeTime
        supplement.updatedAtEpochMs = dto.updatedAtEpochMs
        supplement.deletedAtEpochMs = dto.deletedAtEpochMs
        supplement.client = client
    }

    private static func upsertRecords(
        backup: OAKBackupData,
        supplementById: [UUID: UserSupplement],
        existing: [UUID: IntakeRecord],
        context: ModelContext
    ) {
        for dto in backup.history {
            let recordId = UUID(uuidString: dto.id) ?? UUID()
            if let found = existing[recordId] {
                guard dto.updatedAtEpochMs > found.updatedAtEpochMs else { continue }
                found.status = dto.status
                found.date = Date(timeIntervalSince1970: Double(dto.dateEpochMs) / 1000.0)
                found.updatedAtEpochMs = dto.updatedAtEpochMs
                continue
            }
            let supplementId = UUID(uuidString: dto.supplementId)
            guard let supplementId, let supplement = supplementById[supplementId] else { continue }
            let date = Date(timeIntervalSince1970: Double(dto.dateEpochMs) / 1000.0)
            let record = IntakeRecord(id: recordId, date: date, status: dto.status, updatedAtEpochMs: dto.updatedAtEpochMs, supplement: supplement)
            context.insert(record)
        }
    }
    
    private static func upsertRecordsBatched(
        backup: OAKBackupData,
        supplementById: [UUID: UserSupplement],
        existing: inout [UUID: IntakeRecord],
        context: ModelContext
    ) throws {
        let history = Array(backup.history.suffix(5_000))
        var index = 0
        while index < history.count {
            let end = min(index + 500, history.count)
            for dto in history[index..<end] {
                let recordId = UUID(uuidString: dto.id) ?? UUID()
                if let found = existing[recordId] {
                    found.status = dto.status
                    found.date = Date(timeIntervalSince1970: Double(dto.dateEpochMs) / 1000.0)
                    continue
                }
                let supplementId = UUID(uuidString: dto.supplementId)
                guard let supplementId, let supplement = supplementById[supplementId] else { continue }
                let date = Date(timeIntervalSince1970: Double(dto.dateEpochMs) / 1000.0)
                let record = IntakeRecord(id: recordId, date: date, status: dto.status, updatedAtEpochMs: dto.updatedAtEpochMs, supplement: supplement)
                context.insert(record)
                existing[recordId] = record
            }
            try context.save()
            index = end
        }
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
                        weeklyAnchorDate: supplement.cycleConfig.weeklyRecurrence.map { Self.dayString(from: $0.anchorDate) }
                    )
                )
            }
        )
        return try JSONEncoder().encode(file)
    }
    
    static func decode(data: Data) throws -> SupplementExportFile {
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
            weeklyRecurrence: weekly
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
