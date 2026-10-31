# App Store listing: Mileage Log

Everything App Store Connect asks for, ready to paste. Character limits are
Apple's; every field here fits.

Screenshots are in [`screenshots/`](screenshots/) (6.9-inch iPhone, 1320 x 2868).
Upload them in order under **iPhone 6.9" Display**. Apple scales them down for
smaller iPhones, so no other sizes are needed. The app is iPhone-only, so no
iPad screenshots either.

---

## App Information (App Store Connect → App Information)

| Field | Value |
| --- | --- |
| Name (30) | `Mileage Log` |
| Subtitle (30) | `Business miles, tracked by GPS` |
| Category | Primary: **Business**. Secondary: **Finance** |
| Content rights | Does not contain, show, or access third-party content |
| Age rating | Answer **None** / **No** to every question. Result: 4+ |

## Version information (the 1.0 page)

### Promotional text (170)

```
Turn on automatic drive detection and every drive lands in your log. Tap Business or Personal, and the IRS deduction adds itself up, including 2026's July rate change.
```

### Description (4,000)

```
Mileage Log keeps the mileage records the IRS asks for, without the paperwork. It measures your drives with GPS, even with the phone in your pocket, and adds up your business miles and deduction for the year.

LOGS DRIVES ON ITS OWN
Turn on automatic drive detection and Mileage Log notices when you start driving, measures the trip, and ends it when you park. Each drive lands in a review list with its miles and start and end addresses. Tap Business or Personal. That's it.

OR TRACK IT YOURSELF
Tap Start trip when you leave and Finish trip when you arrive. Or log a past drive by miles, round trip, or odometer readings.

RECORDS THE IRS EXPECTS
Every business trip keeps the date, where you started and ended, the miles, and the business purpose. Add a client or job and notes if you like.

RATES THAT CHANGE MIDYEAR
Each trip uses the IRS standard mileage rate in effect on the day you drove it. The 2026 change is built in: 72.5 cents per mile through June 30, then 76 cents from July 1. Add your own rate changes whenever the IRS announces one.

YOUR YEAR AT A GLANCE
A highway-sign odometer shows the year's business miles, your deduction, this month's miles, and a month-by-month chart. Tap the year to look back at earlier tax years.

NAMES YOUR REGULAR STOPS
Rename an address once, like "Shop" or a client's house, and Mileage Log uses that name on future trips that start or end there.

READY FOR TAX TIME
Export any year as a spreadsheet (CSV) with the date, route, purpose, miles, rate, and deduction for every trip, plus totals. Save a backup to Files or iCloud Drive and restore it on a new phone.

PRIVATE BY DESIGN
No account, no ads, no tracking. Your trips and location never leave your phone unless you export them.

Mileage Log helps you keep records. It isn't tax advice; check with a tax professional about your own situation.
```

### Keywords (100)

```
tracker,IRS,deduction,trip,tax,expense,odometer,reimbursement,1099,self-employed,writeoff,gig,driver
```

Words already in the name or subtitle (mileage, log, business, miles, GPS) aren't repeated:
Apple searches those fields too. Don't add brand names like Uber, DoorDash, or MileIQ;
Apple rejects trademarked keywords.

### URLs

| Field | Value |
| --- | --- |
| Support URL | `https://tjdoolittle.github.io/mileage-log/support.html` |
| Marketing URL (optional) | `https://tjdoolittle.github.io/mileage-log/` |
| Privacy Policy URL (App Information page) | `https://tjdoolittle.github.io/mileage-log/privacy.html` |

### Copyright

```
2026 Thomas Doolittle
```

### What's New (later versions only)

Leave blank for 1.0.

---

## App Privacy (App Store Connect → App Privacy)

Choose **"No, we do not collect data from this app."** That's accurate: trips,
location, and settings stay on the phone, there's no server, and nothing is
sent to you or anyone else.

## App Review Information

**Sign-in required:** No.

**Contact:** your name, phone, and `doolittle.thomas+apps@gmail.com`.

**Notes** (this matters: Apple looks closely at apps that use location *Always*):

```
Mileage Log is a business mileage log. It uses location to measure the distance of the user's drives for IRS mileage records.

Why "Always" location: with Automatic drive detection turned on (Settings, off by default), the app uses Motion & Fitness, significant location changes, and a geofence around the last parking spot to notice when a drive starts, then measures the drive with GPS while the phone is in a pocket or bag. This only works with Always access. The blue location indicator is shown while a drive is being measured. Without Always, users can still track trips by tapping Start trip and Finish trip, or enter trips by hand.

Privacy: there is no account and no server. Location and trip data never leave the device unless the user exports a CSV or backup file themselves.

To try it without driving: tap "+ Log trip" to enter a trip by hand, or tap "Start trip", allow location While Using, then "Finish trip".
```

---

## Before you submit

- [ ] Screenshots uploaded (5, in order)
- [ ] Privacy Policy URL set on the App Information page
- [ ] App Privacy answered: Data Not Collected
- [ ] Build selected under the version (the TestFlight build)
- [ ] Review notes pasted
- [ ] Pricing: **Free** (Pricing and Availability page)
