import Foundation

/// Canonical Gregorian `yyyy-MM-dd` codec for date-only domain values.
/// The schema is Gregorian/POSIX; only the time zone comes from the caller.
public enum LocalDayCodec: Sendable {
    public static func string(from date: Date, calendar: Calendar = .current) -> String {
        let schemaCalendar = canonicalCalendar(timeZone: calendar.timeZone)
        let parts = schemaCalendar.dateComponents([.year, .month, .day], from: date)
        guard let year = parts.year, let month = parts.month, let day = parts.day else {
            preconditionFailure("Calendar failed to produce a date-only health value")
        }
        return String(
            format: "%04d-%02d-%02d",
            locale: Locale(identifier: "en_US_POSIX"),
            year,
            month,
            day
        )
    }

    public static func date(from raw: String, calendar: Calendar = .current) -> Date? {
        let pieces = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            .split(separator: "-", omittingEmptySubsequences: false)
        guard pieces.count == 3,
              let year = Int(pieces[0]),
              let month = Int(pieces[1]),
              let day = Int(pieces[2]) else { return nil }
        let schemaCalendar = canonicalCalendar(timeZone: calendar.timeZone)
        var components = DateComponents()
        components.calendar = schemaCalendar
        components.timeZone = schemaCalendar.timeZone
        components.year = year
        components.month = month
        components.day = day
        guard let date = schemaCalendar.date(from: components) else { return nil }
        let roundTrip = schemaCalendar.dateComponents([.year, .month, .day], from: date)
        guard roundTrip.year == year, roundTrip.month == month, roundTrip.day == day else { return nil }
        return date
    }

    private static func canonicalCalendar(timeZone: TimeZone) -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.locale = Locale(identifier: "en_US_POSIX")
        calendar.timeZone = timeZone
        return calendar
    }
}
