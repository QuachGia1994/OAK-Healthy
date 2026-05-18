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
    var stack: [OAKBackupSupplement]
    var history: [OAKBackupHistory]

    enum CodingKeys: String, CodingKey {
        case version
        case stack = "supplements"
        case history = "historyLogs"
    }

    enum LegacyCodingKeys: String, CodingKey {
        case stack
        case history
    }

    init(version: String, stack: [OAKBackupSupplement], history: [OAKBackupHistory]) {
        self.version = version
        self.stack = stack
        self.history = history
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let legacyContainer = try decoder.container(keyedBy: LegacyCodingKeys.self)

        self.version = try container.decodeIfPresent(String.self, forKey: .version) ?? "1.1"

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

        if let historyLogs = try container.decodeIfPresent([OAKBackupHistory].self, forKey: .history) {
            self.history = historyLogs
        } else if let legacyHistory = try legacyContainer.decodeIfPresent([OAKBackupHistory].self, forKey: .history) {
            self.history = legacyHistory
        } else {
            self.history = []
        }
    }
}

struct OAKBackupSupplement: Codable, Sendable {
    var id: String
    var name: String
    var dailyDose: String
    var intakeTime: String
    var startDate: String
    var cycle: SupplementExportCycle

    enum CodingKeys: String, CodingKey {
        case id
        case name
        case dailyDose
        case intakeTime
        case startDate
        case cycle
    }
}

struct OAKBackupHistory: Codable, Sendable {
    var id: String
    var supplementId: String
    var dateEpochMs: Int64
    var status: String

    enum CodingKeys: String, CodingKey {
        case id
        case supplementId
        case dateEpochMs
        case status
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
        let file = OAKBackupData(
            version: "1.1",
            stack: supplements.map { supplement in
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
                    )
                )
            },
            history: records.compactMap { record in
                guard let supplementId = record.supplement?.id else { return nil }
                return OAKBackupHistory(
                    id: record.id.uuidString,
                    supplementId: supplementId.uuidString,
                    dateEpochMs: Int64(record.date.timeIntervalSince1970 * 1000),
                    status: record.status
                )
            }
        )
        return try JSONEncoder().encode(file)
    }
    
    static func decodeBackupCompat(data: Data) throws -> OAKBackupData {
        if let decoded = try? JSONDecoder().decode(OAKBackupData.self, from: data) {
            return decoded
        }

        if let stack = try? JSONDecoder().decode([OAKBackupSupplement].self, from: data) {
            return OAKBackupData(version: "1.1", stack: stack, history: [])
        }
        
        let legacy = try decode(data: data)
        let converted = OAKBackupData(
            version: "1.1",
            stack: legacy.supplements.map { dto in
                OAKBackupSupplement(
                    id: UUID().uuidString,
                    name: dto.name,
                    dailyDose: dto.dailyDose,
                    intakeTime: dto.intakeTime,
                    startDate: dto.startDate,
                    cycle: dto.cycle
                )
            },
            history: []
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
        
        var supplementById: [UUID: UserSupplement] = [:]
        for dto in backup.stack {
            let id = UUID(uuidString: dto.id) ?? UUID()
            let supplement = UserSupplement(
                id: id,
                name: dto.name,
                startDate: try dayDate(from: dto.startDate),
                cycleConfig: cycleConfig(from: dto.cycle),
                dailyDose: dto.dailyDose,
                intakeTime: dto.intakeTime,
                client: client
            )
            context.insert(supplement)
            supplementById[id] = supplement
        }
        
        for dto in backup.history {
            let recordId = UUID(uuidString: dto.id) ?? UUID()
            let supplementId = UUID(uuidString: dto.supplementId)
            guard let supplementId, let supplement = supplementById[supplementId] else { continue }
            let date = Date(timeIntervalSince1970: Double(dto.dateEpochMs) / 1000.0)
            let record = IntakeRecord(id: recordId, date: date, status: dto.status, supplement: supplement)
            context.insert(record)
        }
        
        try context.save()
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
        let existingSupplements = try supplementsById(clientId: client.id, context: context)
        var existingRecords = try recordsById(clientId: client.id, context: context)
        let supplementById = try upsertSupplements(backup: backup, client: client, existing: existingSupplements, context: context)
        try context.save()
        try upsertRecordsBatched(backup: backup, supplementById: supplementById, existing: &existingRecords, context: context)
    }

    private static func supplementsById(
        clientId: UUID,
        context: ModelContext
    ) throws -> [UUID: UserSupplement] {
        let all = try context.fetch(FetchDescriptor<UserSupplement>())
        return Dictionary(uniqueKeysWithValues: all.compactMap { s in
            guard s.client?.id == clientId else { return nil }
            return (s.id, s)
        })
    }

    private static func recordsById(
        clientId: UUID,
        context: ModelContext
    ) throws -> [UUID: IntakeRecord] {
        let all = try context.fetch(FetchDescriptor<IntakeRecord>())
        let filtered = all.filter { $0.supplement?.client?.id == clientId }
        return Dictionary(uniqueKeysWithValues: filtered.map { ($0.id, $0) })
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
                try apply(dto: dto, to: target, client: client)
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
                found.status = dto.status
                found.date = Date(timeIntervalSince1970: Double(dto.dateEpochMs) / 1000.0)
                continue
            }
            let supplementId = UUID(uuidString: dto.supplementId)
            guard let supplementId, let supplement = supplementById[supplementId] else { continue }
            let date = Date(timeIntervalSince1970: Double(dto.dateEpochMs) / 1000.0)
            let record = IntakeRecord(id: recordId, date: date, status: dto.status, supplement: supplement)
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
                let record = IntakeRecord(id: recordId, date: date, status: dto.status, supplement: supplement)
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
                Text("OAK Healthy")
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
