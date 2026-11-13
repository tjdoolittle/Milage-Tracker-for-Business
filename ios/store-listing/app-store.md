# App Store listing: Tax Mileage Tracker

Everything App Store Connect asks for, ready to paste. Character limits are
Apple's; every field here fits.

Screenshots are in [`screenshots/`](screenshots/), numbered in upload order
(drive detected, review, home, rates, dark mode):

| App Store Connect slot | Folder | Size | Needed? |
| --- | --- | --- | --- |
| **iPhone 6.3" Display** | `screenshots/6.3-inch/` | 1206 x 2622 | **Required** |
| iPhone 6.9" Display | `screenshots/6.9-inch/` | 1320 x 2868 | Optional |

The app is iPhone-only, so no iPad screenshots. "File dimensions are invalid" means
an image went into the wrong slot: each slot only takes its own sizes.

**Creative assets** (optional landscape header art for the product page and
search results): [`creative-assets/`](creative-assets/), at 5244 x 2950 and
3840 x 1646. These are separate from screenshots.

To change captions, edit `compose_store.py` (screenshots) or `compose_header.py`
(header art) and rerun them on the raw captures from the `ci-ios` branch.

---

## App Information (App Store Connect → App Information)

| Field | Value |
| --- | --- |
| Name (30) | `Tax Mileage Tracker` |
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
Tax Mileage Tracker keeps the mileage records the IRS asks for, without the paperwork. It measures your drives with GPS, even with the phone in your pocket, and adds up your business miles and deduction for the year.

LOGS DRIVES ON ITS OWN
Turn on automatic drive detection and Tax Mileage Tracker notices when you start driving, measures the trip, and ends it when you park. Each drive lands in a review list with its miles and start and end addresses. Tap Business or Personal. That's it.

OR TRACK IT YOURSELF
Tap Start trip when you leave and Finish trip when you arrive. Or tap + Log trip to add a drive by miles, round trip, or odometer readings.

RECORDS THE IRS EXPECTS
Every business trip keeps the date, where you started and ended, the miles, and the business purpose. Pick a purpose like Client Visits, Job Site Visits, Sales Calls, or Deliveries, or type your own. Add a client or job and notes if you like.

HOME AND WORK, ONE TAP
Save your home and work addresses and fill in a trip's route with one tap. Regular everyday commuting isn't deductible, and Tax Mileage Tracker flags trips between home and work so you can mark them personal.

RATES THAT CHANGE MIDYEAR
Each trip uses the IRS standard mileage rate in effect on the day you drove it. The 2026 change is built in: 72.5 cents per mile through June 30, then 76 cents from July 1. Add your own rate changes whenever the IRS announces one.
Source: IRS standard mileage rates, https://www.irs.gov/tax-professionals/standard-mileage-rates

NOT A GOVERNMENT APP
Tax Mileage Tracker is not affiliated with, endorsed by, or acting for the IRS or any government agency. Mileage rates are published by the IRS at the source link above. The app helps you keep records and isn't tax advice; you're responsible for the data you log. Check with a tax professional about your own situation.

YOUR YEAR AT A GLANCE
A highway-sign odometer shows the year's business miles, your deduction, this month's miles, and a month-by-month chart. Tap the year to look back at earlier tax years.

NAMES YOUR REGULAR STOPS
Rename an address once, like "Shop" or a client's house, and Tax Mileage Tracker uses that name on future trips that start or end there.

READY FOR TAX TIME
Make a PDF tax report for any year: your business miles, mileage deduction, parking and tolls, the vehicle numbers Schedule C asks for (total, business, commuting, and other miles), a month-by-month table, and every trip. Hand it to your tax preparer as is. Or export a spreadsheet (CSV) of the same trips.

PARKING, TOLLS, AND VEHICLES
Add parking fees and tolls to any trip; they're deductible on top of the mileage rate. Track more than one vehicle and enter each one's odometer readings for the year.

Save a backup to Files or iCloud Drive and restore it on a new phone.

PRIVATE BY DESIGN
No account, no ads, no tracking. Your trips and location never leave your phone unless you export them.
```

### Keywords (100)

```
log,record,IRS,deduction,trip,expense,odometer,reimbursement,1099,self-employed,writeoff,gig,driver
```

Words already in the name or subtitle (tax, mileage, tracker, business, miles, GPS) aren't repeated:
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
Tax Mileage Tracker is a business mileage log. It uses location to measure the distance of the user's drives for IRS mileage records.

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
