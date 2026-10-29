# Mileage Log

A business mileage tracker for Android and iPhone. It measures your drives with
GPS, even with the screen off and the phone in your pocket, can notice drives
on its own, and totals your miles and IRS deduction for the year.

| Folder | App | How you get it |
| --- | --- | --- |
| [`android/`](android/) | Native Kotlin app | Download the APK from [Releases](https://github.com/tjdoolittle/Milage-Tracker-for-Business/releases). Every push that changes `android/` builds a new release. |
| [`ios/`](ios/) | Native SwiftUI app | TestFlight. One-time Apple setup in [ios/docs/apple-setup.md](ios/docs/apple-setup.md), then **Actions → iOS release → Run workflow**. |

There's also a web version at <https://tjdoolittle.github.io/mileage-log/>.
Its code lives with the rest of that site in the `tjdoolittle.github.io` repo.

All three use the same backup file format, so trips move between them
(Settings → Save backup, then Restore on the other device).

## What both apps do

- **Automatic drive detection.** Notices when you're driving, measures the trip
  with GPS, and adds it to a review list. You tap **Business** or **Personal**.
- **Start trip / Finish trip** for manual tracking, and **+ Log trip** to enter
  a drive by miles or odometer readings.
- **Rates that change midyear.** Each trip uses the IRS rate in effect on the
  day it was driven. 2026 is built in: 72.5¢ per mile through June 30, then 76¢
  from July 1. Tap the rate on the green sign to add or edit rate changes.
- **Year picker** on the sign, **CSV export** with the rate per trip, and
  **saved place names** (rename a spot once, like "Shop," and future trips use it).

## Builds

| Workflow | Runs when | Result |
| --- | --- | --- |
| `Android APK` | a push changes `android/` | Signed APK on the Releases page |
| `iOS build and screenshots` | a push changes `ios/` | Simulator build check plus screenshots on the `ci-ios` branch |
| `iOS release` | you start it from the Actions tab | Signed build uploaded to TestFlight |

This repo is private, so GitHub counts macOS build minutes at 10x. That's why
the iOS workflows only run when the iPhone code changes or when you start them.
