import UIKit

/// Yearly numbers behind the tax report. Matches the Android report.
struct ReportData {
    struct VehicleRow {
        var name: String
        var inService: String
        var odo: YearOdo?
        var business: Double
        var commuting: Double
        var total: Double? {
            guard let s = odo?.start, let e = odo?.end, e >= s else { return nil }
            return e - s
        }
        var other: Double? { total.map { max(0, $0 - business - commuting) } }
    }
    struct RateRow { var rate: Double; var from: String; var to: String; var miles: Double }
    struct MonthRow { var month: Int; var count: Int; var miles: Double; var deduction: Double; var parking: Double }

    let year: Int
    let trips: [Trip]
    let miles: Double
    let deduction: Double
    let parking: Double
    let vehicles: [VehicleRow]
    let rates: [RateRow]
    let months: [MonthRow]

    init(year: Int, store: Store = .shared) {
        self.year = year
        let d = store.data
        let s = d.settings
        let ded = { (t: Trip) in (t.miles * store.rateOn(t.date) * 100).rounded() / 100 }
        let trips = d.trips.filter { $0.status == TripStatus.logged && $0.year == year }
            .sorted { ($0.date, $0.startTime ?? $0.createdAt) < ($1.date, $1.startTime ?? $1.createdAt) }
        self.trips = trips
        let personal = d.trips.filter { $0.status == TripStatus.personal && $0.year == year }
        miles = trips.reduce(0) { $0 + $1.miles }
        deduction = trips.reduce(0) { $0 + ded($1) }
        parking = trips.reduce(0) { $0 + $1.parking }

        let list = s.vehicles.isEmpty ? [Vehicle(id: "", name: "Vehicle")] : s.vehicles
        let owner = { (t: Trip) -> String in (s.vehicles.first { $0.id == t.vehicleId } ?? s.vehicles.first)?.id ?? "" }
        vehicles = list.map { v in
            VehicleRow(
                name: v.name, inService: v.inService, odo: v.odometer[String(year)],
                business: trips.filter { owner($0) == v.id }.reduce(0) { $0 + $1.miles },
                commuting: personal.filter { owner($0) == v.id && store.isCommute(from: $0.from, to: $0.to) }.reduce(0) { $0 + $1.miles })
        }

        let sch = store.schedule()
        var groups: [String: [Trip]] = [:]
        for t in trips { groups[sch.last(where: { $0.from <= t.date })?.from ?? "", default: []].append(t) }
        rates = groups.values.map { ts in
            RateRow(rate: store.rateOn(ts[0].date), from: ts.first!.date, to: ts.last!.date, miles: ts.reduce(0) { $0 + $1.miles })
        }.sorted { $0.from < $1.from }

        months = (1...12).map { m in
            let ts = trips.filter { $0.month == m }
            return MonthRow(month: m, count: ts.count, miles: ts.reduce(0) { $0 + $1.miles },
                            deduction: ts.reduce(0) { $0 + ded($1) }, parking: ts.reduce(0) { $0 + $1.parking })
        }
    }
}

/// Draws the tax report as a letter-size PDF.
enum ReportPDF {
    private static let W: CGFloat = 612, H: CGFloat = 792, M: CGFloat = 42
    private static let green = UIColor(red: 13/255, green: 107/255, blue: 74/255, alpha: 1)
    private static let ink = UIColor(red: 24/255, green: 28/255, blue: 26/255, alpha: 1)
    private static let muted = UIColor(red: 98/255, green: 106/255, blue: 102/255, alpha: 1)
    private static let line = UIColor(red: 214/255, green: 219/255, blue: 216/255, alpha: 1)
    private static let band = UIColor(red: 238/255, green: 243/255, blue: 240/255, alpha: 1)

    private static let usdFmt: NumberFormatter = {
        let f = NumberFormatter(); f.numberStyle = .currency; f.locale = Locale(identifier: "en_US"); return f
    }()
    private static func usd(_ v: Double) -> String { usdFmt.string(from: NSNumber(value: v)) ?? "$0.00" }
    private static func mi(_ v: Double) -> String {
        let f = NumberFormatter(); f.numberStyle = .decimal; f.minimumFractionDigits = 1; f.maximumFractionDigits = 1
        f.locale = Locale(identifier: "en_US"); return f.string(from: NSNumber(value: v)) ?? "0.0"
    }
    private static func whole(_ v: Double) -> String {
        let f = NumberFormatter(); f.numberStyle = .decimal; f.maximumFractionDigits = 0
        f.locale = Locale(identifier: "en_US"); return f.string(from: NSNumber(value: v)) ?? "0"
    }
    private static func cents(_ r: Double) -> String {
        let c = (r * 1000).rounded() / 10
        return c == c.rounded() ? "\(Int(c))¢" : "\(c)¢"
    }
    private static func fmtDate(_ iso: String, _ pattern: String) -> String {
        guard let d = Store.dayFormatter.date(from: iso) else { return iso }
        let f = DateFormatter(); f.locale = Locale(identifier: "en_US"); f.dateFormat = pattern
        return f.string(from: d)
    }
    private static func font(_ size: CGFloat, bold: Bool = false) -> UIFont {
        .systemFont(ofSize: size, weight: bold ? .bold : .regular)
    }
    private static func attrs(_ size: CGFloat, bold: Bool = false, color: UIColor = ink) -> [NSAttributedString.Key: Any] {
        [.font: font(size, bold: bold), .foregroundColor: color]
    }

    /// Cuts text to fit `width`, adding an ellipsis.
    private static func fit(_ text: String, _ a: [NSAttributedString.Key: Any], _ width: CGFloat) -> String {
        if (text as NSString).size(withAttributes: a).width <= width { return text }
        var t = text
        while !t.isEmpty && ((t + "…") as NSString).size(withAttributes: a).width > width { t.removeLast() }
        return t + "…"
    }

    private final class Writer {
        let ctx: UIGraphicsPDFRendererContext
        let footer: String
        var page = 0
        var y: CGFloat = 0
        init(_ ctx: UIGraphicsPDFRendererContext, footer: String) { self.ctx = ctx; self.footer = footer }

        func newPage() {
            ctx.beginPage()
            page += 1
            y = M
            let a = attrs(8, color: muted)
            (footer as NSString).draw(at: CGPoint(x: M, y: H - 32), withAttributes: a)
            let pg = "Page \(page)" as NSString
            pg.draw(at: CGPoint(x: W - M - pg.size(withAttributes: a).width, y: H - 32), withAttributes: a)
        }
        /// Starts a new page when fewer than `h` points are left. Returns true if it did.
        @discardableResult func room(_ h: CGFloat) -> Bool {
            if y + h > H - 48 { newPage(); return true }
            return false
        }
        func fill(_ r: CGRect, _ c: UIColor) { c.setFill(); UIRectFill(r) }
        func text(_ s: String, _ x: CGFloat, _ y: CGFloat, _ a: [NSAttributedString.Key: Any]) {
            (s as NSString).draw(at: CGPoint(x: x, y: y), withAttributes: a)
        }

        func paragraph(_ s: String, _ a: [NSAttributedString.Key: Any], gap: CGFloat = 4) {
            let str = NSAttributedString(string: s, attributes: a)
            let r = str.boundingRect(with: CGSize(width: W - 2 * M, height: .greatestFiniteMagnitude), options: [.usesLineFragmentOrigin], context: nil)
            room(r.height + 2)
            str.draw(with: CGRect(x: M, y: y, width: W - 2 * M, height: r.height + 2), options: [.usesLineFragmentOrigin], context: nil)
            y += ceil(r.height) + gap
        }

        func heading(_ s: String) {
            room(60)
            y += 12
            text(s.uppercased(), M, y, attrs(12, bold: true, color: green))
            y += 18
            fill(CGRect(x: M, y: y, width: W - 2 * M, height: 1.2), green)
            y += 8
        }

        /// One table row. `cols` are (share of page width, right-aligned).
        func row(_ cells: [String], _ cols: [(CGFloat, Bool)], _ a: [NSAttributedString.Key: Any], shade: Bool = false, rule: Bool = true) {
            let size = (a[.font] as? UIFont)?.pointSize ?? 9
            let h = size + 8
            room(h)
            if shade { fill(CGRect(x: M, y: y, width: W - 2 * M, height: h), band) }
            var x = M
            let total = W - 2 * M
            for (i, cell) in cells.enumerated() {
                let (share, right) = cols[i]
                let w = total * share
                let t = fit(cell, a, w - 8)
                let tw = (t as NSString).size(withAttributes: a).width
                text(t, right ? x + w - 4 - tw : x + 4, y + 3, a)
                x += w
            }
            y += h
            if rule { fill(CGRect(x: M, y: y - 0.5, width: W - 2 * M, height: 0.5), line) }
        }
    }

    static func make(year: Int, store: Store = .shared) -> Data {
        let r = ReportData(year: year, store: store)
        let s = store.data.settings
        let renderer = UIGraphicsPDFRenderer(bounds: CGRect(x: 0, y: 0, width: W, height: H))
        return renderer.pdfData { ctx in
            let w = Writer(ctx, footer: "Tax Mileage Tracker · \(year) mileage log · Not tax advice. Not affiliated with the IRS or any government agency.")
            w.newPage()

            // Title
            w.y += 4
            w.text("\(year) Business Mileage Log", M, w.y, attrs(22, bold: true))
            w.y += 30
            let who = s.reportName.trimmingCharacters(in: .whitespaces)
            let sub = [who.isEmpty ? nil : who, "Prepared \(fmtDate(Store.today(), "MMM d, yyyy"))"].compactMap { $0 }.joined(separator: "  ·  ")
            w.text(sub, M, w.y, attrs(10, color: muted))
            w.y += 14

            // Summary
            w.heading("Summary")
            let boxes = [(mi(r.miles), "BUSINESS MILES"), (usd(r.deduction), "MILEAGE DEDUCTION"),
                         (usd(r.parking), "PARKING AND TOLLS"), (usd(r.deduction + r.parking), "TOTAL")]
            let bw = (W - 2 * M) / 4
            w.room(56)
            for (i, b) in boxes.enumerated() {
                let x = M + CGFloat(i) * bw
                w.fill(CGRect(x: x + 3, y: w.y, width: bw - 6, height: 50), band)
                let big = attrs(18, bold: true)
                w.text(fit(b.0, big, bw - 16), x + 10, w.y + 6, big)
                w.text(b.1, x + 10, w.y + 32, attrs(8.5, bold: true, color: muted))
            }
            w.y += 58
            w.paragraph("\(r.trips.count) business trips. Each trip uses the IRS standard mileage rate in effect on the day it was driven. " +
                        "Parking fees and tolls for business trips are deductible in addition to the standard mileage rate.", attrs(9, color: muted))

            // Vehicles
            w.heading("Vehicle information")
            w.paragraph("The numbers Schedule C (Form 1040), Part IV, asks for. Total miles come from the odometer readings entered in Settings.",
                        attrs(9, color: muted), gap: 2)
            let vCols: [(CGFloat, Bool)] = [(0.2, false), (0.13, false), (0.18, true), (0.13, true), (0.11, true), (0.12, true), (0.13, true)]
            let head = attrs(8.5, bold: true, color: muted)
            let body = attrs(9.5)
            w.row(["Vehicle", "In service", "Odometer", "Total miles", "Business", "Commuting", "Other"], vCols, head)
            for v in r.vehicles {
                var odo = "Not entered"
                if let a = v.odo?.start, let b = v.odo?.end { odo = "\(whole(a))–\(whole(b))" }
                w.row([v.name, v.inService.isEmpty ? "Not entered" : fmtDate(v.inService, "MMM d, yyyy"), odo,
                       v.total.map(mi) ?? "Not entered", mi(v.business), mi(v.commuting), v.other.map(mi) ?? "Not entered"], vCols, body)
            }
            w.paragraph("Commuting is trips marked personal between your saved home and work addresses. Other is total miles minus business and commuting.",
                        attrs(8.5, color: muted), gap: 0)

            // Rates
            if !r.rates.isEmpty {
                w.heading("Rates used")
                let rCols: [(CGFloat, Bool)] = [(0.4, false), (0.2, true), (0.2, true), (0.2, true)]
                w.row(["Trips dated", "Rate", "Miles", "Deduction"], rCols, head)
                for rr in r.rates {
                    w.row(["\(fmtDate(rr.from, "MMM d")) to \(fmtDate(rr.to, "MMM d"))", cents(rr.rate) + " per mile", mi(rr.miles), usd(rr.miles * rr.rate)], rCols, body)
                }
                w.paragraph("Source: IRS standard mileage rates, \(Store.irsSourceURL.absoluteString)", attrs(8.5, color: muted), gap: 0)
            }

            // Months
            w.heading("By month")
            let mCols: [(CGFloat, Bool)] = [(0.28, false), (0.12, true), (0.2, true), (0.2, true), (0.2, true)]
            w.row(["Month", "Trips", "Miles", "Deduction", "Parking and tolls"], mCols, head)
            let names = DateFormatter().standaloneMonthSymbols ?? []
            for (i, m) in r.months.enumerated() {
                let name = m.month - 1 < names.count ? names[m.month - 1] : "\(m.month)"
                w.row([name, "\(m.count)", mi(m.miles), usd(m.deduction), usd(m.parking)], mCols, body, shade: i % 2 == 1)
            }
            w.row(["Total", "\(r.trips.count)", mi(r.miles), usd(r.deduction), usd(r.parking)], mCols, attrs(9.5, bold: true))

            // Trip log
            w.newPage()
            w.heading("Trip log")
            let multi = s.vehicles.count > 1
            let tCols: [(CGFloat, Bool)] = multi
                ? [(0.09, false), (0.34, false), (0.19, false), (0.1, true), (0.08, true), (0.11, true), (0.09, true)]
                : [(0.1, false), (0.5, false), (0.11, true), (0.08, true), (0.11, true), (0.1, true)]
            let tHead = multi ? ["Date", "Route and purpose", "Vehicle", "Miles", "Rate", "Deduction", "Parking"]
                              : ["Date", "Route and purpose", "Miles", "Rate", "Deduction", "Parking"]
            let small = attrs(8.5)
            let subA = attrs(8, color: muted)
            w.row(tHead, tCols, head)
            for (i, t) in r.trips.enumerated() {
                let rate = store.rateOn(t.date)
                var route = [t.from, t.to].filter { !$0.isEmpty }.joined(separator: " → ")
                if route.isEmpty { route = "Route not entered" }
                if t.roundTrip { route += " (round trip)" }
                let purpose = [t.purpose, t.client].filter { !$0.isEmpty }.joined(separator: " · ")
                let h: CGFloat = 30
                if w.room(h) { w.row(tHead, tCols, head) }
                if i % 2 == 1 { w.fill(CGRect(x: M, y: w.y, width: W - 2 * M, height: h), band) }
                var cells = [fmtDate(t.date, "MMM d"), route]
                if multi { cells.append(store.vehicle(for: t)?.name ?? "") }
                cells += [mi(t.miles), cents(rate), usd(t.miles * rate), t.parking > 0 ? usd(t.parking) : ""]
                var x = M
                let total = W - 2 * M
                for (ci, cell) in cells.enumerated() {
                    let (share, right) = tCols[ci]
                    let cw = total * share
                    let txt = fit(cell, small, cw - 8)
                    let tw = (txt as NSString).size(withAttributes: small).width
                    w.text(txt, right ? x + cw - 4 - tw : x + 4, w.y + 4, small)
                    if ci == 1 && !purpose.isEmpty { w.text(fit(purpose, subA, cw - 8), x + 4, w.y + 16, subA) }
                    x += cw
                }
                w.y += h
                w.fill(CGRect(x: M, y: w.y - 0.5, width: W - 2 * M, height: 0.5), line)
            }
            if r.trips.isEmpty { w.paragraph("No business trips logged for \(year).", body) }
            w.y += 6
            let totals = (multi ? ["Total", "\(r.trips.count) trips", ""] : ["Total", "\(r.trips.count) trips"]) + [mi(r.miles), "", usd(r.deduction), usd(r.parking)]
            w.row(totals, tCols, attrs(9, bold: true), rule: false)
            w.y += 10
            w.paragraph("Records kept with Tax Mileage Tracker. You're responsible for the accuracy of the trips you log. This report isn't tax advice; " +
                        "check with a tax professional about your own situation.", attrs(8.5, color: muted))
        }
    }
}
