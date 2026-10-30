package com.tjdoolittle.mileagelog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tjdoolittle.mileagelog.data.ActiveTrip
import com.tjdoolittle.mileagelog.data.AppData
import com.tjdoolittle.mileagelog.data.Status
import com.tjdoolittle.mileagelog.data.Store
import com.tjdoolittle.mileagelog.data.Trip
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    data: AppData,
    live: ActiveTrip?,
    gpsAccuracy: Double?,
    year: Int,
    onYear: (Int) -> Unit,
    onOpenTrip: (Trip) -> Unit,
    onPersonal: (Trip) -> Unit,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
    onLog: () -> Unit,
    onSettings: () -> Unit,
) {
    val p = LocalPalette.current
    val now = Calendar.getInstance()
    val curYear = now.get(Calendar.YEAR)
    val s = data.settings
    var pickYear by rememberSaveable { mutableStateOf(false) }
    val logged = data.trips.filter { it.status == Status.LOGGED && it.year == year }
        .sortedWith(compareByDescending<Trip> { it.date }.thenByDescending { it.startTime ?: it.createdAt })
    val review = data.trips.filter { it.status == Status.REVIEW }.sortedByDescending { it.startTime ?: it.createdAt }
    val total = logged.sumOf { it.miles }
    val totalDeduction = logged.sumOf { Store.deduction(it, s) }
    val byMonth = List(12) { m -> logged.filter { it.month == m + 1 }.sumOf { it.miles } }
    val years = (setOf(curYear) + data.trips.map { it.year }.filter { it > 2000 }).sortedDescending().take(5)
    val pickable = (((curYear - 4)..(curYear + 1)).toSet() + data.trips.map { it.year }.filter { it > 2000 }).sortedDescending()
    val active = live ?: data.active

    Column(Modifier.fillMaxSize().background(p.bg)) {
        LazyColumn(
            Modifier.weight(1f).statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).background(p.sign), contentAlignment = Alignment.Center) {
                        Text("MI", style = Type.tiny.copy(fontSize = 8.sp), color = p.signInk)
                    }
                    Text("MILEAGE LOG", style = Type.label.copy(fontSize = 15.sp), color = p.ink, modifier = Modifier.padding(start = 8.dp).weight(1f))
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings and export", tint = p.ink) }
                }
            }
            if (years.size > 1) item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    years.forEach { y ->
                        val sel = y == year
                        Text(y.toString(), style = Type.bodyBold.copy(fontSize = 14.sp),
                            color = if (sel) p.bg else p.ink,
                            modifier = Modifier.clip(CircleShape).background(if (sel) p.ink else p.bg)
                                .border(1.dp, if (sel) p.ink else p.line, CircleShape)
                                .clickable { onYear(y) }.padding(horizontal = 12.dp, vertical = 5.dp))
                    }
                }
            }
            item {
                val isCur = year == curYear
                val mid = if (isCur) fmtMi(byMonth[now.get(Calendar.MONTH)]) else fmtMi(total / 12)
                val midLabel = if (isCur) "${MON[now.get(Calendar.MONTH)].uppercase()} MILES" else "AVG / MONTH"
                val refDate = if (isCur) Store.today() else "$year-12-31"
                SignCard(year, total, totalDeduction, Store.rateOn(refDate, s), mid, midLabel, logged.size, byMonth,
                    if (isCur) now.get(Calendar.MONTH) else null, onYearClick = { pickYear = true }, onRateClick = onSettings)
            }
            if (active != null) item { ActiveBanner(active, gpsAccuracy, onDiscard) }
            if (review.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("TO REVIEW", style = Type.label, color = p.amber, modifier = Modifier.weight(1f))
                        Text("${review.size} drive${if (review.size > 1) "s" else ""}", style = Type.small, color = p.muted)
                    }
                }
                items(review, key = { "r" + it.id }) { t -> ReviewCard(t, deduction = Store.deduction(t, s), onBusiness = { onOpenTrip(t) }, onPersonal = { onPersonal(t) }) }
            }
            if (logged.isEmpty()) item { EmptyState(year) }
            else {
                logged.groupBy { it.date.take(7) }.toSortedMap(compareByDescending { it }).forEach { (ym, list) ->
                    item(key = "h$ym") {
                        val sum = list.sumOf { it.miles }
                        Column {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp)) {
                                Text(MONTHS[ym.substring(5).toInt() - 1].uppercase(), style = Type.label, color = p.muted, modifier = Modifier.weight(1f))
                                Text("${fmtMi(sum)} MI · ${fmtUsd(list.sumOf { Store.deduction(it, s) })}", style = Type.label, color = p.ink)
                            }
                            Box(Modifier.fillMaxWidth().height(2.dp).background(p.ink))
                            list.forEach { t -> TripRow(t, Store.deduction(t, s)) { onOpenTrip(t) } }
                        }
                    }
                }
            }
        }
        // Thumb-reach action bar
        Column(Modifier.fillMaxWidth().background(p.bg)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (active != null) {
                    BigButton("Log trip", Modifier.weight(1f), kind = "outline", onClick = onLog)
                    BigButton("Finish trip", Modifier.weight(1.4f), kind = "amber", onClick = onFinish)
                } else {
                    BigButton("Start trip", Modifier.weight(1f), kind = "outline", onClick = onStart)
                    BigButton("+ Log trip", Modifier.weight(1.4f), onClick = onLog)
                }
            }
        }
    }
    if (pickYear) YearDialog(pickable, year, onPick = { onYear(it); pickYear = false }, onDismiss = { pickYear = false })
}

@Composable
private fun YearDialog(years: List<Int>, selected: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tax year", style = Type.title, color = p.ink) },
        text = {
            Column {
                years.forEach { y ->
                    val sel = y == selected
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(y) }.padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(y.toString(), style = Type.signNum, color = if (sel) p.signText else p.ink, modifier = Modifier.weight(1f))
                        if (sel) Text("✓", style = Type.signNum, color = p.signText)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ActiveBanner(a: ActiveTrip, acc: Double?, onDiscard: () -> Unit) {
    val p = LocalPalette.current
    val since = SimpleDateFormat("h:mm a", Locale.US).format(Date(a.startedAt))
    val status = when {
        acc == null -> "Finding your location…"
        acc > 50 -> "Weak GPS signal (±${acc.toInt()} m)"
        else -> "GPS on (±${acc.toInt()} m)"
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.amberBg)
            .border(1.5.dp, p.amber, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (a.auto) "DRIVE DETECTED" else "TRACKING WITH GPS", style = Type.label, color = p.amber)
            Text("${fmtMi(a.miles)} mi", style = Type.title.copy(fontSize = 30.sp), color = p.ink)
            Text("Since $since · $status" + if (a.gaps > 0) " · ${a.gaps} signal gap${if (a.gaps > 1) "s" else ""}" else "",
                style = Type.small, color = p.ink)
        }
        TextButton(onClick = onDiscard) { Text("DISCARD", style = Type.label, color = p.ink) }
    }
}

@Composable
private fun ReviewCard(t: Trip, deduction: Double, onBusiness: () -> Unit, onPersonal: () -> Unit) {
    val p = LocalPalette.current
    val whenText = t.startTime?.let { SimpleDateFormat("EEE MMM d · h:mm a", Locale.US).format(Date(it)) } ?: t.date
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.surface)
            .border(1.5.dp, p.line, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(whenText, style = Type.bodyBold, color = p.ink, modifier = Modifier.weight(1f))
            Text("${fmtMi(t.miles)} mi", style = Type.signNum.copy(fontSize = 19.sp), color = p.ink)
        }
        Text(listOf(t.from.ifBlank { "Start" }, t.to.ifBlank { "End" }).joinToString(" → "), style = Type.small, color = p.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onPersonal, modifier = Modifier.weight(1f)) { Text("Personal", style = Type.bodyBold, color = p.ink) }
            androidx.compose.material3.Button(onClick = onBusiness, modifier = Modifier.weight(1f)) {
                Text("Business · ${fmtUsd(deduction)}", style = Type.bodyBold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun TripRow(t: Trip, deduction: Double, onClick: () -> Unit) {
    val p = LocalPalette.current
    val cal = Calendar.getInstance().apply { time = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(t.date) ?: Date() }
    val dow = SimpleDateFormat("EEE", Locale.US).format(cal.time).uppercase()
    val route = listOf(t.from, t.to).filter { it.isNotBlank() }.joinToString(" → ")
    val sub = listOf(t.client, route).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "No route entered" }
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(cal.get(Calendar.DAY_OF_MONTH).toString(), style = Type.signNum, color = p.ink)
                Text(dow, style = Type.tiny.copy(fontSize = 11.sp), color = p.muted)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.purpose, style = Type.bodyBold, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (t.roundTrip) Tag("RT")
                    if (t.source != null) Tag("GPS")
                }
                Text(sub, style = Type.small, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(fmtMi(t.miles), style = Type.signNum.copy(fontSize = 19.sp), color = p.ink)
                Text(fmtUsd(deduction), style = Type.small.copy(fontSize = 12.sp), color = p.muted)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
    }
}

@Composable
private fun Tag(text: String) {
    val p = LocalPalette.current
    Text(text, style = Type.tiny.copy(fontSize = 10.sp), color = p.muted,
        modifier = Modifier.padding(start = 6.dp).border(1.dp, p.line, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp))
}

@Composable
private fun EmptyState(year: Int) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().border(2.dp, p.line, RoundedCornerShape(14.dp)).padding(horizontal = 20.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("No $year trips logged yet", style = Type.title.copy(fontSize = 19.sp), color = p.ink, textAlign = TextAlign.Center)
        Text("Tap Start trip when you head out, or turn on automatic drive detection in Settings and Mileage Log will notice when you drive.",
            style = Type.body, color = p.muted, textAlign = TextAlign.Center)
    }
}
