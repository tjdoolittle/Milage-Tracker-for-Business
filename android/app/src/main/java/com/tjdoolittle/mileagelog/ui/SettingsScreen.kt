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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tjdoolittle.mileagelog.data.AppData
import com.tjdoolittle.mileagelog.data.Exports
import com.tjdoolittle.mileagelog.data.RatePeriod
import com.tjdoolittle.mileagelog.data.Status
import com.tjdoolittle.mileagelog.data.Store
import com.tjdoolittle.mileagelog.tracking.DriveDetector
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
    val backupOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { write(ctx, it, Exports.backup()); toast(ctx, "Backup saved") }
    }
    val restoreIn = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
        val r = text?.let { Exports.restore(it) }
        toast(ctx, if (r == null) "That file isn't a Mileage Log backup." else "Restored ${r.first} trips. ${r.second} were already here.")
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
                        Store.setSettings(Store.data.settings.copy(autoDetect = on))
                        if (on) {
                            val need = basicsNeeded()
                            if (need.isNotEmpty()) askBasics.launch(need) else DriveDetector.enable(ctx)
                        } else DriveDetector.disable(ctx)
                    })
                }
                if (s.autoDetect) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
                    SectionLabel("Setup checklist")
                    Check("Location", "Allow while using the app", hasLocation(ctx)) { askBasics.launch(basicsNeeded()) }
                    if (Build.VERSION.SDK_INT >= 29) {
                        Check("Physical activity", "Lets the phone tell when you're in a car", DriveDetector.hasPermission(ctx)) { askBasics.launch(basicsNeeded()) }
                        Check("Location all the time", "Choose \"Allow all the time\" so tracking can start in your pocket", hasBackground(ctx)) {
                            if (!hasLocation(ctx)) askBasics.launch(basicsNeeded()) else askBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    }
                    if (Build.VERSION.SDK_INT >= 33) Check("Notifications", "Shows the trip while it's tracking", hasNotifications(ctx)) { askBasics.launch(basicsNeeded()) }
                    Check("Battery: unrestricted", "Stops Android from blocking GPS when a drive starts", batteryUnrestricted(ctx)) {
                        runCatching {
                            ctx.startActivity(Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName)))
                        }.onFailure { openAppSettings(ctx) }
                    }
                }
            }

            // ---- Rates ----
            RatesCard(s)

            // ---- Export & backup ----
            Card {
                val count = data.trips.count { it.status == Status.LOGGED && it.year == year }
                Text("Export and backup", style = Type.bodyBold, color = p.ink)
                BigButton("Export $year CSV ($count trips)", Modifier.fillMaxWidth(), enabled = count > 0) { csvOut.launch("mileage-log-$year.csv") }
                Text("Includes date, route, purpose, and miles for every business trip: the records the IRS asks you to keep.", style = Type.small, color = p.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton("Save backup", Modifier.weight(1f), kind = "outline") { backupOut.launch("mileage-log-backup-${Store.today()}.json") }
                    BigButton("Restore", Modifier.weight(1f), kind = "outline") { restoreIn.launch(arrayOf("application/json", "*/*")) }
                }
                Text("Trips live only on this phone. Backups also restore into the Mileage Log web app, and its backups restore here.",
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
            Text("Mileage Log for Android · version ${versionName(ctx)}", style = Type.small, color = p.muted, modifier = Modifier.padding(bottom = 16.dp))
        }
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
        Text("IRS business rate for 2026: 72.5¢ through June 30, then 76¢ from July 1 through December 31.", style = Type.small, color = p.muted)
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
