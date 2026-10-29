import SwiftUI
import UserNotifications

@main
struct MileageLogApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            HomeView()
                .environmentObject(Store.shared)
                .environmentObject(Tracker.shared)
                .environmentObject(Router.shared)
                .tint(Palette.signText)
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { Tracker.shared.refreshAuth() }
        }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        // Also runs when iOS relaunches the app in the background for a location event.
        Tracker.shared.resume()
        return true
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .sound])
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        if let id = response.notification.request.content.userInfo["tripId"] as? String {
            DispatchQueue.main.async { Router.shared.open(tripId: id) }
        }
        completionHandler()
    }
}

/// Which sheet is showing. Shared so notifications can open a trip.
final class Router: ObservableObject {
    static let shared = Router()

    struct Editing: Identifiable {
        var trip: Trip
        var isNew: Bool
        var id: String { trip.id }
    }

    @Published var editing: Editing?
    @Published var showSettings = false

    private init() {
        // Screenshot mode can open a screen directly: -screen settings | editor
        let args = ProcessInfo.processInfo.arguments
        if let i = args.firstIndex(of: "-screen"), i + 1 < args.count {
            switch args[i + 1] {
            case "settings": showSettings = true
            case "editor":
                if let t = Store.shared.data.trips.first(where: { $0.status == TripStatus.review }) { editing = Editing(trip: t, isNew: false) }
            default: break
            }
        }
    }

    func open(tripId: String) {
        if let t = Store.shared.data.trips.first(where: { $0.id == tripId }) {
            showSettings = false
            editing = Editing(trip: t, isNew: false)
        }
    }

    func newTrip() {
        editing = Editing(trip: Trip(id: Store.newId(), date: Store.today(), miles: 0), isNew: true)
    }
}
