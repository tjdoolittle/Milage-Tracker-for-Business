import Foundation

/// Made-up sample trips for screenshots (`-demo` launch argument). Never saved.
enum DemoData {
    static func make() -> AppData {
        var d = AppData()
        let places = [
            Place(name: "Shop", lat: 33.7900, lng: -84.3880),
            Place(name: "Hartwell residence", lat: 33.8480, lng: -84.3730),
            Place(name: "Peachtree Dental", lat: 33.8030, lng: -84.3880),
            Place(name: "Morrow office park", lat: 33.5830, lng: -84.3390),
            Place(name: "Alpharetta model home", lat: 34.0750, lng: -84.2940),
            Place(name: "AV supply house", lat: 33.7550, lng: -84.4200),
        ]
        d.settings.places = places
        d.settings.autoDetect = true
        let jobs: [(String, String, Int, Double)] = [
            ("Theater install", "Hartwell residence", 1, 12.6),
            ("Control4 programming", "Peachtree Dental", 2, 4.8),
            ("Service call: Wi-Fi drops", "Morrow office park", 3, 21.3),
            ("Site survey", "Alpharetta model home", 4, 26.9),
            ("Equipment pickup", "", 5, 6.2),
        ]
        var rng = SeededRandom(seed: 7)
        let cal = Calendar(identifier: .gregorian)
        let year = cal.component(.year, from: Date())
        var day = cal.date(from: DateComponents(year: year, month: 1, day: 6))!
        let end = Date()
        var n = 0
        while day < end {
            let wd = cal.component(.weekday, from: day)
            if (2...6).contains(wd) && rng.next() < 0.42 {
                let j = jobs[Int(rng.next() * Double(jobs.count)) % jobs.count]
                let round = rng.next() < 0.4
                var t = Trip(id: "demo-\(n)", date: Store.dayFormatter.string(from: day), miles: round ? j.3 * 2 : j.3)
                t.purpose = j.0; t.client = j.1; t.from = "Shop"; t.to = places[j.2].name
                t.roundTrip = round
                t.source = rng.next() < 0.5 ? "Auto GPS" : nil
                t.createdAt = Double(n)
                d.trips.append(t)
                n += 1
            }
            day = cal.date(byAdding: .day, value: 1, to: day)!
        }
        // Two detected drives waiting for review, at believable morning times (screenshots show 9:41).
        let now = Date().timeIntervalSince1970 * 1000
        func todayAt(_ h: Int, _ m: Int) -> Double {
            let d = cal.date(bySettingHour: h, minute: m, second: 0, of: Date()) ?? Date()
            return d.timeIntervalSince1970 * 1000
        }
        var r1 = Trip(id: "demo-r1", date: Store.today(), miles: 18.4)
        r1.status = TripStatus.review; r1.source = "Auto GPS"; r1.from = "Shop"; r1.to = "1180 Peachtree St NE, Atlanta"
        r1.startTime = todayAt(7, 52); r1.createdAt = now
        var r2 = Trip(id: "demo-r2", date: Store.today(), miles: 3.1)
        r2.status = TripStatus.review; r2.source = "Auto GPS"; r2.from = "1180 Peachtree St NE, Atlanta"; r2.to = "Home"
        r2.startTime = todayAt(8, 31); r2.createdAt = now + 1
        d.trips += [r1, r2]

        if ProcessInfo.processInfo.arguments.contains("-active") {
            var a = ActiveTrip(auto: true)
            a.startedAt = todayAt(9, 15)
            a.meters = 12.3 * 1609.344
            a.fixes = 540
            a.startLat = 33.79; a.startLng = -84.388
            d.active = a
        }
        return d
    }
}

/// Small deterministic generator so screenshots look the same on every run.
struct SeededRandom {
    private var state: UInt64
    init(seed: UInt64) { state = seed &* 6364136223846793005 &+ 1442695040888963407 }
    mutating func next() -> Double {
        state = state &* 6364136223846793005 &+ 1442695040888963407
        return Double((state >> 11) & ((1 << 53) - 1)) / Double(1 << 53)
    }
}
