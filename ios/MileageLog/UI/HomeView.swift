import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var store: Store
    @EnvironmentObject private var tracker: Tracker
    @EnvironmentObject private var router: Router
    @State private var year = Calendar.current.component(.year, from: Date())
    @State private var pickYear = false

    private var curYear: Int { Calendar.current.component(.year, from: Date()) }
    private var isCur: Bool { year == curYear }

    var body: some View {
        let logged = store.data.trips
            .filter { $0.status == TripStatus.logged && $0.year == year }
            .sorted { ($0.date, $0.startTime ?? $0.createdAt) > ($1.date, $1.startTime ?? $1.createdAt) }
        let review = store.data.trips.filter { $0.status == TripStatus.review }
            .sorted { ($0.startTime ?? $0.createdAt) > ($1.startTime ?? $1.createdAt) }
        let total = logged.reduce(0) { $0 + $1.miles }
        let totalDed = logged.reduce(0) { $0 + store.deduction($1) }
        let byMonth = (1...12).map { m in logged.filter { $0.month == m }.reduce(0) { $0 + $1.miles } }
        let nowMonth = Calendar.current.component(.month, from: Date()) - 1
        let rate = store.rateOn(isCur ? Store.today() : "\(year)-12-31")
        let groups = Dictionary(grouping: logged) { String($0.date.prefix(7)) }.sorted { $0.key > $1.key }

        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header
                SignCard(
                    year: year, miles: total, deduction: totalDed, rate: rate,
                    statMid: isCur ? Fmt.mi(byMonth[nowMonth]) : Fmt.mi(total / 12),
                    statMidLabel: isCur ? "\(Fmt.mon[nowMonth]) miles" : "Avg / month",
                    trips: logged.count, byMonth: byMonth, highlight: isCur ? nowMonth : nil,
                    onYear: { pickYear = true }, onRate: { router.showSettings = true }
                )
                if let a = tracker.live ?? store.data.active {
                    ActiveBanner(trip: a, accuracy: tracker.accuracy) { tracker.discard() }
                }
                if !review.isEmpty {
                    HStack {
                        SignLabel(text: "To review", color: Palette.amber)
                        Spacer()
                        Text("\(review.count) drive\(review.count > 1 ? "s" : "")").font(Fonts.body(14)).foregroundStyle(Palette.muted)
                    }
                    .padding(.top, 4)
                    ForEach(review) { t in
                        ReviewCard(trip: t, deduction: store.deduction(t),
                                   onBusiness: { router.editing = .init(trip: t, isNew: false) },
                                   onPersonal: { var p = t; p.status = TripStatus.personal; store.saveTrip(p) })
                    }
                }
                if logged.isEmpty {
                    EmptyState(year: year)
                } else {
                    ForEach(groups, id: \.key) { g in
                        MonthSection(key: g.key, trips: g.value)
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .padding(.bottom, 24)
        }
        .background(Palette.bg.ignoresSafeArea())
        .safeAreaInset(edge: .bottom) { actionBar }
        .sheet(item: $router.editing) { e in
            TripEditorView(original: e.trip, isNew: e.isNew)
                .environmentObject(store)
        }
        .sheet(isPresented: $router.showSettings) {
            SettingsView(year: year)
                .environmentObject(store)
                .environmentObject(tracker)
        }
        .confirmationDialog("Tax year", isPresented: $pickYear, titleVisibility: .visible) {
            ForEach(pickableYears, id: \.self) { y in
                Button(y == year ? "\(String(y)) ✓" : String(y)) { year = y }
            }
        }
        .alert("Location is off", isPresented: Binding(get: { tracker.alert != nil }, set: { if !$0 { tracker.alert = nil } })) {
            Button("Open Settings") { tracker.openSettings() }
            Button("Not now", role: .cancel) {}
        } message: {
            Text(tracker.alert ?? "")
        }
    }

    private var pickableYears: [Int] {
        let base = Set((curYear - 4)...(curYear + 1))
        return Array(base.union(store.data.trips.map(\.year).filter { $0 > 2000 })).sorted(by: >)
    }

    private var header: some View {
        HStack(spacing: 8) {
            Text("MI").font(Fonts.sign(8)).foregroundStyle(Palette.signInk)
                .frame(width: 22, height: 22).background(RoundedRectangle(cornerRadius: 5).fill(Palette.sign))
            SignLabel(text: "Mileage Log", size: 15, color: Palette.ink)
            Spacer()
            Button { router.showSettings = true } label: {
                Image(systemName: "gearshape").font(.system(size: 20, weight: .semibold)).foregroundStyle(Palette.ink)
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel("Settings and export")
        }
    }

    private var actionBar: some View {
        VStack(spacing: 0) {
            Rectangle().fill(Palette.line).frame(height: 1)
            HStack(spacing: 10) {
                if tracker.live != nil || store.data.active != nil {
                    Button("Log trip") { router.newTrip() }.buttonStyle(BigButtonStyle(kind: .outline))
                    Button("Finish trip") {
                        if let id = tracker.finish() { router.open(tripId: id) }
                    }
                    .buttonStyle(BigButtonStyle(kind: .amber))
                } else {
                    Button("Start trip") { tracker.startManual() }.buttonStyle(BigButtonStyle(kind: .outline))
                    Button("+ Log trip") { router.newTrip() }.buttonStyle(BigButtonStyle(kind: .primary))
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .background(Palette.bg)
    }
}

// MARK: - Sign

struct SignCard: View {
    var year: Int
    var miles: Double
    var deduction: Double
    var rate: Double
    var statMid: String
    var statMidLabel: String
    var trips: Int
    var byMonth: [Double]
    var highlight: Int?
    var onYear: () -> Void
    var onRate: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Button(action: onYear) { SignLabel(text: "\(String(year)) ▾  Business miles", color: Palette.signInk) }
                    .accessibilityLabel("Tax year \(String(year)). Change year")
                Spacer()
                Button(action: onRate) { SignLabel(text: "\(Fmt.cents(rate))¢ / mi ▾", color: Palette.signInk) }
                    .accessibilityLabel("Rate \(Fmt.cents(rate)) cents per mile. Change rates")
            }
            Odometer(miles: miles)
            Rectangle().fill(Palette.signInk.opacity(0.35)).frame(height: 1.5)
            HStack(alignment: .top, spacing: 10) {
                stat(Fmt.money(deduction), "Deduction").frame(maxWidth: .infinity, alignment: .leading)
                stat(statMid, statMidLabel).frame(maxWidth: .infinity, alignment: .leading)
                stat("\(trips)", "Trips").frame(width: 58, alignment: .leading)
            }
            VStack(spacing: 4) {
                let max = Swift.max(byMonth.max() ?? 0, 1)
                HStack(alignment: .bottom, spacing: 4) {
                    ForEach(0..<12, id: \.self) { i in
                        UnevenRoundedRectangle(topLeadingRadius: 2, topTrailingRadius: 2)
                            .fill(i == highlight ? Palette.signInk : Palette.signInk.opacity(0.22))
                            .frame(height: Swift.max(2, 46 * byMonth[i] / max))
                            .frame(maxWidth: .infinity)
                    }
                }
                .frame(height: 46, alignment: .bottom)
                HStack(spacing: 4) {
                    ForEach(Array("JFMAMJJASOND").indices, id: \.self) { i in
                        Text(String(Array("JFMAMJJASOND")[i])).font(Fonts.sign(10)).foregroundStyle(Palette.signSoft).frame(maxWidth: .infinity)
                    }
                }
            }
            .accessibilityHidden(true)
        }
        .padding(16)
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Palette.signInk, lineWidth: 2.5))
        .padding(6)
        .background(RoundedRectangle(cornerRadius: 14).fill(Palette.sign))
    }

    private func stat(_ value: String, _ label: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value).font(Fonts.sign(22)).monospacedDigit().foregroundStyle(Palette.signInk).lineLimit(1).minimumScaleFactor(0.6)
            SignLabel(text: label, size: 11, color: Palette.signSoft).lineLimit(1).minimumScaleFactor(0.7)
        }
    }
}

/// Mechanical odometer: six white-on-black wheels and a black-on-white tenths wheel.
struct Odometer: View {
    var miles: Double
    var body: some View {
        let digits = Array(String(format: "%08.1f", min(max(miles, 0), 999_999.9)).replacingOccurrences(of: ".", with: ""))
        HStack(alignment: .bottom, spacing: 3) {
            ForEach(digits.indices, id: \.self) { i in
                let tenth = i == digits.count - 1
                Text(String(digits[i]))
                    .font(Fonts.mono(34))
                    .contentTransition(.numericText())
                    .foregroundStyle(tenth ? Palette.odoBg : Palette.odoFg)
                    .frame(width: 27, height: 42)
                    .background(RoundedRectangle(cornerRadius: 3).fill(tenth ? Palette.odoFg : Palette.odoBg))
                    .overlay(LinearGradient(colors: [.black.opacity(0.45), .clear, .clear, .black.opacity(0.45)], startPoint: .top, endPoint: .bottom)
                        .clipShape(RoundedRectangle(cornerRadius: 3)))
                    .padding(.leading, tenth ? 3 : 0)
            }
            SignLabel(text: "mi", size: 13, color: Palette.odoFg).padding(.leading, 6).padding(.trailing, 2)
        }
        .animation(.easeOut(duration: 0.6), value: miles)
        .padding(7)
        .background(RoundedRectangle(cornerRadius: 8).fill(Palette.odoBg))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(Fmt.mi(miles)) miles")
    }
}

// MARK: - Rows

struct ActiveBanner: View {
    var trip: ActiveTrip
    var accuracy: Double?
    var onDiscard: () -> Void

    var body: some View {
        let since = Date(timeIntervalSince1970: trip.startedAt / 1000).formatted(date: .omitted, time: .shortened)
        let status: String = {
            guard let acc = accuracy else { return "Finding your location…" }
            return acc > 50 ? "Weak GPS signal (±\(Int(acc)) m)" : "GPS on (±\(Int(acc)) m)"
        }()
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                SignLabel(text: trip.auto ? "Drive detected" : "Tracking with GPS", color: Palette.amber)
                Text("\(Fmt.mi(trip.miles)) mi").font(Fonts.sign(30)).monospacedDigit().foregroundStyle(Palette.ink)
                Text("Since \(since) · \(status)" + (trip.gaps > 0 ? " · \(trip.gaps) signal gap\(trip.gaps > 1 ? "s" : "")" : ""))
                    .font(Fonts.body(14)).foregroundStyle(Palette.ink)
            }
            Spacer(minLength: 0)
            Button("Discard", action: onDiscard).font(Fonts.sign(13)).tint(Palette.ink)
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
        .background(RoundedRectangle(cornerRadius: 12).fill(Palette.amberBg))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.amber, lineWidth: 1.5))
    }
}

struct ReviewCard: View {
    var trip: Trip
    var deduction: Double
    var onBusiness: () -> Void
    var onPersonal: () -> Void

    var body: some View {
        let when: String = {
            if let s = trip.startTime {
                let d = Date(timeIntervalSince1970: s / 1000)
                return d.formatted(.dateTime.weekday(.abbreviated).month(.abbreviated).day()) + " · " + d.formatted(date: .omitted, time: .shortened)
            }
            return Fmt.longDate(trip.date)
        }()
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(when).font(Fonts.bodyBold()).foregroundStyle(Palette.ink)
                Spacer()
                Text("\(Fmt.mi(trip.miles)) mi").font(Fonts.sign(19)).monospacedDigit().foregroundStyle(Palette.ink)
            }
            Text("\(trip.from.isEmpty ? "Start" : trip.from) → \(trip.to.isEmpty ? "End" : trip.to)")
                .font(Fonts.body(14)).foregroundStyle(Palette.muted)
            HStack(spacing: 10) {
                Button("Personal", action: onPersonal)
                    .font(Fonts.bodyBold()).frame(maxWidth: .infinity, minHeight: 44)
                    .foregroundStyle(Palette.ink)
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Palette.line, lineWidth: 1.5))
                Button(action: onBusiness) {
                    Text("Business · \(Fmt.money(deduction))").font(Fonts.bodyBold()).lineLimit(1).minimumScaleFactor(0.8)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .foregroundStyle(Palette.signInk)
                        .background(RoundedRectangle(cornerRadius: 10).fill(Palette.sign))
                }
            }
            .buttonStyle(.plain)
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 12).fill(Palette.surface))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.line, lineWidth: 1.5))
    }
}

struct MonthSection: View {
    @EnvironmentObject private var store: Store
    @EnvironmentObject private var router: Router
    var key: String
    var trips: [Trip]

    var body: some View {
        let sum = trips.reduce(0) { $0 + $1.miles }
        let ded = trips.reduce(0) { $0 + store.deduction($1) }
        VStack(spacing: 0) {
            HStack {
                SignLabel(text: Fmt.months[(Int(key.suffix(2)) ?? 1) - 1])
                Spacer()
                SignLabel(text: "\(Fmt.mi(sum)) mi · \(Fmt.money(ded))", color: Palette.ink)
            }
            .padding(.horizontal, 2).padding(.vertical, 6)
            Rectangle().fill(Palette.ink).frame(height: 2)
            ForEach(trips) { t in
                Button { router.editing = .init(trip: t, isNew: false) } label: { TripRow(trip: t, deduction: store.deduction(t)) }
                    .buttonStyle(.plain)
                Rectangle().fill(Palette.line).frame(height: 1)
            }
        }
    }
}

struct TripRow: View {
    var trip: Trip
    var deduction: Double

    var body: some View {
        let d = Store.date(trip.date)
        let day = Calendar.current.component(.day, from: d)
        let route = [trip.from, trip.to].filter { !$0.isEmpty }.joined(separator: " → ")
        let sub = [trip.client, route].filter { !$0.isEmpty }.joined(separator: " · ")
        HStack(spacing: 12) {
            VStack(spacing: 0) {
                Text("\(day)").font(Fonts.sign(22)).monospacedDigit().foregroundStyle(Palette.ink)
                SignLabel(text: d.formatted(.dateTime.weekday(.abbreviated)), size: 11)
            }
            .frame(width: 44)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(trip.purpose.isEmpty ? "Business trip" : trip.purpose).font(Fonts.bodyBold()).foregroundStyle(Palette.ink).lineLimit(1)
                    if trip.roundTrip { Tag(text: "RT") }
                    if trip.source != nil { Tag(text: "GPS") }
                }
                Text(sub.isEmpty ? "No route entered" : sub).font(Fonts.body(14)).foregroundStyle(Palette.muted).lineLimit(1)
            }
            Spacer(minLength: 4)
            VStack(alignment: .trailing, spacing: 2) {
                Text(Fmt.mi(trip.miles)).font(Fonts.sign(19)).monospacedDigit().foregroundStyle(Palette.ink)
                Text(Fmt.money(deduction)).font(Fonts.body(12)).foregroundStyle(Palette.muted)
            }
        }
        .padding(.vertical, 12).padding(.horizontal, 2)
        .contentShape(Rectangle())
    }
}

struct Tag: View {
    var text: String
    var body: some View {
        Text(text).font(Fonts.sign(10)).foregroundStyle(Palette.muted)
            .padding(.horizontal, 4).padding(.vertical, 1)
            .overlay(RoundedRectangle(cornerRadius: 4).stroke(Palette.line, lineWidth: 1))
    }
}

struct EmptyState: View {
    var year: Int
    var body: some View {
        VStack(spacing: 8) {
            Text("No \(String(year)) trips logged yet").font(Fonts.sign(19)).foregroundStyle(Palette.ink).multilineTextAlignment(.center)
            Text("Tap Start trip when you head out, or turn on automatic drive detection in Settings and Mileage Log will notice when you drive.")
                .font(Fonts.body()).foregroundStyle(Palette.muted).multilineTextAlignment(.center)
        }
        .padding(.horizontal, 20).padding(.vertical, 26)
        .frame(maxWidth: .infinity)
        .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Palette.line, style: StrokeStyle(lineWidth: 2, dash: [6, 5])))
    }
}
