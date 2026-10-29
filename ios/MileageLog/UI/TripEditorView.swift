import SwiftUI

/// Editor for a new trip, a detected drive to review, or a logged trip.
/// Saving always marks the trip as a logged business trip.
struct TripEditorView: View {
    @EnvironmentObject private var store: Store
    @Environment(\.dismiss) private var dismiss

    let original: Trip
    let isNew: Bool

    @State private var date = Date()
    @State private var odoMode = false
    @State private var roundTrip = false
    @State private var miles = ""
    @State private var odo1 = ""
    @State private var odo2 = ""
    @State private var from = ""
    @State private var to = ""
    @State private var purpose = ""
    @State private var client = ""
    @State private var notes = ""
    @State private var error: String?
    @State private var confirmDelete = false
    @State private var loaded = false

    private var reviewing: Bool { original.status == TripStatus.review }
    private var dateISO: String { Store.dayFormatter.string(from: date) }

    private var computed: Double? {
        if odoMode {
            guard let a = num(odo1), let b = num(odo2), b > a else { return nil }
            return ((b - a) * 10).rounded() / 10
        }
        guard let m = num(miles), m > 0 else { return nil }
        return roundTrip ? m * 2 : m
    }

    var body: some View {
        NavigationStack {
            Form {
                if original.source != nil && original.miles > 0 {
                    Section {
                        Label("Measured by GPS: \(Fmt.mi(original.miles)) mi", systemImage: "location.fill")
                            .font(Fonts.body(15)).foregroundStyle(Palette.signText)
                    }
                }
                Section {
                    DatePicker("Date", selection: $date, displayedComponents: .date)
                    Picker("Distance from", selection: $odoMode) {
                        Text("Miles").tag(false)
                        Text("Odometer").tag(true)
                    }
                    .pickerStyle(.segmented)
                    if odoMode {
                        TextField("Start reading", text: $odo1).keyboardType(.decimalPad)
                        TextField("End reading", text: $odo2).keyboardType(.decimalPad)
                    } else {
                        TextField(original.source != nil ? "Miles driven" : "Miles driven (one way)", text: $miles).keyboardType(.decimalPad)
                        Toggle("Round trip (doubles the miles)", isOn: $roundTrip)
                    }
                } footer: {
                    if let m = computed {
                        let r = store.rateOn(dateISO)
                        Text("\(Fmt.mi(m)) mi × \(Fmt.cents(r))¢ = \(Fmt.money(m * r)) deduction").font(Fonts.body(14))
                    }
                }

                Section("Route") {
                    TextField("From", text: $from)
                    TextField("To", text: $to)
                    Chips(options: placeNames) { if from.isEmpty { from = $0 } else { to = $0 } }
                    if !from.isEmpty && !to.isEmpty, let url = mapsURL {
                        Link(destination: url) {
                            Label("Check driving distance in Maps", systemImage: "arrow.triangle.turn.up.right.diamond")
                        }
                    }
                }

                Section {
                    TextField("Business purpose", text: $purpose)
                    Chips(options: recent(\.purpose)) { purpose = $0 }
                    TextField("Client or job (optional)", text: $client)
                    Chips(options: recent(\.client)) { client = $0 }
                    TextField("Notes (optional)", text: $notes, axis: .vertical).lineLimit(2...5)
                } header: {
                    Text("Purpose")
                } footer: {
                    Text("The IRS asks for a business purpose on every trip.")
                }

                if let error {
                    Section { Text(error).font(Fonts.bodyBold()).foregroundStyle(Palette.danger) }
                }

                if !isNew {
                    Section {
                        if original.status != TripStatus.personal {
                            Button("Mark as personal") { save(status: TripStatus.personal) }
                        }
                        Button("Delete trip", role: .destructive) { confirmDelete = true }
                    }
                }
            }
            .font(Fonts.body())
            .scrollContentBackground(.hidden)
            .background(Palette.bg)
            .navigationTitle(isNew ? "Log a trip" : reviewing ? "Review drive" : "Edit trip")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(reviewing ? "Business" : "Save") { save(status: TripStatus.logged) }.fontWeight(.bold)
                }
            }
            .confirmationDialog("Delete this trip?", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Delete", role: .destructive) { store.deleteTrip(original.id); dismiss() }
            } message: {
                Text("The \(Fmt.mi(original.miles)) mi trip from \(Fmt.longDate(original.date)) will be removed. This can't be undone.")
            }
        }
        .onAppear(perform: load)
    }

    private func load() {
        guard !loaded else { return }
        loaded = true
        let t = original
        date = Store.date(t.date)
        odoMode = t.odoStart != nil && t.source == nil
        roundTrip = t.roundTrip
        if t.miles > 0 { miles = fmt(t.roundTrip ? t.miles / 2 : t.miles) }
        odo1 = t.odoStart.map(fmt) ?? ""
        odo2 = t.odoEnd.map(fmt) ?? ""
        from = t.from; to = t.to; purpose = t.purpose; client = t.client; notes = t.notes
    }

    private func save(status: String) {
        let m = computed
        if status == TripStatus.logged {
            guard m != nil else {
                error = odoMode ? "Enter both odometer readings, with the end reading higher." : "Enter how many miles you drove."
                return
            }
            guard !purpose.trimmingCharacters(in: .whitespaces).isEmpty else {
                error = "Add a business purpose. The IRS requires one for every trip."
                return
            }
        }
        var t = original
        t.date = dateISO
        t.miles = m ?? original.miles
        t.purpose = purpose.trimmingCharacters(in: .whitespaces)
        t.client = client.trimmingCharacters(in: .whitespaces)
        t.from = from.trimmingCharacters(in: .whitespaces)
        t.to = to.trimmingCharacters(in: .whitespaces)
        t.notes = notes.trimmingCharacters(in: .whitespacesAndNewlines)
        t.roundTrip = !odoMode && roundTrip
        t.odoStart = odoMode ? num(odo1) : nil
        t.odoEnd = odoMode ? num(odo2) : nil
        t.status = status
        store.saveTrip(t)
        if status == TripStatus.logged {
            store.learnPlace(t.from, lat: original.startLat, lng: original.startLng)
            store.learnPlace(t.to, lat: original.endLat, lng: original.endLng)
        }
        dismiss()
    }

    private var placeNames: [String] {
        var seen = Set<String>()
        let names = store.data.settings.places.map(\.name) +
            store.data.trips.sorted { $0.createdAt > $1.createdAt }.flatMap { [$0.from, $0.to] }
        return names.filter { !$0.isEmpty && !Geo.looksLikeCoords($0) && seen.insert($0).inserted }.prefix(8).map { $0 }
    }

    private func recent(_ key: KeyPath<Trip, String>) -> [String] {
        var seen = Set<String>()
        return store.data.trips.sorted { $0.createdAt > $1.createdAt }.map { $0[keyPath: key] }
            .filter { !$0.isEmpty && seen.insert($0).inserted }.prefix(8).map { $0 }
    }

    private var mapsURL: URL? {
        var c = URLComponents(string: "https://maps.apple.com/")
        c?.queryItems = [URLQueryItem(name: "saddr", value: from), URLQueryItem(name: "daddr", value: to), URLQueryItem(name: "dirflg", value: "d")]
        return c?.url
    }

    private func num(_ s: String) -> Double? { Double(s.replacingOccurrences(of: ",", with: "").trimmingCharacters(in: .whitespaces)) }
    private func fmt(_ v: Double) -> String { v == v.rounded() ? String(format: "%.0f", v) : String(format: "%.1f", v) }
}

/// One-tap suggestions from earlier trips.
struct Chips: View {
    var options: [String]
    var onPick: (String) -> Void
    var body: some View {
        if !options.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(options, id: \.self) { o in
                        Button(o) { onPick(o) }
                            .font(Fonts.body(14)).foregroundStyle(Palette.ink).lineLimit(1)
                            .padding(.horizontal, 12).padding(.vertical, 6)
                            .background(Capsule().fill(Palette.bg))
                            .overlay(Capsule().stroke(Palette.line, lineWidth: 1))
                            .buttonStyle(.plain)
                    }
                }
            }
        }
    }
}
