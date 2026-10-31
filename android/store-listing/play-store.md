# Google Play: Mileage Log

Everything Play Console asks for, in the order you'll meet it. Character limits
are Google's; every field here fits.

Files in this folder:

| File | Where it goes |
| --- | --- |
| `graphics/icon-512.png` | Store listing → App icon (512 x 512) |
| `graphics/feature-graphic-1024x500.png` | Store listing → Feature graphic (required) |
| `screenshots/1-...png` to `5-...png` | Store listing → Phone screenshots, in order (1080 x 1920) |

The app bundle to upload is the `.aab` on the
[Releases page](https://github.com/tjdoolittle/Milage-Tracker-for-Business/releases)
(each Android release now has both an `.apk` and an `.aab`).

---

## 1. Create the app

Play Console → **Create app**:

- App name: `Mileage Log: Business Miles`
- Default language: English (United States)
- App or game: **App**
- Free or paid: **Free**
- Tick both declarations, then **Create app**.

## 2. Store listing (Grow users → Store presence → Main store listing)

**App name (30)**

```
Mileage Log: Business Miles
```

**Short description (80)**

```
Logs business drives with GPS and adds up your IRS mileage deduction.
```

**Full description (4,000)**

```
Mileage Log keeps the mileage records the IRS asks for, without the paperwork. It measures your drives with GPS, even with the phone in your pocket, and adds up your business miles and deduction for the year.

LOGS DRIVES ON ITS OWN
Turn on automatic drive detection and Mileage Log notices when you start driving, measures the trip, and ends it when you park. Each drive lands in a review list with its miles and start and end addresses. Tap Business or Personal. That's it.

OR TRACK IT YOURSELF
Tap Start trip when you leave and Finish trip when you arrive. Or tap + Log trip to add a drive by miles, round trip, or odometer readings.

RECORDS THE IRS EXPECTS
Every business trip keeps the date, where you started and ended, the miles, and the business purpose. Pick a purpose like Client Visits, Job Site Visits, Sales Calls, or Deliveries, or type your own. Add a client or job and notes if you like.

HOME AND WORK, ONE TAP
Save your home and work addresses and fill in a trip's route with one tap. Regular everyday commuting isn't deductible, and Mileage Log flags trips between home and work so you can mark them personal.

RATES THAT CHANGE MIDYEAR
Each trip uses the IRS standard mileage rate in effect on the day you drove it. The 2026 change is built in: 72.5 cents per mile through June 30, then 76 cents from July 1. Add your own rate changes whenever the IRS announces one.

YOUR YEAR AT A GLANCE
A highway-sign odometer shows the year's business miles, your deduction, this month's miles, and a month-by-month chart. Tap the year to look back at earlier tax years.

READY FOR TAX TIME
Export any year as a spreadsheet (CSV) with the date, route, purpose, miles, rate, and deduction for every trip, plus totals. Save a backup to Google Drive or Files and restore it on a new phone.

PRIVATE BY DESIGN
No account, no ads, no tracking. Your trips and location stay on your phone unless you export them.

Mileage Log tracks your trips, but you're responsible for the data you log. It helps you keep records and isn't tax advice; check with a tax professional about your own situation.
```

**Graphics:** upload the icon, feature graphic, and the five phone screenshots
(in order). Tablet screenshots are optional; skip them.

**Category and contact (Store settings):**

| Field | Value |
| --- | --- |
| App category | **Business** |
| Tags | Business, Productivity, Finance (pick what's offered) |
| Email | `doolittle.thomas+apps@gmail.com` |
| Website | `https://tjdoolittle.github.io/mileage-log/` |

## 3. App content (Policy → App content)

Work through every item until each shows a green check.

| Item | Answer |
| --- | --- |
| Privacy policy | `https://tjdoolittle.github.io/mileage-log/privacy.html` |
| Ads | **No**, my app does not contain ads |
| App access | **All functionality is available without special access** |
| Content rating | Start questionnaire. Category: **All other app types** (utility/productivity). Answer **No** to every question. Result: Everyone / 3+ |
| Target audience | **18 and over** |
| News app | **No** |
| Data safety | See below |
| Government app | **No** |
| Financial features | **My app doesn't provide any financial features** |
| Health | None |
| Location permissions | See section 4 |
| Foreground service permissions | See section 4 |

**Data safety form:**

- "Does your app collect or share any of the required user data types?" → **No**.
  Location, trips, and settings are processed and stored only on the phone and
  never sent to you or anyone else, which Google doesn't count as "collected."
- That's the whole form for an app that collects nothing. Save and submit it.

## 4. Location and foreground-service declarations

These two forms appear because the app uses background location (automatic
detection) and a location foreground service (measuring a trip with the screen
off). Each needs a **short video** (30 to 90 seconds) uploaded to YouTube as
**Unlisted**.

### Video shot list (record on your phone's screen recorder)

1. Open Mileage Log, tap the **gear**, turn on **Automatic drive detection**.
2. The **"Location for automatic trips"** explanation appears. Pause on it for
   a second so the text is readable, then tap **Continue**.
3. Android asks for location: choose **While using the app**. Then, from the
   setup checklist, tap **Allow** next to *Location all the time* and choose
   **Allow all the time** on the settings page.
4. Show a drive being measured: tap **Start trip** on the home screen, pull
   down the notification shade to show the **"Tracking trip"** notification,
   then come back and tap **Finish trip**. (Or, as a passenger, record the
   "Drive detected" notification during a real drive. Don't record while you're
   the one driving.)
5. Show the trip in the review list and tap **Business**.

One video covering all five steps works for both forms.

### Location permissions form (App content → Location permissions)

- **Which feature uses background location?**
  ```
  Automatic drive detection for a business mileage log. When the user turns it on, the app notices when they start driving and measures the trip's distance with GPS while the phone is in a pocket or bag, then adds the trip to a review list where the user marks it Business or Personal.
  ```
- **Why does it need location in the background (not only while in use)?**
  ```
  Drives start while the app is closed and the phone is put away. Without background access the app can't notice a drive starting or measure its miles, so trips would be missing from the mileage log the user keeps for IRS records.
  ```
- **How is the user told?** The in-app explanation shown before any location
  prompt ("Mileage Log collects location data to detect your drives and measure
  their miles for your mileage log, even when the app is closed or not in use").
  Automatic detection is off by default.
- **Video:** the unlisted YouTube link.

### Foreground service permissions form

- Type: **Location**.
- Pick the use case that describes **user-started trip / route tracking**
  (the user starts the trip, or turns on automatic detection, and a
  notification shows the whole time a trip is measured).
- Description:
  ```
  Measures the distance of a drive while it happens, for the user's mileage log. The service runs only during a trip (started by the user or by automatic drive detection the user turned on), shows an ongoing "Tracking trip" notification with the live miles and a Finish button, and stops when the trip ends.
  ```
- **Video:** the same unlisted YouTube link.

## 5. Testing, then production

**If your Play developer account is a personal account created after
November 13, 2023**, Google requires a **closed test with at least 12 testers
opted in for 14 days in a row** before you can publish to everyone. (Play
Console tells you on the Dashboard if this applies.)

1. **Test and release → Testing → Closed testing → Create track** (or use the
   default "Alpha").
2. **Testers:** add an email list with at least 12 people (Adam, Chase,
   Carolyn, and more), or a Google Group. Copy the **opt-in link** and send it to
   them. Each tester opens it on their Android phone, taps **Accept**, then
   installs from Play.
3. **Create release:** upload the `.aab` from the latest GitHub release. The
   first upload turns on **Play App Signing**: Google keeps the app's signing key
   and your GitHub key becomes the upload key. Release notes: `First test
   release.`
4. **Review and roll out.** Google reviews the first release (usually a few
   days, longer with background location).
5. After 14 days with 12+ testers, Dashboard → **Apply for production**.
   Google usually answers within 7 days.

If your account isn't covered by that rule, you can skip the 14 days: create a
**Production** release with the same `.aab` and send it for review.

### Moving your own phone to the Play version

Play signs the app with Google's key, which is different from the sideloaded
APKs, so Android won't install one over the other. When you switch: open
Mileage Log → Settings → **Save backup**, uninstall, install from Play, then
**Restore**.

### Later updates

Every push that changes `android/` builds a new release with a higher version
code. Upload its `.aab` to the track you want (testing or production).
