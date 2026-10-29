import Foundation

/// Logged trips count toward totals. Review trips were detected automatically and wait for a decision.
enum TripStatus {
    static let logged = "logged"
    static let review = "review"
    static let personal = "personal"
}

/// One drive. The JSON field names match the Android and web apps, so backups move between them.
struct Trip: Identifiable, Equatable, Codable {
    var id: String
    var date: String                 // yyyy-MM-dd, local time
    var miles: Double
    var purpose: String = ""
    var client: String = ""
    var from: String = ""
    var to: String = ""
    var notes: String = ""
    var roundTrip: Bool = false
    var odoStart: Double? = nil
    var odoEnd: Double? = nil
    var createdAt: Double = Date().timeIntervalSince1970 * 1000   // milliseconds
    var source: String? = nil        // "GPS", "Auto GPS", "Google Maps", nil = entered by hand
    var status: String = TripStatus.logged
    var startTime: Double? = nil     // milliseconds
    var endTime: Double? = nil
    var startLat: Double? = nil
    var startLng: Double? = nil
    var endLat: Double? = nil
    var endLng: Double? = nil
    var gKey: String? = nil

    var year: Int { Int(date.prefix(4)) ?? 0 }
    var month: Int { Int(date.dropFirst(5).prefix(2)) ?? 1 }

    init(id: String, date: String, miles: Double) {
        self.id = id; self.date = date; self.miles = miles
    }

    enum CodingKeys: String, CodingKey {
        case id, date, miles, purpose, client, from, to, notes, roundTrip, odoStart, odoEnd, createdAt,
             source, status, startTime, endTime, startLat, startLng, endLat, endLng, gKey
    }

    // Lenient decoding: the web app writes some numbers as strings and times as ISO dates.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.str(.id) ?? ""
        date = String((c.str(.date) ?? "").prefix(10))
        miles = c.num(.miles) ?? 0
        purpose = c.str(.purpose) ?? ""
        client = c.str(.client) ?? ""
        from = c.str(.from) ?? ""
        to = c.str(.to) ?? ""
        notes = c.str(.notes) ?? ""
        roundTrip = c.bool(.roundTrip) ?? false
        odoStart = c.num(.odoStart)
        odoEnd = c.num(.odoEnd)
        createdAt = c.num(.createdAt) ?? Date().timeIntervalSince1970 * 1000
        source = c.str(.source).flatMap { $0.isEmpty ? nil : $0 }
        status = c.str(.status).flatMap { $0.isEmpty ? nil : $0 } ?? TripStatus.logged
        startTime = c.num(.startTime)
        endTime = c.num(.endTime)
        startLat = c.num(.startLat); startLng = c.num(.startLng)
        endLat = c.num(.endLat); endLng = c.num(.endLng)
        gKey = c.str(.gKey)
    }
}

struct Place: Codable, Equatable, Hashable {
    var name: String
    var lat: Double
    var lng: Double
}

/// A mileage rate that applies from `from` (yyyy-MM-dd) until the next rate starts.
struct RatePeriod: Codable, Equatable, Hashable, Identifiable {
    var from: String
    var rate: Double
    var id: String { from }
}

struct AppSettings: Codable, Equatable {
    var rates: [String: Double] = [:]        // older per-year overrides from web backups
    var ratePeriods: [RatePeriod]? = nil     // nil = use the IRS schedule
    var places: [Place] = []
    var autoDetect: Bool = false
    var minAutoMiles: Double = 0.5

    init() {}

    enum CodingKeys: String, CodingKey { case rates, ratePeriods, places, autoDetect, minAutoMiles }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        rates = (try? c.decodeIfPresent([String: Double].self, forKey: .rates)) ?? [:]
        let periods = (try? c.decodeIfPresent([RatePeriod].self, forKey: .ratePeriods)) ?? nil
        ratePeriods = periods?.filter { $0.from.count == 10 && $0.rate > 0 }
        places = (try? c.decodeIfPresent([Place].self, forKey: .places)) ?? []
        autoDetect = c.bool(.autoDetect) ?? false
        minAutoMiles = c.num(.minAutoMiles) ?? 0.5
    }
}

/// The drive being measured right now. Saved often so the trip survives the app being closed.
struct ActiveTrip: Codable, Equatable {
    var startedAt: Double              // milliseconds
    var auto: Bool
    var meters: Double = 0
    var gaps: Int = 0
    var fixes: Int = 0
    var maxSpeed: Double = 0           // meters per second
    var startLat: Double? = nil
    var startLng: Double? = nil
    var lastLat: Double? = nil
    var lastLng: Double? = nil
    var lastFixAt: Double = 0
    var lastMoveAt: Double
    var purpose: String = ""
    var client: String = ""
    var from: String = ""

    var miles: Double { meters / 1609.344 }

    init(auto: Bool) {
        let now = Date().timeIntervalSince1970 * 1000
        self.startedAt = now
        self.auto = auto
        self.lastMoveAt = now
    }
}

struct AppData: Codable, Equatable {
    var trips: [Trip] = []
    var settings = AppSettings()
    var active: ActiveTrip? = nil

    init() {}

    enum CodingKeys: String, CodingKey { case trips, settings, active }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        trips = ((try? c.decodeIfPresent([Trip].self, forKey: .trips)) ?? nil)?.filter { !$0.id.isEmpty && $0.date.count == 10 } ?? []
        settings = ((try? c.decodeIfPresent(AppSettings.self, forKey: .settings)) ?? nil) ?? AppSettings()
        active = (try? c.decodeIfPresent(ActiveTrip.self, forKey: .active)) ?? nil
    }
}

// MARK: - Lenient JSON helpers

private let isoFull: ISO8601DateFormatter = {
    let f = ISO8601DateFormatter(); f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]; return f
}()
private let isoPlain = ISO8601DateFormatter()

extension KeyedDecodingContainer {
    func str(_ k: Key) -> String? {
        if let s = try? decodeIfPresent(String.self, forKey: k) { return s }
        if let d = try? decodeIfPresent(Double.self, forKey: k) { return String(d) }
        return nil
    }

    /// A number, a numeric string, or an ISO date (returned as milliseconds).
    func num(_ k: Key) -> Double? {
        if let d = try? decodeIfPresent(Double.self, forKey: k) { return d }
        if let s = try? decodeIfPresent(String.self, forKey: k) {
            if let d = Double(s.replacingOccurrences(of: ",", with: "")) { return d }
            if let date = isoFull.date(from: s) ?? isoPlain.date(from: s) { return date.timeIntervalSince1970 * 1000 }
        }
        return nil
    }

    func bool(_ k: Key) -> Bool? {
        if let b = try? decodeIfPresent(Bool.self, forKey: k) { return b }
        if let s = try? decodeIfPresent(String.self, forKey: k) { return s == "true" || s == "Yes" }
        return nil
    }
}
