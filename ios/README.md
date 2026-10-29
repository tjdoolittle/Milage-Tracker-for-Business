# Mileage Log for iPhone

A business mileage tracker that measures your drives with GPS, even with the
screen locked and the phone in your pocket. Turn on automatic drive detection
and it notices when you start driving, tracks the trip, and adds it to a review
list. Later you tap **Business** or **Personal**.

It's the iPhone version of Mileage Log. The [web app](https://tjdoolittle.github.io/mileage-log/)
and the Android app use the same backup format, so trips move between all three.

<!-- SCREENSHOTS -->

## Features

- **Automatic drive detection.** Uses Motion & Fitness to tell when you're in
  a car, plus location wake-ups so it works when the app isn't open. The trip
  ends a minute after you get out, or after five minutes parked. Short or slow
  "drives" (under half a mile, or never above about 15 mph) are dropped.
- **Start trip / Finish trip** for manual tracking, and **+ Log trip** to
  enter a drive by miles (with round trip) or by odometer readings.
- **Street addresses** for each end of a GPS trip. Rename a spot once, like
  "Shop," and future trips use that name.
- **Rates that change midyear.** Each trip uses the rate in effect on the day
  it was driven. The IRS 2026 change is built in: 72.5¢ per mile through
  June 30, then 76¢ from July 1. Tap the rate on the green sign to add, edit,
  or remove a rate change.
- **Year picker.** Tap the year on the sign.
- **CSV export** with the rate used per trip, plus backup and restore through
  the Files app.
- Dark mode, and no account, server, or tracking. Location never leaves the phone.

## Install

The app installs through **TestFlight**. One-time Apple setup (about 10 minutes,
no Mac needed, and it reuses your Verse & Advocate certificate and API key):
[docs/apple-setup.md](docs/apple-setup.md).

After that, **Actions → iOS release → Run workflow** builds, signs, and uploads
a new version.

## Set it up on the phone

1. Open Mileage Log, tap the **gear**, and turn on **Automatic drive detection**.
2. Allow location **While Using** first. iOS asks again later; choose
   **Change to Always Allow**. Without Always, tracking can't start in your pocket.
3. Allow **Motion & Fitness** and **Notifications**.
4. Leave **Background App Refresh** on for Mileage Log, and don't swipe it away
   in the app switcher. iOS won't wake a force-quit app until you open it again.

## How it works

| Path | What it does |
| --- | --- |
| `MileageLog/Tracking/Tracker.swift` | GPS trips, distance math, automatic detection (motion, significant location changes, and a 150 m geofence where you parked) |
| `MileageLog/Tracking/Notifier.swift` | "Drive logged" notifications |
| `MileageLog/Data/` | Models (backup-compatible JSON), the store, rate schedule, CSV and backup |
| `MileageLog/UI/` | SwiftUI screens: home, trip editor, settings, rates |
| `project.yml` | XcodeGen spec; CI generates the Xcode project from it |
| `../.github/workflows/ios-build.yml` | Every push that changes `ios/`: simulator build plus screenshots with sample data |
| `../.github/workflows/ios-release.yml` | Manual: signed build uploaded to TestFlight |

Distance is the sum of hops between GPS fixes better than 50 m accuracy. It
ignores drift while parked and jumps faster than 155 mph, and bridges signal
gaps over 90 seconds in a straight line (noted on the trip).

### Building on a Mac (optional)

```bash
brew install xcodegen
xcodegen generate
open MileageLog.xcodeproj
```

Launch with the `-demo` argument for sample data (nothing is saved), and add
`-active`, `-screen editor`, or `-screen settings` to open those states.

The trips and places in the screenshots are made-up sample data.
