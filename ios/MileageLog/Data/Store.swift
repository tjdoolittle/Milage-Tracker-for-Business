import Foundation
import Combine

/// All app data lives in one JSON file in Application Support. The UI and the tracker share it.
final class Store: ObservableObject {
    static let shared = Store()

    /// IRS standard business rates. 2026 changed midyear: 72.5¢ through June 30, 76¢ from July 1.
    static let irsRates: [RatePeriod] = [
        RatePeriod(from: "2024-01-01", rate: 0.67),
        RatePeriod(from: "2025-01-01", rate: 0.70),
        RatePeriod(from: "2026-01-01", rate: 0.725),
        RatePeriod(from: "2026-07-01", rate: 0.76),
    ]

    @Published private(set) var data = AppData()
    private let fileURL: URL
    /// Screenshot mode (`-demo` launch argument): sample data, nothing saved.
    let demo: Bool

    private init() {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        fileURL = dir.appendingPathComponent("mileage-log.json")
        demo = ProcessInfo.processInfo.arguments.contains("-demo")
        if demo {
            data = DemoData.make()
        } else if let raw = try? Data(contentsOf: fileURL), let d = try? JSONDecoder().decode(AppData.self, from: raw) {
            data = d
        }
    }

    func update(_ change: (inout AppData) -> Void) {
        var d = data
        change(&d)
        guard d != data else { return }
        data = d
        save()
    }

    private func save() {
        guard !demo else { return }
        if let raw = try? JSONEncoder().encode(data) {
            try? raw.write(to: fileURL, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        }
    }

    // MARK: Trips

    func saveTrip(_ t: Trip) {
        update { d in
            if let i = d.trips.firstIndex(where: { $0.id == t.id }) { d.trips[i] = t } else { d.trips.append(t) }
        }
    }

    func deleteTrip(_ id: String) { update { $0.trips.removeAll { $0.id == id } } }

    func setActive(_ a: ActiveTrip?) { update { $0.active = a } }

    func setSettings(_ s: AppSettings) { update { $0.settings = s } }

    /// Remember a named spot so later trips that start or end within 200 m get its name.
    func learnPlace(_ name: String, lat: Double?, lng: Double?) {
        let name = name.trimmingCharacters(in: .whitespaces)
        guard let lat, let lng, !name.isEmpty, !Geo.looksLikeCoords(name) else { return }
        var s = data.settings
        guard !s.places.contains(where: { Geo.meters(lat, lng, $0.lat, $0.lng) < 200 }) else { return }
        s.places.append(Place(name: name, lat: lat, lng: lng))
        if s.places.count > 300 { s.places.removeFirst(s.places.count - 300) }
        setSettings(s)
    }

    // MARK: Purposes and home/work

    /// Common business purposes offered in the trip editor. "Other" lets the driver type their own.
    static let purposes = [
        "Travel between offices", "Client Visits", "Business Meeting", "Sales Calls",
        "Deliveries", "Banking", "Admin Errands", "Job Site Visits",
    ]
    static let otherPurpose = "Other"
    static let commuteNote = "Regular Everyday Commute is Not Deductible"
    static let responsibility = "Mileage Log tracks all your trips, but you're responsible for the data you log. Check each trip before you count it."

    /// Save the home or work address. When it was found on the map it also becomes a saved
    /// place, so GPS trips that start or end there get the address filled in.
    func setHomeWork(home: Bool, address: String, lat: Double?, lng: Double?) {
        update { d in
            let old = home ? d.settings.homeAddress : d.settings.workAddress
            if !old.isEmpty { d.settings.places.removeAll { $0.name == old } }
            if let lat, let lng, !address.isEmpty {
                d.settings.places.removeAll { Geo.meters(lat, lng, $0.lat, $0.lng) < 200 }
                d.settings.places.append(Place(name: address, lat: lat, lng: lng))
            }
            if home { d.settings.homeAddress = address } else { d.settings.workAddress = address }
        }
    }

    /// True when a trip goes between the saved home and work addresses, in either direction.
    func isCommute(from: String, to: String) -> Bool {
        let norm = { (s: String) in s.trimmingCharacters(in: .whitespaces).lowercased() }
        let h = norm(data.settings.homeAddress), w = norm(data.settings.workAddress)
        guard !h.isEmpty, !w.isEmpty else { return false }
        let f = norm(from), t = norm(to)
        return (f == h && t == w) || (f == w && t == h)
    }

    // MARK: Rates

    /// The rate schedule in effect, oldest first.
    func schedule(_ s: AppSettings? = nil) -> [RatePeriod] {
        let s = s ?? data.settings
        if let custom = s.ratePeriods, !custom.isEmpty { return custom.sorted { $0.from < $1.from } }
        let overridden = Set(s.rates.keys)
        let base = Store.irsRates.filter { !overridden.contains(String($0.from.prefix(4))) }
        let legacy = s.rates.compactMap { (y, r) -> RatePeriod? in Int(y) != nil ? RatePeriod(from: "\(y)-01-01", rate: r) : nil }
        return (base + legacy).sorted { $0.from < $1.from }
    }

    /// The rate for a trip on `date`: the latest rate that started on or before it.
    func rateOn(_ date: String) -> Double {
        let sch = schedule()
        guard let first = sch.first else { return 0.725 }
        return sch.last(where: { $0.from <= date })?.rate ?? first.rate
    }

    func deduction(_ t: Trip) -> Double { t.miles * rateOn(t.date) }

    func setSchedule(_ list: [RatePeriod]) {
        var seen = Set<String>()
        let clean = list.filter { seen.insert($0.from).inserted }.sorted { $0.from < $1.from }
        var s = data.settings
        s.ratePeriods = clean
        s.rates = [:]
        setSettings(s)
    }

    func useIRSRates() {
        var s = data.settings
        s.ratePeriods = nil
        s.rates = [:]
        setSettings(s)
    }

    var isCustomSchedule: Bool { data.settings.ratePeriods != nil || !data.settings.rates.isEmpty }

    // MARK: Dates and ids

    static func newId() -> String {
        "t-" + UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased().prefix(12)
    }

    static let dayFormatter: DateFormatter = {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    static func today() -> String { dayFormatter.string(from: Date()) }

    static func isoDate(ms: Double) -> String { dayFormatter.string(from: Date(timeIntervalSince1970: ms / 1000)) }

    static func date(_ iso: String) -> Date { dayFormatter.date(from: iso) ?? Date() }
}
