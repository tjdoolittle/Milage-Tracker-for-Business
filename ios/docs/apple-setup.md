# Getting Mileage Log onto your iPhone (TestFlight)

GitHub's Mac machines build and sign the app, so you never need a Mac. You
already did the hard part for Verse & Advocate: the Apple Developer account,
the distribution certificate, and the App Store Connect API key. Mileage Log
reuses all three. It only needs its own App ID, provisioning profile, and app
record, which takes about 10 minutes on Apple's websites.

## What you reuse and what's new

| Secret | Where it comes from |
| --- | --- |
| `APPLE_TEAM_ID` | **Same** as Verse & Advocate |
| `APPLE_DIST_CERT_P12` | **Same** `dist.p12` file as Verse & Advocate |
| `APPLE_DIST_CERT_PASSWORD` | **Same** password |
| `APPSTORE_API_KEY_ID` | **Same** key |
| `APPSTORE_API_ISSUER_ID` | **Same** issuer |
| `APPSTORE_API_PRIVATE_KEY` | **Same** `AuthKey_XXXX.p8` file |
| `APPLE_PROVISIONING_PROFILE` | **New**, step 2 below |
| `APPSTORE_APP_ID` | **New**, step 3 below |

GitHub secrets can't be copied from one repo to another, and nobody (including
you) can read a secret's value back after saving it. So you'll set them again
here from the files you saved when setting up Verse & Advocate (`dist.p12`, the
`.p8` key, and your notes of the IDs and password). If any of those files are
gone, the Verse & Advocate repo's `docs/ios-ci-setup.md` shows how to make new
ones. Don't create a new distribution certificate unless you have to: Apple
allows only three.

## 1. Register the App ID

[Identifiers](https://developer.apple.com/account/resources/identifiers/list) →
**+** → **App IDs** → **App** → Description `Mileage Log`, Bundle ID
**explicit** `com.tjdoolittle.mileagelog`. Leave every capability unchecked.
Background location and motion don't need one.

## 2. Create the provisioning profile

[Profiles](https://developer.apple.com/account/resources/profiles/list) → **+** →
**App Store Connect** (under Distribution) → pick `com.tjdoolittle.mileagelog`
→ pick your existing **Apple Distribution** certificate (the same one Verse &
Advocate uses) → name it `Mileage Log App Store` → download the
`.mobileprovision`.

Profiles expire after a year. If a release fails on signing next fall, make a
new one here and update the secret.

## 3. Create the app record

[App Store Connect](https://appstoreconnect.apple.com/apps) → **Apps** → **+**
→ **New App**: platform iOS, name `Mileage Log` (if it's taken, try
`Mileage Log by Prestige AV` or similar; the home-screen name stays
"Mileage Log"), language English (U.S.), bundle ID `com.tjdoolittle.mileagelog`,
SKU `mileagelog`, full access.

Then **App Information** → copy the **Apple ID** (a 10-digit number). That's
`APPSTORE_APP_ID`.

## 4. Add the secrets to this repo

Repo → **Settings** → **Secrets and variables** → **Actions** → **New
repository secret**, once for each of the eight in the table above.

Or, from the laptop that has the files (PowerShell, in the folder with them):

```powershell
$repo = "tjdoolittle/Milage-Tracker-for-Business"
gh secret set APPLE_TEAM_ID            -R $repo --body "YOUR_TEAM_ID"
gh secret set APPLE_DIST_CERT_PASSWORD -R $repo --body "YOUR_P12_PASSWORD"
gh secret set APPLE_DIST_CERT_P12      -R $repo --body ([Convert]::ToBase64String([IO.File]::ReadAllBytes("dist.p12")))
gh secret set APPLE_PROVISIONING_PROFILE -R $repo --body ([Convert]::ToBase64String([IO.File]::ReadAllBytes("Mileage_Log_App_Store.mobileprovision")))
gh secret set APPSTORE_API_KEY_ID      -R $repo --body "YOUR_KEY_ID"
gh secret set APPSTORE_API_ISSUER_ID   -R $repo --body "YOUR_ISSUER_ID"
gh secret set APPSTORE_API_PRIVATE_KEY -R $repo --body ([Convert]::ToBase64String([IO.File]::ReadAllBytes("AuthKey_XXXXXXXXXX.p8")))
gh secret set APPSTORE_APP_ID          -R $repo --body "1234567890"
```

Secrets are never shown in logs, and the release workflow only runs when you
start it yourself.

## 5. Build and upload

Repo → **Actions** → **iOS release** → **Run workflow**. Leave the version
fields blank. For the very first run you can untick **upload** to check that
signing works without sending anything to Apple.

It takes about 10 minutes. Then Apple processes the build for another 10 to 30
minutes.

## 6. Install it on your iPhone

1. In App Store Connect → your app → **TestFlight** → **Internal Testing** →
   **+** to make a group, and add yourself. Internal testers don't need
   Apple's review.
2. If the build shows **Missing Compliance**, answer **None of the algorithms
   mentioned above** (the app sets this automatically, so you usually won't see it).
3. Install the **TestFlight** app from the App Store, open the invite email, and
   install Mileage Log.

Each new release shows up in TestFlight as an update. Your trips stay put.

## Later: the public App Store

The App Store reviewers look closely at apps that use location **Always**. When
you submit, say in the review notes that it's a mileage log that measures
business drives automatically, and that location never leaves the phone. Under
**App Privacy**, choose **Data Not Collected**.
