import Foundation
import CoreLocation
import CoreMotion
import UIKit
import Combine

/// Measures drives with GPS and, when automatic detection is on, notices drives by itself.
///
/// Distance is the sum of hops between accurate fixes. Hops shorter than the GPS noise
/// (parked drift) and jumps faster than 155 mph are ignored. A gap of more than 90 s
/// (tunnel, parking deck) is bridged in a straight line and counted.
///
/// Automatic detection uses three wake-up sources, because iOS suspends apps:
///  - Motion & Fitness reports "automotive" while the app is running,
///  - significant location changes relaunch the app after roughly 500 m of travel,
///  - a 150 m geofence around where the last trip ended fires as soon as you drive away.
final class Tracker: NSObject, ObservableObject, CLLocationManagerDelegate {
    static let shared = Tracker()

    @Published private(set) var live: ActiveTrip?
    @Published private(set) var accuracy: Double?
    @Published private(set) var auth: CLAuthorizationStatus = .notDetermined
    @Published private(set) var motionAuth: CMAuthorizationStatus = CMMotionActivityManager.authorizationStatus()
    @Published var alert: String?

    private let manager = CLLocationManager()
    private let motion = CMMotionActivityManager()
    private var motionRunning = false
    private var updatesRunning = false
    private var lastSave = Date.distantPast
    private var vehicleExitAt: Date?
    private var stillTimer: Timer?
    private var startWhenAuthorized = false
    private var store: Store { .shared }
    private static let parkedRegion = "parked"

    private override init() {
        super.init()
        manager.delegate = self
        manager.activityType = .automotiveNavigation
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.distanceFilter = 5
        manager.pausesLocationUpdatesAutomatically = false
        auth = manager.authorizationStatus
        live = store.data.active
        if store.demo && live != nil { accuracy = 5 }
    }

    var current: ActiveTrip? { live ?? store.data.active }
    var hasLocation: Bool { auth == .authorizedAlways || auth == .authorizedWhenInUse }
    var hasAlways: Bool { auth == .authorizedAlways }
    private var nowMs: Double { Date().timeIntervalSince1970 * 1000 }

    /// Called at launch, including when iOS relaunches the app in the background for a location event.
    func resume() {
        guard !store.demo else { return }
        if store.data.settings.autoDetect { enableAutoDetect() }
        if let a = store.data.active {
            live = a
            beginUpdates()
        }
    }

    // MARK: Permissions

    func requestLocation() {
        switch auth {
        case .notDetermined: manager.requestWhenInUseAuthorization()
        case .authorizedWhenInUse: manager.requestAlwaysAuthorization()
        case .denied, .restricted: openSettings()
        default: break
        }
    }

    func requestAlways() {
        if auth == .notDetermined { manager.requestWhenInUseAuthorization() }
        else if auth == .authorizedWhenInUse { manager.requestAlwaysAuthorization() }
        else if auth != .authorizedAlways { openSettings() }
    }

    /// Asking for one activity query is what triggers the Motion & Fitness prompt.
    func requestMotion() {
        guard CMMotionActivityManager.isActivityAvailable() else { return }
        if motionAuth == .denied || motionAuth == .restricted { openSettings(); return }
        motion.queryActivityStarting(from: Date().addingTimeInterval(-60), to: Date(), to: .main) { [weak self] _, _ in
            self?.motionAuth = CMMotionActivityManager.authorizationStatus()
        }
    }

    func refreshAuth() {
        auth = manager.authorizationStatus
        motionAuth = CMMotionActivityManager.authorizationStatus()
    }

    func openSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
    }

    func locationManagerDidChangeAuthorization(_ m: CLLocationManager) {
        auth = m.authorizationStatus
        if startWhenAuthorized && hasLocation {
            startWhenAuthorized = false
            startManual()
        } else if startWhenAuthorized && (auth == .denied || auth == .restricted) {
            startWhenAuthorized = false
            alert = "Mileage Log needs location access to measure trips. Turn it on in Settings, or log trips by hand."
        }
        if hasAlways && store.data.settings.autoDetect { enableAutoDetect() }
    }

    // MARK: Manual trips

    func startManual() {
        guard hasLocation else {
            if auth == .notDetermined { startWhenAuthorized = true; manager.requestWhenInUseAuthorization() }
            else { alert = "Mileage Log needs location access to measure trips. Turn it on in Settings, or log trips by hand." }
            return
        }
        Notifier.requestPermission()
        if current == nil { set(ActiveTrip(auto: false), persist: true) }
        beginUpdates()
    }

    func discard() {
        stopUpdates()
        live = nil
        store.setActive(nil)
    }

    /// Ends the trip and saves it to the review list. Returns the trip id, or nil when an
    /// automatic drive was too short or too slow to be a drive.
    @discardableResult
    func finish(endedByStop: Bool = false) -> String? {
        guard let a = current else { stopUpdates(); return nil }
        stopUpdates()
        live = nil
        store.setActive(nil)
        if let lat = a.lastLat, let lng = a.lastLng, store.data.settings.autoDetect { watchParkedSpot(lat: lat, lng: lng) }

        let miles = (a.miles * 10).rounded() / 10
        if a.auto && (miles < store.data.settings.minAutoMiles || a.maxSpeed < 6.5) { return nil }

        let places = store.data.settings.places
        var notes = (a.auto ? "Detected automatically, " : "GPS tracked, ") + "\(a.fixes) fixes"
        if a.gaps > 0 { notes += ", \(a.gaps) signal gap\(a.gaps > 1 ? "s" : "")" }
        var t = Trip(id: Store.newId(), date: Store.isoDate(ms: a.startedAt), miles: miles)
        t.purpose = a.purpose
        t.client = a.client
        t.from = a.from.isEmpty ? (Geo.savedPlace(a.startLat, a.startLng, in: places)?.name ?? "") : a.from
        t.to = Geo.savedPlace(a.lastLat, a.lastLng, in: places)?.name ?? ""
        t.notes = notes
        t.source = a.auto ? "Auto GPS" : "GPS"
        t.status = TripStatus.review
        t.startTime = a.startedAt
        t.endTime = endedByStop ? a.lastMoveAt : nowMs
        t.startLat = a.startLat; t.startLng = a.startLng
        t.endLat = a.lastLat; t.endLng = a.lastLng
        store.saveTrip(t)
        fillAddresses(t)
        if a.auto { Notifier.driveLogged(t) }
        return t.id
    }

    private func fillAddresses(_ t: Trip) {
        guard t.from.isEmpty || t.to.isEmpty else { return }
        let places = store.data.settings.places
        Task {
            let from = t.from.isEmpty ? await Geo.label(t.startLat, t.startLng, places: places) : t.from
            let to = t.to.isEmpty ? await Geo.label(t.endLat, t.endLng, places: places) : t.to
            await MainActor.run {
                Store.shared.update { d in
                    if let i = d.trips.firstIndex(where: { $0.id == t.id }) {
                        if d.trips[i].from.isEmpty { d.trips[i].from = from }
                        if d.trips[i].to.isEmpty { d.trips[i].to = to }
                    }
                }
            }
        }
    }

    // MARK: Automatic detection

    func setAutoDetect(_ on: Bool) {
        var s = store.data.settings
        s.autoDetect = on
        store.setSettings(s)
        if on {
            Notifier.requestPermission()
            requestMotion()
            requestAlways()
            enableAutoDetect()
        } else {
            disableAutoDetect()
        }
    }

    private func enableAutoDetect() {
        guard hasAlways else { return }
        if CLLocationManager.significantLocationChangeMonitoringAvailable() {
            manager.startMonitoringSignificantLocationChanges()
        }
        startMotionUpdates()
    }

    private func disableAutoDetect() {
        manager.stopMonitoringSignificantLocationChanges()
        if motionRunning { motion.stopActivityUpdates(); motionRunning = false }
        for r in manager.monitoredRegions where r.identifier == Tracker.parkedRegion { manager.stopMonitoring(for: r) }
    }

    private func startMotionUpdates() {
        guard CMMotionActivityManager.isActivityAvailable(), !motionRunning else { return }
        motionRunning = true
        motion.startActivityUpdates(to: .main) { [weak self] a in
            guard let self, let a else { return }
            self.onActivity(a)
        }
    }

    private func onActivity(_ a: CMMotionActivity) {
        motionAuth = CMMotionActivityManager.authorizationStatus()
        guard store.data.settings.autoDetect, a.confidence != .low else { return }
        if a.automotive {
            vehicleExitAt = nil
            if current == nil { startAuto() }
        } else if (a.walking || a.running || a.cycling || a.stationary) && current?.auto == true {
            if vehicleExitAt == nil { vehicleExitAt = Date() }
        }
    }

    /// Woken by a significant location change: check whether we're driving.
    private func considerWake(_ loc: CLLocation) {
        guard store.data.settings.autoDetect, current == nil else { return }
        if loc.speed > 6 { startAuto(); return }
        guard CMMotionActivityManager.isActivityAvailable() else { return }
        motion.queryActivityStarting(from: Date().addingTimeInterval(-180), to: Date(), to: .main) { [weak self] acts, _ in
            guard let self, let last = acts?.last(where: { $0.confidence != .low }) else { return }
            if last.automotive && self.current == nil { self.startAuto() }
        }
    }

    private func startAuto() {
        guard current == nil, hasAlways else { return }
        set(ActiveTrip(auto: true), persist: true)
        for r in manager.monitoredRegions where r.identifier == Tracker.parkedRegion { manager.stopMonitoring(for: r) }
        beginUpdates()
    }

    private func watchParkedSpot(lat: Double, lng: Double) {
        guard hasAlways, CLLocationManager.isMonitoringAvailable(for: CLCircularRegion.self) else { return }
        let region = CLCircularRegion(center: CLLocationCoordinate2D(latitude: lat, longitude: lng), radius: 150, identifier: Tracker.parkedRegion)
        region.notifyOnExit = true
        region.notifyOnEntry = false
        manager.startMonitoring(for: region)
    }

    func locationManager(_ m: CLLocationManager, didExitRegion region: CLRegion) {
        guard region.identifier == Tracker.parkedRegion, store.data.settings.autoDetect, current == nil else { return }
        startAuto()
    }

    // MARK: GPS updates

    private func beginUpdates() {
        guard hasLocation else { return }
        if Bundle.main.object(forInfoDictionaryKey: "UIBackgroundModes") != nil {
            manager.allowsBackgroundLocationUpdates = true
        }
        manager.showsBackgroundLocationIndicator = true
        if !updatesRunning {
            manager.startUpdatingLocation()
            updatesRunning = true
        }
        stillTimer?.invalidate()
        stillTimer = Timer.scheduledTimer(withTimeInterval: 20, repeats: true) { [weak self] _ in self?.checkAutoStop() }
    }

    private func stopUpdates() {
        if updatesRunning { manager.stopUpdatingLocation(); updatesRunning = false }
        manager.allowsBackgroundLocationUpdates = false
        stillTimer?.invalidate(); stillTimer = nil
        accuracy = nil
        vehicleExitAt = nil
    }

    func locationManager(_ m: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        if current == nil {
            if let last = locations.last { considerWake(last) }
            return
        }
        for loc in locations { onFix(loc) }
        checkAutoStop()
    }

    func locationManager(_ m: CLLocationManager, didFailWithError error: Error) {
        if (error as? CLError)?.code == .denied { stopUpdates() }
    }

    private func onFix(_ loc: CLLocation) {
        guard var a = current, loc.horizontalAccuracy >= 0 else { return }
        let acc = loc.horizontalAccuracy
        accuracy = acc
        if acc > 50 { return }
        let t = loc.timestamp.timeIntervalSince1970 * 1000
        a.fixes += 1
        if loc.speed > a.maxSpeed && loc.speed < 70 { a.maxSpeed = loc.speed }
        if a.startLat == nil { a.startLat = loc.coordinate.latitude; a.startLng = loc.coordinate.longitude; a.lastMoveAt = t }
        if let pLat = a.lastLat, let pLng = a.lastLng {
            let d = Geo.meters(pLat, pLng, loc.coordinate.latitude, loc.coordinate.longitude)
            let dt = (t - a.lastFixAt) / 1000
            if d < max(10, acc * 0.6) {
                // parked or GPS drift: keep the old anchor
            } else if dt > 0 && d / dt > 70 {
                // faster than 155 mph: a bad fix
            } else {
                a.meters += d
                a.lastLat = loc.coordinate.latitude; a.lastLng = loc.coordinate.longitude
                a.lastMoveAt = t
                if dt > 90 { a.gaps += 1 }
            }
        } else {
            a.lastLat = loc.coordinate.latitude; a.lastLng = loc.coordinate.longitude
        }
        a.lastFixAt = t
        let persist = Date().timeIntervalSince(lastSave) > 15
        if persist { lastSave = Date() }
        set(a, persist: persist)
    }

    /// Automatic trips end on their own: a minute after leaving the car, or after 5 minutes parked.
    private func checkAutoStop() {
        guard let a = current, a.auto else { return }
        let stillFor = (nowMs - a.lastMoveAt) / 1000
        if (vehicleExitAt != nil && stillFor > 60) || stillFor > 300 {
            finish(endedByStop: true)
        }
    }

    private func set(_ a: ActiveTrip, persist: Bool) {
        live = a
        if persist { store.setActive(a) }
    }
}
