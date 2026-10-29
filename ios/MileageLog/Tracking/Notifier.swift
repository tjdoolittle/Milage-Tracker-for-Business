import Foundation
import UserNotifications

enum Notifier {
    static func requestPermission() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    }

    /// "Drive logged: 12.3 mi". Tapping it opens the trip for review.
    static func driveLogged(_ t: Trip) {
        let c = UNMutableNotificationContent()
        c.title = String(format: "Drive logged: %.1f mi", t.miles)
        c.body = "Tap to mark it business or personal."
        c.sound = .default
        c.userInfo = ["tripId": t.id]
        let req = UNNotificationRequest(identifier: t.id, content: c, trigger: nil)
        UNUserNotificationCenter.current().add(req)
    }
}
