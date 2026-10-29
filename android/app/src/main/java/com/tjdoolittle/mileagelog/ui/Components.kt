package com.tjdoolittle.mileagelog.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
val MON = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

fun fmtMi(v: Double): String = String.format(Locale.US, "%,.1f", v)
fun fmtUsd(v: Double): String = NumberFormat.getCurrencyInstance(Locale.US).format(v)

/** 0.76 -> "76", 0.725 -> "72.5" */
fun fmtCents(rate: Double): String {
    val c = Math.round(rate * 1000) / 10.0
    return if (c % 1.0 == 0.0) String.format(Locale.US, "%.0f", c) else String.format(Locale.US, "%.1f", c)
}

/** "2026-07-01" -> "Jul 1, 2026" */
fun longDate(iso: String): String = runCatching {
    java.time.LocalDate.parse(iso).format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))
}.getOrDefault(iso)

fun dayBefore(iso: String): String = runCatching { java.time.LocalDate.parse(iso).minusDays(1).toString() }.getOrDefault(iso)

/** Mechanical odometer: six white-on-black digits and a black-on-white tenths wheel. */
@Composable
fun Odometer(miles: Double) {
    val p = LocalPalette.current
    val digits = String.format(Locale.US, "%08.1f", miles.coerceIn(0.0, 999999.9)).replace(".", "")
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(p.odoBg).padding(7.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        digits.forEachIndexed { i, c ->
            val tenth = i == digits.lastIndex
            if (tenth) Spacer(Modifier.width(3.dp))
            Box(
                Modifier.width(27.dp).height(42.dp).clip(RoundedCornerShape(3.dp))
                    .background(if (tenth) p.odoFg else p.odoBg),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(targetState = c, transitionSpec = {
                    slideInVertically { it } togetherWith slideOutVertically { -it }
                }, label = "digit") { ch ->
                    Text(ch.toString(), fontFamily = Fonts.mono, fontWeight = FontWeight.SemiBold, fontSize = 34.sp,
                        color = if (tenth) p.odoBg else p.odoFg)
                }
            }
        }
        Text("MI", style = Type.label, color = p.odoFg, modifier = Modifier.padding(start = 6.dp, end = 2.dp))
    }
}

@Composable
fun SignCard(
    year: Int, miles: Double, deduction: Double, rate: Double, statMid: String, statMidLabel: String, trips: Int,
    byMonth: List<Double>, highlightMonth: Int?, onYearClick: () -> Unit, onRateClick: () -> Unit,
) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.sign).padding(6.dp)) {
        Column(
            Modifier.fillMaxWidth().border(2.5.dp, p.signInk, RoundedCornerShape(10.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                // Both labels are tappable: the year opens the year picker, the rate opens the rate schedule.
                Text("$year ▾  BUSINESS MILES", style = Type.label, color = p.signInk,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onYearClick).padding(vertical = 4.dp, horizontal = 2.dp))
                Text("${fmtCents(rate)}¢ / MI ▾", style = Type.label, color = p.signInk,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onRateClick).padding(vertical = 4.dp, horizontal = 2.dp))
            }
            Odometer(miles)
            Box(Modifier.fillMaxWidth().height(1.5.dp).background(p.signInk.copy(alpha = 0.35f)))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat(fmtUsd(deduction), "DEDUCTION", Modifier.weight(1.55f))
                Stat(statMid, statMidLabel, Modifier.weight(1f))
                Stat(trips.toString(), "TRIPS", Modifier.weight(0.7f))
            }
            MonthBars(byMonth, highlightMonth)
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    val p = LocalPalette.current
    Column(modifier) {
        Text(value, style = Type.signNum, color = p.signInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = Type.tiny, color = p.signSoft, maxLines = 1)
    }
}

@Composable
private fun MonthBars(byMonth: List<Double>, highlight: Int?) {
    val p = LocalPalette.current
    val max = (byMonth.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    Column {
        Row(Modifier.fillMaxWidth().height(46.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            byMonth.forEachIndexed { i, m ->
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    val frac = (m / max).toFloat().coerceIn(0.05f, 1f)
                    Box(Modifier.fillMaxWidth().fillMaxHeight(frac).clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(if (i == highlight) p.signInk else p.signInk.copy(alpha = 0.22f)))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            "JFMAMJJASOND".forEach { Text(it.toString(), Modifier.weight(1f), style = Type.tiny.copy(fontSize = 10.sp), color = p.signSoft, textAlign = TextAlign.Center) }
        }
    }
}

@Composable
fun BigButton(text: String, modifier: Modifier = Modifier, kind: String = "primary", enabled: Boolean = true, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(12.dp)
    val label: @Composable () -> Unit = { Text(text.uppercase(), style = Type.label.copy(fontSize = 15.sp, letterSpacing = 0.6.sp)) }
    when (kind) {
        "outline" -> OutlinedButton(onClick, modifier.heightIn(min = 52.dp), enabled = enabled, shape = shape,
            border = androidx.compose.foundation.BorderStroke(2.dp, p.ink),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = p.ink)) { label() }
        "danger" -> OutlinedButton(onClick, modifier.heightIn(min = 48.dp), enabled = enabled, shape = shape,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, p.danger),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = p.danger)) { label() }
        "amber" -> Button(onClick, modifier.heightIn(min = 52.dp), enabled = enabled, shape = shape,
            colors = ButtonDefaults.buttonColors(containerColor = p.amber, contentColor = Color(0xFF1A1405))) { label() }
        else -> Button(onClick, modifier.heightIn(min = 52.dp), enabled = enabled, shape = shape,
            colors = ButtonDefaults.buttonColors(containerColor = p.sign, contentColor = p.signInk)) { label() }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = Type.tiny, color = LocalPalette.current.muted, modifier = modifier)
}
