import SwiftData

/// Explicit baseline for the first versioned OAK Healthy SwiftData schema.
enum OAKSchemaV1: VersionedSchema {
    static let versionIdentifier = Schema.Version(1, 0, 0)

    static var models: [any PersistentModel.Type] {
        [ClientProfile.self, UserSupplement.self, IntakeRecord.self]
    }
}

/// Central migration registry for every persisted SwiftData schema version.
enum OAKSchemaMigrationPlan: SchemaMigrationPlan {
    static var schemas: [any VersionedSchema.Type] {
        [OAKSchemaV1.self]
    }

    static var stages: [MigrationStage] {
        []
    }
}
