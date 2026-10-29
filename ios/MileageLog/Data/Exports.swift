import Foundation
import SwiftUI
import UniformTypeIdentifiers

enum Exports {
    private static func q(_ v: String) -> String {
        v.contains(",") || v.contains("\"") || v.contains("\n") ? "\"" + v.replacingOccurrences(of: "\"", with: "\"\"") + "\"" : v
    }

    /// One row per business trip in the year, with the rate used, plus a totals row.
    static func csv(year: Int, store: Store = .shared) -> String {
        let trips = store.data.trips
            .filter { $0.status == TripStatus.logged && $0.year == year }
            .sorted { ($0.date, $0.startTime ?? $0.createdAt) < ($1.date, $1.startTime ?? $1.createdAt) }
        var rows: [[String]] = [["Date", "From", "To", "Business purpose", "Client", "Odometer start", "Odometer end",
                                 "Round trip", "Miles", "Rate per mile", "Deduction", "Notes", "Source"]]
        var total = 0.0, totalDed = 0.0
        for t in trips {
            let rate = store.rateOn(t.date)
            let ded = (t.miles * rate * 100).rounded() / 100
            total += t.miles; totalDed += ded
            rows.append([t.date, t.from, t.to, t.purpose, t.client,
                         t.odoStart.map { String(format: "%.1f", $0) } ?? "", t.odoEnd.map { String(format: "%.1f", $0) } ?? "",
                         t.roundTrip ? "Yes" : "", String(format: "%.1f", t.miles), String(format: "%.3f", rate),
                         String(format: "%.2f", ded), t.notes, t.source ?? "Manual"])
        }
        rows.append(["Total", "", "", "", "", "", "", "", String(format: "%.1f", total), "", String(format: "%.2f", totalDed), "", ""])
        return rows.map { $0.map(q).joined(separator: ",") }.joined(separator: "\n")
    }

    /// Same format as the Android and web backups.
    static func backup(store: Store = .shared) -> String {
        struct Backup: Encodable {
            let app = "mileage-log", version = 1, platform = "ios"
            let exportedAt: String
            let trips: [Trip]
            let settings: AppSettings
        }
        let b = Backup(exportedAt: ISO8601DateFormatter().string(from: Date()), trips: store.data.trips, settings: store.data.settings)
        let enc = JSONEncoder(); enc.outputFormatting = [.prettyPrinted, .sortedKeys]
        return (try? enc.encode(b)).flatMap { String(data: $0, encoding: .utf8) } ?? "{}"
    }

    /// Adds trips that aren't already here. Returns (added, skipped), or nil if the file isn't a backup.
    static func restore(_ raw: Data, store: Store = .shared) -> (Int, Int)? {
        struct Backup: Decodable {
            let app: String?
            let trips: [Trip]?
            let settings: AppSettings?
        }
        guard let b = try? JSONDecoder().decode(Backup.self, from: raw), b.app == "mileage-log", let incoming = b.trips else { return nil }
        let have = Set(store.data.trips.map(\.id))
        let add = incoming.filter { !$0.id.isEmpty && $0.date.count == 10 && !have.contains($0.id) }
        store.update { d in
            d.trips += add
            if let theirs = b.settings {
                for p in theirs.places where !d.settings.places.contains(where: { Geo.meters(p.lat, p.lng, $0.lat, $0.lng) < 200 }) {
                    d.settings.places.append(p)
                }
                d.settings.rates = theirs.rates.merging(d.settings.rates) { _, mine in mine }
                if d.settings.ratePeriods == nil { d.settings.ratePeriods = theirs.ratePeriods }
            }
        }
        return (add.count, incoming.count - add.count)
    }
}

/// Wraps text for SwiftUI's file exporter.
struct TextFile: FileDocument {
    static var readableContentTypes: [UTType] { [.commaSeparatedText, .json, .plainText] }
    var text: String
    var type: UTType

    init(text: String, type: UTType) { self.text = text; self.type = type }

    init(configuration: ReadConfiguration) throws {
        text = configuration.file.regularFileContents.flatMap { String(data: $0, encoding: .utf8) } ?? ""
        type = .plainText
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data(text.utf8))
    }
}
