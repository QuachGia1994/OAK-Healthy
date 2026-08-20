import Foundation
import SwiftData

public enum ClientProfileMutationError: LocalizedError, Equatable {
    case invalidName
    case duplicateName

    public var errorDescription: String? {
        switch self {
        case .invalidName: "client_name_required".localized
        case .duplicateName: "client_name_duplicate".localized
        }
    }
}

/// Persistence owner for client-profile mutations. Views only present the result.
@MainActor
public enum ClientProfileMutationStore {
    public static func create(
        id: UUID = UUID(),
        name rawName: String,
        in context: ModelContext
    ) throws -> ClientProfile {
        let name = try validatedName(rawName, excluding: nil, context: context)
        let profile = ClientProfile(id: id, name: name)
        context.insert(profile)
        do {
            try context.save()
            return profile
        } catch {
            context.rollback()
            throw error
        }
    }

    public static func rename(_ profile: ClientProfile, to rawName: String, in context: ModelContext) throws {
        let name = try validatedName(rawName, excluding: profile.id, context: context)
        let previous = profile.name
        profile.name = name
        do {
            try context.save()
        } catch {
            profile.name = previous
            context.rollback()
            throw error
        }
    }

    public static func delete(_ profile: ClientProfile, in context: ModelContext) throws {
        context.delete(profile)
        do {
            try context.save()
        } catch {
            context.rollback()
            throw error
        }
    }

    private static func validatedName(
        _ rawName: String,
        excluding id: UUID?,
        context: ModelContext
    ) throws -> String {
        let name = ClientNamePolicy.cleaned(rawName)
        guard ClientNamePolicy.isValid(name) else { throw ClientProfileMutationError.invalidName }
        let profiles = try context.fetch(FetchDescriptor<ClientProfile>())
        let normalized = ClientNamePolicy.canonical(name)
        let duplicate = profiles.contains { profile in
            guard profile.id != id else { return false }
            return ClientNamePolicy.canonical(profile.name) == normalized
        }
        guard !duplicate else { throw ClientProfileMutationError.duplicateName }
        return name
    }
}
