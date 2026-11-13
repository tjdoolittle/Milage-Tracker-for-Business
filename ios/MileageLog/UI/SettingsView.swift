import SwiftUI
import UniformTypeIdentifiers
import UserNotifications
import CoreMotion
import CoreLocation

struct SettingsView: View {
    @EnvironmentObject private var store: Store
    @EnvironmentObject private var tracker: Tracker
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase

    let year: Int

    @State private var notificationsOn = false
    @State private var editingRate: RatePeriod?
    @State private var addingRate = false
    @State private var exportDoc: TextFile?
    @State private var exportName = ""
    @State private var exporting = false
    @State private var importing = false
    @State private var message: String?
    @State private var pdfDoc: PDFFile?
    @State private var pdfExporting = false
    @State private var reportName = ""

    var body: some View {
        NavigationStack {
            List {
                autoDetectSection
                HomeWorkSection()
                VehiclesSection(year: year)
                ratesSection
                exportSection
                if !store.data.settings.places.isEmpty { placesSection }
                Section {
                    Text(Store.responsibility + " This app isn't tax advice. " + Store.govDisclaimer).font(Fonts.body(14)).foregroundStyle(Palette.muted)
                } header: {
                    Text("Your log")
                }
                AppearanceSection()
                HelpSection()
                Section {
                    Link(destination: URL(string: "https://tjdoolittle.github.io/mileage-log/support.html")!) {
                        Label("Support", systemImage: "questionmark.circle")
                    }
                    Link(destination: URL(string: "https://tjdoolittle.github.io/mileage-log/privacy.html")!) {
                        Label("Privacy policy", systemImage: "hand.raised")
                    }
                } footer: {
                    Text("Mileage Log for iPhone · version \(appVersion)").font(Fonts.body(13)).foregroundStyle(Palette.muted)
                }
            }
            .font(Fonts.body())
            .scrollContentBackground(.hidden)
            .background(Palette.bg)
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() }.fontWeight(.bold) } }
            .sheet(item: $editingRate) { r in
                RateEditor(initial: r, canDelete: store.schedule().count > 1).environmentObject(store)
            }
            .sheet(isPresented: $addingRate) {
                RateEditor(initial: nil, canDelete: false).environmentObject(store)
            }
            .fileExporter(isPresented: $exporting, document: exportDoc, contentType: exportDoc?.type ?? .plainText, defaultFilename: exportName) { result in
                if case .success = result { message = "Saved \(exportName)" }
            }
            .fileImporter(isPresented: $importing, allowedContentTypes: [.json, .plainText, .data]) { result in
                guard case .success(let url) = result else { return }
                let scoped = url.startAccessingSecurityScopedResource()
                defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                if let raw = try? Data(contentsOf: url), let r = Exports.restore(raw, store: store) {
                    message = "Restored \(r.0) trip\(r.0 == 1 ? "" : "s"). \(r.1) were already here."
                } else {
                    message = "That file isn't a Mileage Log backup."
                }
            }
            .alert(message ?? "", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
                Button("OK", role: .cancel) {}
            }
        }
        .onAppear(perform: refresh)
        .onChange(of: scenePhase) { _, p in if p == .active { refresh() } }
    }

    private func refresh() {
        tracker.refreshAuth()
        UNUserNotificationCenter.current().getNotificationSettings { s in
            DispatchQueue.main.async { notificationsOn = s.authorizationStatus == .authorized || s.authorizationStatus == .provisional }
        }
    }

    // MARK: Sections

    private var autoDetectSection: some View {
        Section {
            Toggle(isOn: Binding(get: { store.data.settings.autoDetect }, set: { tracker.setAutoDetect($0) })) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Automatic drive detection").font(Fonts.bodyBold())
                    Text("Notices when you start driving, measures the trip with GPS, and adds it to your review list. Nothing to tap while you drive.")
                        .font(Fonts.body(14)).foregroundStyle(Palette.muted)
                }
            }
            if store.data.settings.autoDetect {
                CheckRow(title: "Location: Always", detail: "Lets tracking start with the phone in your pocket. Choose \"Change to Always Allow\" when asked.",
                         ok: store.demo || tracker.hasAlways) { tracker.requestAlways() }
                CheckRow(title: "Motion & Fitness", detail: "Lets the phone tell when you're in a car.",
                         ok: store.demo || tracker.motionAuth == .authorized) { tracker.requestMotion() }
                CheckRow(title: "Notifications", detail: "Tells you when a drive is ready to review.", ok: store.demo || notificationsOn) {
                    UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { granted, _ in
                        DispatchQueue.main.async {
                            notificationsOn = granted
                            if !granted { tracker.openSettings() }
                        }
                    }
                }
            }
        } footer: {
            if store.data.settings.autoDetect {
                Text("Leave Background App Refresh on for Mileage Log, and avoid swiping it away in the app switcher: iOS won't wake an app you've force-quit until you open it again.")
            }
        }
    }

    private var rateNote: String {
        let y = Calendar(identifier: .gregorian).component(.year, from: Date())
        if let irs = Store.irsSummary(y) { return "IRS business rate for \(y): \(irs). " }
        if store.hasRate(for: y) { return "" }
        let now = String(format: "%g", (store.rateOn(Store.today()) * 1000).rounded() / 10)
        return "The IRS rate for \(y) isn't in this version of the app yet (the IRS usually announces it in December). Trips keep using \(now)¢ until you tap Add rate change and enter the new rate starting January 1, or until an app update adds it. "
    }

    private var ratesSection: some View {
        let sch = store.schedule()
        return Section {
            ForEach(Array(sch.enumerated().reversed()), id: \.element.from) { pair in
                let i = pair.offset
                let r = pair.element
                Button { editingRate = r } label: {
                    HStack {
                        Text(i + 1 < sch.count ? "\(Fmt.longDate(r.from)) to \(Fmt.longDate(Fmt.dayBefore(sch[i + 1].from)))" : "\(Fmt.longDate(r.from)) onward")
                            .foregroundStyle(Palette.ink)
                        Spacer()
                        Text("\(Fmt.cents(r.rate))¢").font(Fonts.sign(19)).monospacedDigit().foregroundStyle(Palette.ink)
                    }
                }
            }
            Button("Add rate change") { addingRate = true }
            if store.isCustomSchedule {
                Button("Use IRS rates") { store.useIRSRates(); message = "Using the IRS rates" }
            }
            Link(destination: Store.irsSourceURL) {
                Label("Source: IRS standard mileage rates (irs.gov)", systemImage: "link")
            }
        } header: {
            Text("Mileage rates")
        } footer: {
            Text("Each rate applies from its start date until the next one starts, and every trip uses the rate in effect on the day it was driven. " + rateNote + Store.govDisclaimer)
        }
    }

    private var exportSection: some View {
        let count = store.data.trips.filter { $0.status == TripStatus.logged && $0.year == year }.count
        return Section {
            Button {
                pdfDoc = PDFFile(data: ReportPDF.make(year: year, store: store))
                pdfExporting = true
            } label: {
                Label("\(String(year)) tax report (PDF)", systemImage: "doc.richtext").fontWeight(.bold)
            }
            .disabled(count == 0)
            TextField("Name or business on the report (optional)", text: $reportName)
                .textInputAutocapitalization(.words)
                .onChange(of: reportName) { _, v in
                    var st = store.data.settings
                    guard st.reportName != v else { return }
                    st.reportName = v
                    store.setSettings(st)
                }
                .onAppear { reportName = store.data.settings.reportName }
            Button("Export \(String(year)) CSV (\(count) trips)") {
                exportDoc = TextFile(text: Exports.csv(year: year, store: store), type: .commaSeparatedText)
                exportName = "mileage-log-\(year).csv"
                exporting = true
            }
            .disabled(count == 0)
            Button("Save backup") {
                exportDoc = TextFile(text: Exports.backup(store: store), type: .json)
                exportName = "mileage-log-backup-\(Store.today()).json"
                exporting = true
            }
            Button("Restore from backup") { importing = true }
        } header: {
            Text("Tax time and backup")
        } footer: {
            Text("The tax report has your totals, deduction, parking and tolls, vehicle miles for Schedule C Part IV, a monthly table, and every trip, ready for your tax preparer. The CSV is the same trips as a spreadsheet. Trips live only on this phone; backups also restore into the Android and web apps.")
        }
        .fileExporter(isPresented: $pdfExporting, document: pdfDoc, contentType: .pdf, defaultFilename: "mileage-report-\(year).pdf") { result in
            if case .success = result { message = "Tax report saved" }
        }
    }

    private var placesSection: some View {
        Section {
            ForEach(store.data.settings.places, id: \.self) { p in Text(p.name) }
                .onDelete { idx in
                    var s = store.data.settings
                    s.places.remove(atOffsets: idx)
                    store.setSettings(s)
                }
        } header: {
            Text("Saved places")
        } footer: {
            Text("Trips that start or end within about 200 m of these get their name. Swipe left to remove one.")
        }
    }

    private var appVersion: String {
        let v = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
        let b = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? ""
        return "\(v) (\(b))"
    }
}

struct AppearanceSection: View {
    @EnvironmentObject private var store: Store
    var body: some View {
        Section {
            Picker("Theme", selection: Binding(
                get: { store.data.settings.theme },
                set: { v in var s = store.data.settings; s.theme = v; store.setSettings(s) })) {
                Text("Match phone").tag("system")
                Text("Light").tag("light")
                Text("Dark").tag("dark")
            }
            .pickerStyle(.segmented)
        } header: {
            Text("Appearance")
        }
    }
}

struct HelpSection: View {
    @EnvironmentObject private var store: Store

    private var feedbackURL: URL? {
        let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
        let b = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? ""
        let body = "\n\n---\nTax Mileage Tracker \(v) (\(b)), iOS \(UIDevice.current.systemVersion), \(UIDevice.current.model)"
        let q = { (t: String) in t.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed.subtracting(CharacterSet(charactersIn: "&=?+"))) ?? "" }
        return URL(string: "mailto:\(Store.supportEmail)?subject=\(q("Tax Mileage Tracker feedback"))&body=\(q(body))")
    }

    var body: some View {
        Section {
            ForEach(Store.faq, id: \.0) { item in
                DisclosureGroup {
                    Text(item.1).font(Fonts.body(14)).foregroundStyle(Palette.muted)
                } label: {
                    Text(item.0).font(Fonts.body()).foregroundStyle(Palette.ink)
                }
            }
            Button {
                var s = store.data.settings
                s.introDismissed = false
                store.setSettings(s)
            } label: {
                Label("Show the walkthrough again", systemImage: "book")
            }
            if let url = feedbackURL {
                Link(destination: url) { Label("Send feedback", systemImage: "envelope") }
            }
            Link(destination: Store.appStoreReviewURL) { Label("Rate the app", systemImage: "star") }
        } header: {
            Text("Help and feedback")
        }
    }
}

struct VehiclesSection: View {
    @EnvironmentObject private var store: Store
    let year: Int
    @State private var editing: Vehicle?

    var body: some View {
        Section {
            ForEach(store.data.settings.vehicles) { v in
                Button { editing = v } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(v.name).font(Fonts.body()).foregroundStyle(Palette.ink)
                        Text(odoLine(v)).font(Fonts.body(13)).foregroundStyle(Palette.muted)
                    }
                }
            }
            Button(store.data.settings.vehicles.isEmpty ? "Add your vehicle" : "Add another vehicle") {
                editing = Vehicle(id: Store.newVehicleId(), name: "")
            }
        } header: {
            Text("Vehicles")
        } footer: {
            Text("Odometer readings at the start and end of the year give the total miles Schedule C asks for. With more than one vehicle, pick one on each trip.")
        }
        .sheet(item: $editing) { v in
            VehicleEditor(vehicle: v, year: year, isNew: !store.data.settings.vehicles.contains { $0.id == v.id }).environmentObject(store)
        }
    }

    private func odoLine(_ v: Vehicle) -> String {
        let o = v.odometer[String(year)]
        if let a = o?.start, let b = o?.end { return "\(year) odometer \(Int(a).formatted()) to \(Int(b).formatted())" }
        if let a = o?.start { return "\(year) odometer starts at \(Int(a).formatted())" }
        return "No \(year) odometer readings yet"
    }
}

struct VehicleEditor: View {
    @EnvironmentObject private var store: Store
    @Environment(\.dismiss) private var dismiss
    let vehicle: Vehicle
    let year: Int
    let isNew: Bool

    @State private var name = ""
    @State private var hasInService = false
    @State private var inService = Date()
    @State private var start = ""
    @State private var end = ""
    @State private var error: String?
    @State private var confirmDelete = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Name, like 2022 Ford F-150", text: $name).textInputAutocapitalization(.words)
                }
                Section {
                    Toggle("Date first used for business", isOn: $hasInService)
                    if hasInService { DatePicker("Date", selection: $inService, displayedComponents: .date) }
                }
                Section {
                    TextField("Jan 1 reading", text: $start).keyboardType(.numberPad)
                    TextField("Dec 31 reading", text: $end).keyboardType(.numberPad)
                } header: {
                    Text("\(String(year)) odometer")
                } footer: {
                    Text("Use the first reading of the year (or when you started using it) and the last. Leave the end blank until the year is over.")
                }
                if let error { Text(error).foregroundStyle(Palette.danger) }
                if !isNew {
                    Button("Remove this vehicle", role: .destructive) { confirmDelete = true }
                }
            }
            .navigationTitle(isNew ? "Add vehicle" : "Edit vehicle")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Save") { save() }.fontWeight(.bold) }
            }
            .confirmationDialog("Remove \(vehicle.name)?", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Remove", role: .destructive) { store.deleteVehicle(vehicle.id); dismiss() }
            } message: {
                Text("Its trips stay in your log and move to your first vehicle.")
            }
            .onAppear {
                name = vehicle.name
                if let d = Store.dayFormatter.date(from: vehicle.inService) { hasInService = true; inService = d }
                let o = vehicle.odometer[String(year)]
                start = o?.start.map { String(Int($0)) } ?? ""
                end = o?.end.map { String(Int($0)) } ?? ""
            }
        }
    }

    private func save() {
        let num = { (t: String) -> Double? in Double(t.replacingOccurrences(of: ",", with: "").trimmingCharacters(in: .whitespaces)) }
        let a = num(start), b = num(end)
        if name.trimmingCharacters(in: .whitespaces).isEmpty { error = "Give the vehicle a name."; return }
        if (!start.isEmpty && a == nil) || (!end.isEmpty && b == nil) { error = "Enter odometer readings as numbers."; return }
        if let a, let b, b < a { error = "The Dec 31 reading should be higher than Jan 1."; return }
        var v = vehicle
        v.name = name.trimmingCharacters(in: .whitespaces)
        v.inService = hasInService ? Store.dayFormatter.string(from: inService) : ""
        if a == nil && b == nil { v.odometer.removeValue(forKey: String(year)) } else { v.odometer[String(year)] = YearOdo(start: a, end: b) }
        store.saveVehicle(v)
        dismiss()
    }
}

struct HomeWorkSection: View {
    @EnvironmentObject private var store: Store
    @State private var home = ""
    @State private var work = ""
    @State private var loaded = false
    @State private var note: String?

    var body: some View {
        Section {
            AddressRow(label: "Home address", text: $home, saved: store.data.settings.homeAddress) { save(home: true, $0) }
            AddressRow(label: "Work address", text: $work, saved: store.data.settings.workAddress) { save(home: false, $0) }
            if let note { Text(note).font(Fonts.body(13)).foregroundStyle(Palette.muted) }
            VStack(alignment: .leading, spacing: 4) {
                Label(Store.commuteNote, systemImage: "exclamationmark.triangle.fill")
                    .font(Fonts.bodyBold(15)).foregroundStyle(Palette.amber)
                Text("Driving between your home and your regular place of work is commuting, which the IRS doesn't allow as a deduction. Trips from work to clients, job sites, or another office usually count.")
                    .font(Fonts.body(14)).foregroundStyle(Palette.muted)
            }
        } header: {
            Text("Home and work")
        } footer: {
            Text("Saved addresses fill in From and To with one tap. GPS trips that start or end within about 200 m of them get the address too.")
        }
        .onAppear {
            guard !loaded else { return }
            loaded = true
            home = store.data.settings.homeAddress
            work = store.data.settings.workAddress
        }
    }

    private func save(home isHome: Bool, _ value: String) {
        let address = value.trimmingCharacters(in: .whitespaces)
        if address.isEmpty {
            store.setHomeWork(home: isHome, address: "", lat: nil, lng: nil)
            note = "\(isHome ? "Home" : "Work") address removed."
            return
        }
        note = "Finding it on the map…"
        CLGeocoder().geocodeAddressString(address) { marks, _ in
            DispatchQueue.main.async {
                let loc = marks?.first?.location?.coordinate
                store.setHomeWork(home: isHome, address: address, lat: loc?.latitude, lng: loc?.longitude)
                note = loc != nil ? "Saved. Trips that start or end here get this address."
                                  : "Saved. Couldn't find it on the map, so only the one-tap fill will use it."
            }
        }
    }
}

struct AddressRow: View {
    var label: String
    @Binding var text: String
    var saved: String
    var onSave: (String) -> Void

    var body: some View {
        HStack {
            TextField(label, text: $text)
                .textContentType(.fullStreetAddress)
                .submitLabel(.done)
                .onSubmit { if text.trimmingCharacters(in: .whitespaces) != saved { onSave(text) } }
            if text.trimmingCharacters(in: .whitespaces) != saved {
                Button("Save") { onSave(text) }.font(Fonts.sign(14)).buttonStyle(.borderless)
            }
        }
    }
}

struct CheckRow: View {
    var title: String
    var detail: String
    var ok: Bool
    var fix: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: ok ? "checkmark.circle.fill" : "exclamationmark.circle.fill")
                .foregroundStyle(ok ? Palette.signText : Palette.amber).font(.system(size: 20))
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(Fonts.bodyBold())
                Text(detail).font(Fonts.body(14)).foregroundStyle(Palette.muted)
            }
            Spacer(minLength: 0)
            if !ok {
                Button("Allow", action: fix).font(Fonts.sign(14)).buttonStyle(.borderless)
            }
        }
    }
}

struct RateEditor: View {
    @EnvironmentObject private var store: Store
    @Environment(\.dismiss) private var dismiss
    let initial: RatePeriod?
    let canDelete: Bool

    @State private var from = Date()
    @State private var cents = ""
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    DatePicker("Starts on", selection: $from, displayedComponents: .date)
                    HStack {
                        TextField("Cents per mile", text: $cents).keyboardType(.decimalPad)
                        Text("¢ per mile").foregroundStyle(Palette.muted)
                    }
                } footer: {
                    Text("Trips from this date on use this rate, until the next rate change.")
                }
                if let error { Section { Text(error).foregroundStyle(Palette.danger) } }
                if initial != nil && canDelete {
                    Section {
                        Button("Remove this rate", role: .destructive) {
                            store.setSchedule(store.schedule().filter { $0 != initial })
                            dismiss()
                        }
                    }
                }
            }
            .font(Fonts.body())
            .navigationTitle(initial == nil ? "Add rate change" : "Edit rate")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Save", action: save).fontWeight(.bold) }
            }
            .onAppear {
                if let initial { from = Store.date(initial.from); cents = Fmt.cents(initial.rate) }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func save() {
        guard let c = Double(cents.replacingOccurrences(of: "¢", with: "").trimmingCharacters(in: .whitespaces)), c > 0, c <= 500 else {
            error = "Enter the rate in cents per mile, like 76."
            return
        }
        let r = RatePeriod(from: Store.dayFormatter.string(from: from), rate: (c * 10).rounded() / 1000)
        let rest = store.schedule().filter { $0 != initial && $0.from != r.from }
        store.setSchedule(rest + [r])
        dismiss()
    }
}
