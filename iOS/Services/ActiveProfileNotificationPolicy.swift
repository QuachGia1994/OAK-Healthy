import Foundation

enum ActiveProfileNotificationPolicy {
    static func allows(activeClientId: UUID?, supplementClientId: UUID?) -> Bool {
        guard let activeClientId, let supplementClientId else { return false }
        return activeClientId == supplementClientId
    }
}
