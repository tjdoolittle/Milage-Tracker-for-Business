package com.tjdoolittle.mileagelog.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tjdoolittle.mileagelog.data.Status
import com.tjdoolittle.mileagelog.data.Store
import com.tjdoolittle.mileagelog.data.Trip
import java.util.Locale
import kotlin.math.roundToLong

private fun parseNum(s: String): Double? = s.replace(",", "").trim().toDoubleOrNull()

/**
 * Full-screen editor for a new trip, a trip to review, or a logged trip.
 * Saving always marks the trip as a logged business trip.
 */
@Composable
fun TripEditor(trip: Trip, isNew: Boolean, onClose: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val data = Store.data
    val reviewing = trip.status == Status.REVIEW

    var date by remember { mutableStateOf(trip.date) }
    var odoMode by remember { mutableStateOf(trip.odoStart != null && trip.source == null) }
    var roundTrip by remember { mutableStateOf(trip.roundTrip) }
    var miles by remember {
        mutableStateOf(when {
            trip.miles <= 0 -> ""
            trip.roundTrip -> fmt(trip.miles / 2)
            else -> fmt(trip.miles)
        })
    }
    var odo1 by remember { mutableStateOf(trip.odoStart?.let(::fmt) ?: "") }
    var odo2 by remember { mutableStateOf(trip.odoEnd?.let(::fmt) ?: "") }
    var from by remember { mutableStateOf(trip.from) }
    var to by remember { mutableStateOf(trip.to) }
    var purpose by remember { mutableStateOf(trip.purpose) }
    var client by remember { mutableStateOf(trip.client) }
    var notes by remember { mutableStateOf(trip.notes) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val computed: Double? = if (odoMode) {
        val a = parseNum(odo1); val b = parseNum(odo2)
        if (a != null && b != null && b > a) ((b - a) * 10).roundToLong() / 10.0 else null
    } else parseNum(miles)?.takeIf { it > 0 }?.let { if (roundTrip) it * 2 else it }
    val rate = Store.rateOn(date)

    val recentPurposes = data.trips.filter { it.purpose.isNotBlank() }.sortedByDescending { it.createdAt }.map { it.purpose }.distinct().take(8)
    val recentClients = data.trips.filter { it.client.isNotBlank() }.sortedByDescending { it.createdAt }.map { it.client }.distinct().take(8)
    val placeNames = (data.settings.places.map { it.name } + data.trips.sortedByDescending { it.createdAt }.flatMap { listOf(it.from, it.to) })
        .filter { it.isNotBlank() && !com.tjdoolittle.mileagelog.data.Geo.looksLikeCoords(it) }.distinct().take(8)

    fun save(status: String) {
        val m = computed
        if (status == Status.LOGGED) {
            if (m == null) { error = if (odoMode) "Enter both odometer readings, with the end reading higher." else "Enter how many miles you drove."; return }
            if (purpose.isBlank()) { error = "Add a business purpose. The IRS requires one for every trip."; return }
        }
        val t = trip.copy(
            date = date, miles = m ?: trip.miles, purpose = purpose.trim(), client = client.trim(),
            from = from.trim(), to = to.trim(), notes = notes.trim(),
            roundTrip = !odoMode && roundTrip,
            odoStart = if (odoMode) parseNum(odo1) else null, odoEnd = if (odoMode) parseNum(odo2) else null,
            status = status,
        )
        Store.saveTrip(t)
        if (status == Status.LOGGED) {
            Store.learnPlace(t.from, trip.startLat, trip.startLng)
            Store.learnPlace(t.to, trip.endLat, trip.endLng)
        }
        onClose()
    }

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(when { isNew -> "Log a trip"; reviewing -> "Review drive"; else -> "Edit trip" }, style = Type.title, color = p.ink, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = p.ink) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (trip.source != null && trip.miles > 0) {
                Text("Measured by GPS: ${fmtMi(trip.miles)} mi", style = Type.small, color = p.signText)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    SectionLabel("Date")
                    Text(date, style = Type.body, color = p.ink, modifier = Modifier.fillMaxWidth().padding(top = 5.dp)
                        .clip(RoundedCornerShape(10.dp)).background(p.surface).border(1.5.dp, p.line, RoundedCornerShape(10.dp))
                        .clickable {
                            val y = date.take(4).toInt(); val mo = date.substring(5, 7).toInt() - 1; val d = date.substring(8, 10).toInt()
                            DatePickerDialog(ctx, { _, yy, mm, dd -> date = "%04d-%02d-%02d".format(yy, mm + 1, dd) }, y, mo, d).show()
                        }.padding(horizontal = 12.dp, vertical = 14.dp))
                }
                Column(Modifier.weight(1f)) {
                    SectionLabel("Distance from")
                    Row(Modifier.padding(top = 5.dp).clip(RoundedCornerShape(10.dp)).border(1.5.dp, p.line, RoundedCornerShape(10.dp))) {
                        listOf(false to "Miles", true to "Odometer").forEach { (mode, label) ->
                            val sel = odoMode == mode
                            Text(label, style = Type.bodyBold.copy(fontSize = 14.sp),
                                color = if (sel) p.bg else p.ink,
                                modifier = Modifier.weight(1f).background(if (sel) p.ink else p.surface).clickable { odoMode = mode }
                                    .padding(vertical = 14.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
            if (odoMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("Start reading", odo1, { odo1 = it }, Modifier.weight(1f), number = true)
                    Field("End reading", odo2, { odo2 = it }, Modifier.weight(1f), number = true)
                }
            } else {
                Field(if (trip.source != null) "Miles driven" else "Miles driven (one way)", miles, { miles = it }, number = true)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { roundTrip = !roundTrip }) {
                    Checkbox(checked = roundTrip, onCheckedChange = { roundTrip = it })
                    Text("Round trip (doubles the miles)", style = Type.bodyBold, color = p.ink)
                }
            }
            if (computed != null) {
                Text("${fmtMi(computed)} mi × ${fmtCents(rate)}¢ = ${fmtUsd(computed * rate)} deduction",
                    style = Type.small, color = p.muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("From", from, { from = it }, Modifier.weight(1f))
                Field("To", to, { to = it }, Modifier.weight(1f))
            }
            Chips(placeNames) { if (from.isBlank()) from = it else to = it }
            if (from.isNotBlank() && to.isNotBlank()) {
                Text("Check driving distance in Google Maps ↗", style = Type.bodyBold, color = p.signText,
                    modifier = Modifier.clickable {
                        val url = "https://www.google.com/maps/dir/?api=1&travelmode=driving&origin=" + Uri.encode(from) + "&destination=" + Uri.encode(to)
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    })
            }
            Field("Business purpose", purpose, { purpose = it })
            Chips(recentPurposes) { purpose = it }
            Field("Client or job (optional)", client, { client = it })
            Chips(recentClients) { client = it }
            Field("Notes (optional)", notes, { notes = it }, singleLine = false)
            error?.let { Text(it, style = Type.bodyBold, color = p.danger) }
            if (!isNew) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (trip.status != Status.PERSONAL) BigButton("Personal", Modifier.weight(1f), kind = "outline") { save(Status.PERSONAL) }
                    BigButton("Delete", Modifier.weight(1f), kind = "danger") { confirmDelete = true }
                }
            }
        }
        Box(Modifier.fillMaxWidth().background(p.bg).navigationBarsPadding().padding(16.dp)) {
            BigButton(if (reviewing) "Save as business trip" else if (isNew) "Log trip" else "Save changes", Modifier.fillMaxWidth()) { save(Status.LOGGED) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this trip?") },
            text = { Text("The ${fmtMi(trip.miles)} mi trip from ${trip.date} will be removed. This can't be undone.") },
            confirmButton = { TextButton(onClick = { Store.deleteTrip(trip.id); confirmDelete = false; onClose() }) { Text("Delete", color = p.danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep it") } },
        )
    }
}

private fun fmt(v: Double): String = if (v == Math.floor(v)) String.format(Locale.US, "%.0f", v) else String.format(Locale.US, "%.1f", v)

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth(),
                  number: Boolean = false, singleLine: Boolean = true) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier.heightIn(min = 56.dp),
        label = { Text(label) }, singleLine = singleLine, minLines = if (singleLine) 1 else 2,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal)
        else KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        shape = RoundedCornerShape(10.dp),
    )
}

@Composable
private fun Chips(options: List<String>, onPick: (String) -> Unit) {
    if (options.isEmpty()) return
    val p = LocalPalette.current
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 0.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o ->
            Text(o, style = Type.small, color = p.ink, maxLines = 1,
                modifier = Modifier.clip(CircleShape).background(p.surface).border(1.dp, p.line, CircleShape)
                    .clickable { onPick(o) }.padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
}
