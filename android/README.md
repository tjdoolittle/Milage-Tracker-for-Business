# Mileage Log for Android

A business mileage tracker that measures your drives with GPS, even with the
screen off and the phone in your pocket. Turn on automatic drive detection and
it notices when you start driving, tracks the trip, and adds it to a review
list. Swipe through the list later: business or personal.

Same look and the same backup format as the [web version](https://tjdoolittle.github.io/mileage-log/),
so you can move your trips between them.

## Install it

The app isn't on the Play Store. You install the APK directly ("sideloading").

1. On your Android phone, open this repo's
   [Releases page](https://github.com/tjdoolittle/Milage-Tracker-for-Business/releases).
2. Open the newest **Mileage Log for Android** release and tap
   `MileageLog-1.0.N.apk` to download it.
3. Open the downloaded file. Android will ask to allow installs from your
   browser: tap **Settings**, turn on **Allow from this source**, go back, and
   tap **Install**.
4. If Google Play Protect warns about an unknown app, tap **More details**,
   then **Install anyway**. It warns about every app that isn't from the Play
   Store.

**Switching to the new signing key (one time):** releases from October 30,
2026 on are signed with a new key. If you installed an earlier version, Android
will refuse the update. Open Mileage Log, tap the gear, tap **Save backup**,
uninstall the app, install the new APK, then tap **Restore** and pick the backup.
You only do this once; later updates install normally.

**Updating:** download the newest release and install it the same way. It
installs over the old version and keeps your trips. Every push to this repo
that changes the app builds a new release automatically.

## Set it up

1. Open Mileage Log and tap the **gear** in the top corner.
2. Turn on **Automatic drive detection**. Allow **location**, **physical
   activity**, and **notifications** when Android asks.
3. Work through the setup checklist that appears:
   - **Location all the time:** Android opens a settings page. Choose
     **Allow all the time**. Without it, tracking can't start while the app is
     closed.
   - **Battery: unrestricted:** tap **Allow**. Without it, many phones
     (Samsung especially) block GPS from starting in the background.

Some phones need one more step. On Samsung, go to **Settings > Battery >
Background usage limits** and make sure Mileage Log isn't in "Sleeping apps" or
"Deep sleeping apps".

## Using it

**Automatic:** just drive. A "Drive detected" notification shows the miles
while you're moving. The trip ends on its own a minute after you get out of
the car, or after five minutes parked. Drives under half a mile are dropped.
You'll get a "Drive logged" notification; open it (or the app) and tap
**Business** or **Personal**. Business opens the trip so you can add the
purpose and client.

**Manual:** tap **Start trip** when you leave and **Finish trip** when you
arrive (from the app or the notification). The trip opens with the GPS miles
and the start and end addresses filled in.

**By hand:** tap **+ Log trip** to enter a past drive by miles (with a
round-trip option) or by odometer readings.

**Place names:** trips get a street address for each end. Rename one to
something like "Shop" or "Hartwell residence" and save; the app remembers that
spot and uses the name on future trips. Manage saved places in Settings.

**Taxes:** Settings > **Export CSV** saves the year's business trips with date,
route, purpose, client, miles, the rate used, and deduction, plus a totals row.

**Rates and years:** each trip uses the rate in effect on the day it was driven,
so a year can mix rates. The IRS 2026 change is built in: 72.5¢ per mile through
June 30, then 76¢ from July 1. Tap the rate on the green sign (or open Settings >
Mileage rates) to add a rate change with its start date, edit one, or switch back
to the IRS rates. Tap the year on the sign to view another tax year.

**Backups:** trips are stored only on the phone. Settings > **Save backup**
writes a file you can keep in Google Drive. **Restore** adds trips from a backup
made by this app or the web app, skipping any you already have.

## How distance is measured

The app records a GPS fix every 2 to 3 seconds and adds up the distance between
them. It ignores fixes worse than 50 m accuracy, small movements while parked
(GPS drift), and jumps faster than 155 mph. If the signal drops for more than
90 seconds (a tunnel or parking deck), it bridges the gap in a straight line and
notes the gap on the trip.

## For developers

Kotlin and Jetpack Compose, no other frameworks. Data is one JSON file in the
app's private storage.

| Path | What it does |
| --- | --- |
| `app/src/main/java/.../tracking/TrackingService.kt` | Foreground service that runs GPS and sums the distance |
| `app/src/main/java/.../tracking/Tracker.kt` | Starts, finishes, and discards trips; fills in addresses |
| `app/src/main/java/.../tracking/DriveDetector.kt` | Activity recognition (in-vehicle enter and exit) and boot handling |
| `app/src/main/java/.../data/` | Models, the JSON store, CSV and backup, distance and geocoding |
| `app/src/main/java/.../ui/` | Compose screens: home, trip editor, settings |
| `.github/workflows/android.yml` (repo root) | Builds the signed APK and publishes the release |

Build locally with Android Studio, or `gradle assembleRelease` with Gradle 8.11
and JDK 17. A local build without the signing key produces an unsigned APK,
which is fine for testing on an emulator.

### Signing

Every release must be signed with the same key, or Android won't install the
update over the app (and its trips). The key is **not** in this repo. CI reads it
from two Actions secrets:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | the keystore file `mileage-log.jks`, base64-encoded |
| `ANDROID_KEYSTORE_PASSWORD` | its password (the key uses the same one) |

Keep a copy of `mileage-log.jks` and its password somewhere safe, like a
password manager. If they're lost, a new key means uninstalling the app (save a
backup first) before installing the next release.
