import SwiftUI
import UniformTypeIdentifiers
import UserNotifications
import CoreMotion

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

    var body: some View {
        NavigationStack {
            List {
                autoDetectSection
                ratesSection
                exportSection
                if !store.data.settings.places.isEmpty { placesSection }
                Section {
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
        } header: {
            Text("Mileage rates")
        } footer: {
            Text("Each rate applies from its start date until the next one starts, and every trip uses the rate in effect on the day it was driven. IRS business rate for 2026: 72.5¢ through June 30, then 76¢ from July 1 through December 31.")
        }
    }

    private var exportSection: some View {
        let count = store.data.trips.filter { $0.status == TripStatus.logged && $0.year == year }.count
        return Section {
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
            Text("Export and backup")
        } footer: {
            Text("The CSV has date, route, purpose, miles, and the rate used for every business trip: the records the IRS asks you to keep. Trips live only on this phone; backups also restore into the Android and web apps.")
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
