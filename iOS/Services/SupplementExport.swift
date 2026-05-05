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
                        durationMonths: supplement.cycleConfig.durationMonths
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
        let renderer = ImageRenderer(content: StackShareCardView(items: items).environment(\.colorScheme, colorScheme))
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
        CycleConfig(
            daysOn: dto.daysOn,
            daysOff: dto.daysOff,
            isContinuous: dto.isContinuous,
            durationMonths: dto.durationMonths
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

private struct StackShareCardView: View {
    let items: [StackShareItem]
    
    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color(red: 0.08, green: 0.0, blue: 0.15), .black],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text("OAK Healthy")
                        .font(.title2)
                        .fontWeight(.bold)
                    Spacer()
                }
                
                VStack(alignment: .leading, spacing: 10) {
                    ForEach(items.prefix(12)) { item in
                        HStack(alignment: .firstTextBaseline) {
                            Text(item.time)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .frame(width: 52, alignment: .leading)
                            
                            VStack(alignment: .leading, spacing: 2) {
                                Text(item.name)
                                    .font(.headline)
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
            .background(.ultraThinMaterial)
            .clipShape(RoundedRectangle(cornerRadius: 18))
            .padding(18)
        }
        .frame(width: 390, height: 520, alignment: .center)
    }
}
