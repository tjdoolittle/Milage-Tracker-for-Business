package com.tjdoolittle.mileagelog.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tjdoolittle.mileagelog.data.AppData
import com.tjdoolittle.mileagelog.data.Exports
import com.tjdoolittle.mileagelog.data.Geo
import com.tjdoolittle.mileagelog.data.RatePeriod
import com.tjdoolittle.mileagelog.data.ReportPdf
import com.tjdoolittle.mileagelog.data.Vehicle
import com.tjdoolittle.mileagelog.data.YearOdo
import com.tjdoolittle.mileagelog.data.Status
import com.tjdoolittle.mileagelog.data.Store
import com.tjdoolittle.mileagelog.tracking.DriveDetector
import kotlinx.coroutines.launch
import java.util.Locale

fun granted(ctx: Context, perm: String) = ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

fun hasLocation(ctx: Context) = granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
fun hasBackground(ctx: Context) = Build.VERSION.SDK_INT < 29 || granted(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
fun hasNotifications(ctx: Context) = Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS)
fun batteryUnrestricted(ctx: Context) = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

@Composable
fun SettingsScreen(data: AppData, year: Int, resumeTick: Int, onClose: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val s = data.settings
    var tick by remember { mutableStateOf(0) }
    var disclose by remember { mutableStateOf<(() -> Unit)?>(null) }
    // Re-read permission state whenever we come back from a system screen.
    @Suppress("UNUSED_VARIABLE") val refresh = resumeTick + tick

    val askBasics = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        tick++
        if (Store.data.settings.autoDetect) DriveDetector.enable(ctx)
    }
    val askBackground = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        tick++
        if (!ok) openAppSettings(ctx)
    }
    val csvOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { write(ctx, it, Exports.csv(year)); toast(ctx, "CSV saved") }
    }
    val pdfOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let {
            runCatching { ctx.contentResolver.openOutputStream(it)?.use { o -> ReportPdf.write(o, year) } }
                .onSuccess { toast(ctx, "Tax report saved") }.onFailure { toast(ctx, "Couldn't save the report.") }
        }
    }
    val backupOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { write(ctx, it, Exports.backup()); toast(ctx, "Backup saved") }
    }
    val restoreIn = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
        val r = text?.let { Exports.restore(it) }
        toast(ctx, if (r == null) "That file isn't a Tax Mileage Tracker backup." else "Restored ${r.first} trips. ${r.second} were already here.")
    }

    fun basicsNeeded(): Array<String> = buildList {
        if (!hasLocation(ctx)) { add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION) }
        if (!DriveDetector.hasPermission(ctx) && Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (!hasNotifications(ctx) && Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", style = Type.title, color = p.ink, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = p.ink) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // ---- Automatic drive detection ----
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Automatic drive detection", style = Type.bodyBold, color = p.ink)
                        Text("Notices when you start driving, measures the trip with GPS, and adds it to your review list. Nothing to tap while you drive.",
                            style = Type.small, color = p.muted)
                    }
                    Switch(checked = s.autoDetect, onCheckedChange = { on ->
                        if (on) {
                            // Google Play requires this explanation before asking for location.
                            disclose = {
                                Store.setSettings(Store.data.settings.copy(autoDetect = true))
                                val need = basicsNeeded()
                                if (need.isNotEmpty()) askBasics.launch(need) else DriveDetector.enable(ctx)
                            }
                        } else {
                            Store.setSettings(Store.data.settings.copy(autoDetect = false))
                            DriveDetector.disable(ctx)
                        }
                    })
                }
                if (s.autoDetect) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
                    SectionLabel("Setup checklist")
                    Check("Location", "Allow while using the app", Store.demo || hasLocation(ctx)) { disclose = { askBasics.launch(basicsNeeded()) } }
                    if (Build.VERSION.SDK_INT >= 29) {
                        Check("Physical activity", "Lets the phone tell when you're in a car", Store.demo || DriveDetector.hasPermission(ctx)) { askBasics.launch(basicsNeeded()) }
                        Check("Location all the time", "Choose \"Allow all the time\" so tracking can start in your pocket", Store.demo || hasBackground(ctx)) {
                            disclose = {
                                if (!hasLocation(ctx)) askBasics.launch(basicsNeeded()) else askBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            }
                        }
                    }
                    if (Build.VERSION.SDK_INT >= 33) Check("Notifications", "Shows the trip while it's tracking", Store.demo || hasNotifications(ctx)) { askBasics.launch(basicsNeeded()) }
                    Check("Battery: unrestricted", "Opens app info. Tap Battery (or App battery usage), then Unrestricted.", Store.demo || batteryUnrestricted(ctx)) {
                        openAppSettings(ctx)
                    }
                }
            }

            // ---- Home and work ----
            Card {
                Text("Home and work", style = Type.bodyBold, color = p.ink)
                Text("Saved addresses fill in From and To with one tap. GPS trips that start or end within about 200 m of them get the address too.",
                    style = Type.small, color = p.muted)
                AddressField("Home address", s.homeAddress, home = true)
                AddressField("Work address", s.workAddress, home = false)
                Text(Store.COMMUTE_NOTE, style = Type.bodyBold, color = p.amber)
                Text("Driving between your home and your regular place of work is commuting, which the IRS doesn't allow as a deduction. Trips from work to clients, job sites, or another office usually count.",
                    style = Type.small, color = p.muted)
            }

            // ---- Vehicles ----
            VehiclesCard(s, year)

            // ---- Rates ----
            RatesCard(s)

            // ---- Export & backup ----
            Card {
                val count = data.trips.count { it.status == Status.LOGGED && it.year == year }
                Text("Tax time", style = Type.bodyBold, color = p.ink)
                BigButton("$year tax report (PDF)", Modifier.fillMaxWidth(), enabled = count > 0) { pdfOut.launch("mileage-report-$year.pdf") }
                Text("Totals, deduction, parking and tolls, vehicle miles for Schedule C Part IV, a monthly table, and every trip. Ready to hand to your tax preparer.",
                    style = Type.small, color = p.muted)
                ReportNameField(s.reportName)
                BigButton("Export $year CSV ($count trips)", Modifier.fillMaxWidth(), kind = "outline", enabled = count > 0) { csvOut.launch("mileage-log-$year.csv") }
                Text("A spreadsheet with date, route, purpose, vehicle, miles, and parking and tolls for every business trip.", style = Type.small, color = p.muted)
                Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton("Save backup", Modifier.weight(1f), kind = "outline") { backupOut.launch("mileage-log-backup-${Store.today()}.json") }
                    BigButton("Restore", Modifier.weight(1f), kind = "outline") { restoreIn.launch(arrayOf("application/json", "*/*")) }
                }
                Text("Trips live only on this phone. Backups also restore into the Tax Mileage Tracker web app, and its backups restore here.",
                    style = Type.small, color = p.muted)
            }

            // ---- Places ----
            if (s.places.isNotEmpty()) Card {
                Text("Saved places", style = Type.bodyBold, color = p.ink)
                Text("Trips that start or end within about 200 m of these get their name.", style = Type.small, color = p.muted)
                s.places.forEach { pl ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(pl.name, style = Type.body, color = p.ink, modifier = Modifier.weight(1f))
                        TextButton(onClick = { Store.setSettings(Store.data.settings.copy(places = Store.data.settings.places - pl)) }) {
                            Text("Remove", color = p.danger)
                        }
                    }
                }
            }
            Card {
                listOf("Support" to "https://tjdoolittle.github.io/mileage-log/support.html",
                       "Privacy policy" to "https://tjdoolittle.github.io/mileage-log/privacy.html").forEach { (label, url) ->
                    Text(label, style = Type.bodyBold, color = p.signText,
                        modifier = Modifier.fillMaxWidth().clickable {
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        }.padding(vertical = 6.dp))
                }
            }
            Card {
                Text("Your log", style = Type.bodyBold, color = p.ink)
                Text(Store.RESPONSIBILITY + " This app isn't tax advice. " + Store.GOV_DISCLAIMER, style = Type.small, color = p.muted)
            }
            AppearanceCard(s.theme)
            HelpCard()
            Text("Tax Mileage Tracker for Android · version ${versionName(ctx)}", style = Type.small, color = p.muted, modifier = Modifier.padding(bottom = 16.dp))
        }
    }
    disclose?.let { action ->
        LocationDisclosure(onContinue = { disclose = null; action() }, onDismiss = { disclose = null })
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.surface).border(1.dp, p.line, RoundedCornerShape(12.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
private fun Check(title: String, detail: String, ok: Boolean, onFix: () -> Unit) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (ok) "✓" else "!", style = Type.title,
            color = if (ok) p.signText else p.amber, modifier = Modifier.padding(end = 10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.bodyBold, color = p.ink)
            Text(detail, style = Type.small, color = p.muted)
        }
        if (!ok) TextButton(onClick = onFix) { Text("ALLOW", style = Type.label, color = p.signText) }
    }
}

/** Google Play "prominent disclosure": shown in the app before any location permission prompt. */
@Composable
fun LocationDisclosure(onContinue: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Location for automatic trips", style = Type.title, color = p.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tax Mileage Tracker collects location data to detect your drives and measure their miles for your mileage log, even when the app is closed or not in use.",
                    style = Type.body, color = p.ink)
                Text("Physical activity data is used only to tell when you're in a car. Your location and trips stay on your phone and aren't shared.",
                    style = Type.small, color = p.muted)
                Text("Next, Android asks for location access. To detect drives automatically, choose \"Allow all the time\".",
                    style = Type.small, color = p.muted)
            }
        },
        confirmButton = { TextButton(onClick = onContinue) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

@Composable
private fun AddressField(label: String, saved: String, home: Boolean) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable(saved) { mutableStateOf(saved) }
    var busy by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(text, { text = it }, Modifier.weight(1f), label = { Text(label) }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        TextButton(enabled = !busy && text.trim() != saved, onClick = {
            val value = text.trim()
            busy = true
            scope.launch {
                val spot = if (value.isBlank()) null else Geo.locate(ctx, value)
                Store.setHomeWork(home, value, spot?.first, spot?.second)
                busy = false
                toast(ctx, when {
                    value.isBlank() -> "${if (home) "Home" else "Work"} address removed"
                    spot != null -> "Saved. Trips that start or end here get this address."
                    else -> "Saved. Couldn't find it on the map, so only the one-tap fill will use it."
                })
            }
        }) { Text(if (busy) "…" else "SAVE", style = Type.label, color = p.signText) }
    }
}

@Composable
private fun RatesCard(s: com.tjdoolittle.mileagelog.data.Settings) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val sch = Store.schedule(s)
    var editing by remember { mutableStateOf<RatePeriod?>(null) }
    var adding by remember { mutableStateOf(false) }
    fun close() { adding = false; editing = null }

    Card {
        Text("Mileage rates", style = Type.bodyBold, color = p.ink)
        Text("Each rate applies from its start date until the next rate starts. Every trip uses the rate in effect on the day it was driven. Tap a rate to change it.",
            style = Type.small, color = p.muted)
        sch.indices.reversed().forEach { i ->
            val r = sch[i]
            val next = sch.getOrNull(i + 1)
            val range = if (next == null) "${longDate(r.from)} onward" else "${longDate(r.from)} to ${longDate(dayBefore(next.from))}"
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { editing = r }.padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(range, style = Type.body, color = p.ink, modifier = Modifier.weight(1f))
                Text("${fmtCents(r.rate)}¢", style = Type.signNum.copy(fontSize = 19.sp), color = p.ink)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            BigButton("Add rate change", Modifier.weight(1f), kind = "outline") { adding = true }
            if (s.ratePeriods != null || s.rates.isNotEmpty()) {
                TextButton(onClick = {
                    Store.setSettings(Store.data.settings.copy(ratePeriods = null, rates = emptyMap()))
                    toast(ctx, "Using the IRS rates")
                }) { Text("Use IRS rates", style = Type.bodyBold, color = p.signText) }
            }
        }
        val thisYear = java.time.LocalDate.now().year
        val irs = Store.irsSummary(thisYear)
        if (irs != null) {
            Text("IRS business rate for $thisYear: $irs.", style = Type.small, color = p.muted)
        } else if (!Store.hasRateFor(thisYear, s)) {
            Text("The IRS rate for $thisYear isn't in this version of the app yet (the IRS usually announces it in December). " +
                "Trips keep using ${fmtCents(Store.rateOn(Store.today(), s))}¢ until you tap Add rate change and enter the new rate starting January 1, " +
                "or until an app update adds it.",
                style = Type.small, color = p.ink,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(p.amberBg).padding(10.dp))
        }
        Text("Source: IRS standard mileage rates (irs.gov)", style = Type.bodyBold, color = p.signText,
            modifier = Modifier.fillMaxWidth().clickable {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Store.IRS_SOURCE_URL))) }
            }.padding(vertical = 6.dp))
        Text(Store.GOV_DISCLAIMER, style = Type.small, color = p.muted)
    }

    if (adding || editing != null) {
        val init = editing
        RateDialog(
            initial = init,
            canDelete = sch.size > 1,
            onSave = { r ->
                Store.setSchedule(sch.filter { it != init && it.from != r.from } + r)
                toast(ctx, "Rate saved. Trips from ${longDate(r.from)} on use ${fmtCents(r.rate)}¢.")
                close()
            },
            onDelete = { Store.setSchedule(sch.filter { it != init }); close() },
            onDismiss = { close() },
        )
    }
}

@Composable
private fun RateDialog(initial: RatePeriod?, canDelete: Boolean, onSave: (RatePeriod) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var from by rememberSaveable { mutableStateOf(initial?.from ?: Store.today()) }
    var cents by rememberSaveable { mutableStateOf(initial?.let { fmtCents(it.rate) } ?: "") }
    var err by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add rate change" else "Edit rate", style = Type.title, color = p.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel("Starts on")
                Text(longDate(from), style = Type.body, color = p.ink,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.5.dp, p.line, RoundedCornerShape(10.dp))
                        .clickable {
                            DatePickerDialog(ctx, { _, y, m, d -> from = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d) },
                                from.take(4).toInt(), from.substring(5, 7).toInt() - 1, from.substring(8, 10).toInt()).show()
                        }
                        .padding(horizontal = 12.dp, vertical = 14.dp))
                OutlinedTextField(cents, { cents = it; err = null }, label = { Text("Cents per mile") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                err?.let { Text(it, style = Type.small, color = p.danger) }
                if (initial != null && canDelete) {
                    TextButton(onClick = onDelete) { Text("Remove this rate", style = Type.bodyBold, color = p.danger) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val c = cents.replace("¢", "").trim().toDoubleOrNull()
                if (c == null || c <= 0 || c > 500) err = "Enter the rate in cents per mile, like 76."
                else onSave(RatePeriod(from, Math.round(c * 10) / 1000.0))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AppearanceCard(theme: String) {
    val p = LocalPalette.current
    Card {
        Text("Appearance", style = Type.bodyBold, color = p.ink)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, p.line, RoundedCornerShape(10.dp))) {
            listOf("system" to "Match phone", "light" to "Light", "dark" to "Dark").forEach { (key, label) ->
                val sel = theme == key
                Text(label, style = Type.bodyBold.copy(fontSize = 15.sp), color = if (sel) p.signInk else p.ink, textAlign = TextAlign.Center, maxLines = 1,
                    modifier = Modifier.weight(1f).background(if (sel) p.sign else Color.Transparent)
                        .clickable { Store.setSettings(Store.data.settings.copy(theme = key)) }.padding(vertical = 12.dp))
            }
        }
    }
}

@Composable
private fun HelpCard() {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var open by rememberSaveable { mutableStateOf(-1) }
    Card {
        Text("Help and feedback", style = Type.bodyBold, color = p.ink)
        Store.FAQ.forEachIndexed { i, (q, a) ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { open = if (open == i) -1 else i }.padding(vertical = 8.dp, horizontal = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(q, style = Type.body, color = p.ink, modifier = Modifier.weight(1f))
                    Text(if (open == i) "−" else "+", style = Type.title, color = p.signText, modifier = Modifier.padding(start = 8.dp))
                }
                if (open == i) Text(a, style = Type.small, color = p.muted, modifier = Modifier.padding(top = 6.dp))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        }
        BigButton("Show the walkthrough again", Modifier.fillMaxWidth(), kind = "outline") {
            Store.setSettings(Store.data.settings.copy(introDismissed = false))
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton("Send feedback", Modifier.fillMaxWidth(), kind = "outline") {
                val body = "\n\n---\nTax Mileage Tracker ${versionName(ctx)}, Android ${Build.VERSION.RELEASE}, ${Build.MANUFACTURER} ${Build.MODEL}"
                val uri = Uri.parse("mailto:${Store.SUPPORT_EMAIL}?subject=" + Uri.encode("Tax Mileage Tracker feedback") + "&body=" + Uri.encode(body))
                runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, uri)) }.onFailure { toast(ctx, "No email app found. Write to ${Store.SUPPORT_EMAIL}") }
            }
            BigButton("Rate the app", Modifier.fillMaxWidth(), kind = "outline") {
                val pkg = ctx.packageName
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))) } }
            }
        }
    }
}

@Composable
private fun ReportNameField(saved: String) {
    var text by rememberSaveable(saved) { mutableStateOf(saved) }
    OutlinedTextField(text, { text = it; Store.setSettings(Store.data.settings.copy(reportName = it)) }, Modifier.fillMaxWidth(),
        label = { Text("Name or business on the report (optional)") }, singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
}

@Composable
private fun VehiclesCard(s: com.tjdoolittle.mileagelog.data.Settings, year: Int) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf<Vehicle?>(null) }
    Card {
        Text("Vehicles", style = Type.bodyBold, color = p.ink)
        Text("Odometer readings at the start and end of the year give the total miles Schedule C asks for. With more than one vehicle, pick one on each trip.",
            style = Type.small, color = p.muted)
        s.vehicles.forEach { v ->
            val o = v.odometer[year]
            val odo = if (o?.start != null && o.end != null) "$year odometer ${fmtOdo(o.start)} to ${fmtOdo(o.end)}"
            else if (o?.start != null) "$year odometer starts at ${fmtOdo(o.start)}" else "No $year odometer readings yet"
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { editing = v }.padding(vertical = 8.dp, horizontal = 4.dp)) {
                Text(v.name, style = Type.body, color = p.ink)
                Text(odo, style = Type.small, color = p.muted)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        }
        BigButton(if (s.vehicles.isEmpty()) "Add your vehicle" else "Add another vehicle", Modifier.fillMaxWidth(), kind = "outline") {
            editing = Vehicle(Store.newVehicleId(), "")
        }
    }
    editing?.let { v ->
        VehicleDialog(v, year, isNew = s.vehicles.none { it.id == v.id }, canDelete = s.vehicles.any { it.id == v.id },
            onSave = { Store.saveVehicle(it); editing = null; toast(ctx, "Vehicle saved") },
            onDelete = { Store.deleteVehicle(v.id); editing = null },
            onDismiss = { editing = null })
    }
}

private fun fmtOdo(v: Double): String = String.format(Locale.US, "%,.0f", v)

@Composable
private fun VehicleDialog(v: Vehicle, year: Int, isNew: Boolean, canDelete: Boolean,
                          onSave: (Vehicle) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val o = v.odometer[year]
    var name by rememberSaveable { mutableStateOf(v.name) }
    var inService by rememberSaveable { mutableStateOf(v.inService) }
    var start by rememberSaveable { mutableStateOf(o?.start?.let { String.format(Locale.US, "%.0f", it) } ?: "") }
    var end by rememberSaveable { mutableStateOf(o?.end?.let { String.format(Locale.US, "%.0f", it) } ?: "") }
    var err by rememberSaveable { mutableStateOf<String?>(null) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    fun num(t: String) = t.replace(",", "").trim().toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add vehicle" else "Edit vehicle", style = Type.title, color = p.ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it; err = null }, label = { Text("Name, like 2022 Ford F-150") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                SectionLabel("First used for business")
                Text(if (inService.isBlank()) "Choose a date" else longDate(inService), style = Type.body, color = if (inService.isBlank()) p.muted else p.ink,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.5.dp, p.line, RoundedCornerShape(10.dp))
                        .clickable {
                            val init = inService.ifBlank { Store.today() }
                            DatePickerDialog(ctx, { _, y, m, d -> inService = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d) },
                                init.take(4).toInt(), init.substring(5, 7).toInt() - 1, init.substring(8, 10).toInt()).show()
                        }
                        .padding(horizontal = 12.dp, vertical = 14.dp))
                SectionLabel("$year odometer")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(start, { start = it; err = null }, Modifier.weight(1f), label = { Text("Jan 1") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(end, { end = it; err = null }, Modifier.weight(1f), label = { Text("Dec 31") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                Text("Use the first reading of the year (or when you started using it) and the last. Leave the end blank until the year is over.",
                    style = Type.small, color = p.muted)
                err?.let { Text(it, style = Type.small, color = p.danger) }
                if (canDelete) TextButton(onClick = { confirm = true }) { Text("Remove this vehicle", style = Type.bodyBold, color = p.danger) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = num(start); val b = num(end)
                when {
                    name.isBlank() -> err = "Give the vehicle a name."
                    start.isNotBlank() && a == null || end.isNotBlank() && b == null -> err = "Enter odometer readings as numbers."
                    a != null && b != null && b < a -> err = "The Dec 31 reading should be higher than Jan 1."
                    else -> {
                        val odo = v.odometer.toMutableMap()
                        if (a == null && b == null) odo.remove(year) else odo[year] = YearOdo(a, b)
                        onSave(v.copy(name = name.trim(), inService = inService, odometer = odo))
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Remove ${v.name}?") },
            text = { Text("Its trips stay in your log and move to your first vehicle.") },
            confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text("Remove", color = p.danger) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep it") } },
        )
    }
}

private fun write(ctx: Context, uri: Uri, text: String) {
    runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } }
        .onFailure { toast(ctx, "Couldn't save the file.") }
}

fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

fun openAppSettings(ctx: Context) {
    runCatching { ctx.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))) }
}

private fun versionName(ctx: Context): String =
    runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" }.getOrDefault("")
