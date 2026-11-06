package com.tjdoolittle.mileagelog.data

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Yearly numbers behind the tax report. Kept apart from drawing so they're easy to check. */
class ReportData(val year: Int, d: AppData = Store.data) {
    val s = d.settings
    val trips: List<Trip> = d.trips.filter { it.status == Status.LOGGED && it.year == year }
        .sortedWith(compareBy({ it.date }, { it.startTime ?: it.createdAt }))
    private val personal = d.trips.filter { it.status == Status.PERSONAL && it.year == year }

    val miles = trips.sumOf { it.miles }
    val deduction = trips.sumOf { Math.round(it.miles * Store.rateOn(it.date, s) * 100) / 100.0 }
    val parking = trips.sumOf { it.parking }

    class VehicleRow(val name: String, val inService: String, val odo: YearOdo?, val business: Double, val commuting: Double) {
        val total: Double? = odo?.let { o -> if (o.start != null && o.end != null && o.end >= o.start) o.end - o.start else null }
        val other: Double? = total?.let { maxOf(0.0, it - business - commuting) }
    }

    val vehicles: List<VehicleRow> = run {
        val list = s.vehicles.ifEmpty { listOf(Vehicle("", "Vehicle")) }
        fun owner(t: Trip) = (s.vehicles.firstOrNull { it.id == t.vehicleId } ?: s.vehicles.firstOrNull())?.id ?: ""
        list.map { v ->
            VehicleRow(
                name = v.name, inService = v.inService, odo = v.odometer[year],
                business = trips.filter { owner(it) == v.id }.sumOf { it.miles },
                commuting = personal.filter { owner(it) == v.id && Store.isCommute(it.from, it.to, s) }.sumOf { it.miles },
            )
        }
    }

    class RateRow(val rate: Double, val from: String, val to: String, val miles: Double)
    val rates: List<RateRow> = run {
        val sch = Store.schedule(s)
        trips.groupBy { Store.rateOn(it.date, s) to (sch.lastOrNull { p -> p.from <= it.date }?.from ?: "") }
            .map { (k, ts) -> RateRow(k.first, ts.first().date, ts.last().date, ts.sumOf { it.miles }) }
            .sortedBy { it.from }
    }

    class MonthRow(val month: Int, val count: Int, val miles: Double, val deduction: Double, val parking: Double)
    val months: List<MonthRow> = (1..12).map { m ->
        val ts = trips.filter { it.month == m }
        MonthRow(m, ts.size, ts.sumOf { it.miles }, ts.sumOf { Math.round(it.miles * Store.rateOn(it.date, s) * 100) / 100.0 }, ts.sumOf { it.parking })
    }
}

/** Draws the tax report as a letter-size PDF with the platform's PdfDocument. */
object ReportPdf {
    private const val W = 612f
    private const val H = 792f
    private const val M = 42f
    private val usd = NumberFormat.getCurrencyInstance(Locale.US)
    private fun mi(v: Double) = String.format(Locale.US, "%,.1f", v)
    private fun cents(r: Double) = (Math.round(r * 1000) / 10.0).let { if (it == Math.floor(it)) "${it.toInt()}¢" else "$it¢" }
    private fun shortDate(iso: String) = runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("MMM d", Locale.US)) }.getOrDefault(iso)
    private fun longDate(iso: String) = runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)) }.getOrDefault(iso)

    private val green = Color.rgb(13, 107, 74)
    private val ink = Color.rgb(24, 28, 26)
    private val muted = Color.rgb(98, 106, 102)
    private val line = Color.rgb(214, 219, 216)
    private val band = Color.rgb(238, 243, 240)

    private fun paint(size: Float, bold: Boolean = false, color: Int = ink) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private class Writer(val doc: PdfDocument, val footer: String) {
        var page: PdfDocument.Page? = null
        var n = 0
        var y = 0f
        val c get() = page!!.canvas

        fun newPage() {
            finish()
            n++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), n).create())
            y = M
            val f = paint(8f, color = muted)
            c.drawText(footer, M, H - 24f, f)
            val pg = "Page $n"
            c.drawText(pg, W - M - f.measureText(pg), H - 24f, f)
        }
        fun finish() { page?.let { doc.finishPage(it) }; page = null }
        /** Starts a new page when fewer than `h` points are left. Returns true if it did. */
        fun room(h: Float): Boolean { if (y + h > H - 48f) { newPage(); return true }; return false }
    }

    /** Cuts text to fit `width`, adding an ellipsis. */
    private fun fit(text: String, p: Paint, width: Float): String {
        if (p.measureText(text) <= width) return text
        var t = text
        while (t.isNotEmpty() && p.measureText("$t…") > width) t = t.dropLast(1)
        return "$t…"
    }

    /** Word-wraps text to lines no wider than `width`. */
    private fun wrap(text: String, p: Paint, width: Float): List<String> {
        val out = mutableListOf<String>()
        text.split("\n").forEach { para ->
            var cur = ""
            para.split(" ").forEach { w ->
                val next = if (cur.isEmpty()) w else "$cur $w"
                if (p.measureText(next) <= width || cur.isEmpty()) cur = next else { out += cur; cur = w }
            }
            out += cur
        }
        return out
    }

    private fun Writer.paragraph(text: String, p: Paint, gap: Float = 4f) {
        wrap(text, p, W - 2 * M).forEach { l -> room(p.textSize + 3); y += p.textSize; c.drawText(l, M, y, p); y += 3f }
        y += gap
    }

    private fun Writer.heading(text: String) {
        room(60f)
        y += 14f
        val p = paint(12f, bold = true, color = green)
        y += 12f
        c.drawText(text.uppercase(Locale.US), M, y, p)
        y += 6f
        c.drawRect(M, y, W - M, y + 1.2f, Paint().apply { color = green })
        y += 10f
    }

    /** One table row. `cols` are (width, right-aligned); widths are shares of the page width. */
    private fun Writer.row(cells: List<String>, cols: List<Pair<Float, Boolean>>, p: Paint, shade: Boolean = false, rule: Boolean = true) {
        val h = p.textSize + 9f
        room(h)
        if (shade) c.drawRect(M, y, W - M, y + h, Paint().apply { color = band })
        var x = M
        val total = W - 2 * M
        cells.forEachIndexed { i, cell ->
            val (share, right) = cols[i]
            val w = total * share
            val t = fit(cell, p, w - 8f)
            val tx = if (right) x + w - 4f - p.measureText(t) else x + 4f
            c.drawText(t, tx, y + p.textSize + 3f, p)
            x += w
        }
        y += h
        if (rule) c.drawRect(M, y - 0.5f, W - M, y, Paint().apply { color = line })
    }

    fun write(out: OutputStream, year: Int, d: AppData = Store.data) {
        val r = ReportData(year, d)
        val s = r.s
        val doc = PdfDocument()
        val w = Writer(doc, "Tax Mileage Tracker · $year mileage log · Not tax advice. Not affiliated with the IRS or any government agency.")
        w.newPage()

        // ---- Title ----
        w.y += 20f
        w.c.drawText("$year Business Mileage Log", M, w.y, paint(22f, bold = true))
        w.y += 18f
        val who = s.reportName.trim()
        w.c.drawText(listOfNotNull(who.ifBlank { null }, "Prepared ${longDate(Store.today())}").joinToString("  ·  "), M, w.y, paint(10f, color = muted))
        w.y += 14f

        // ---- Summary ----
        w.heading("Summary")
        val big = paint(18f, bold = true)
        val label = paint(8.5f, bold = true, color = muted)
        val boxes = listOf(
            mi(r.miles) to "BUSINESS MILES",
            usd.format(r.deduction) to "MILEAGE DEDUCTION",
            usd.format(r.parking) to "PARKING AND TOLLS",
            usd.format(r.deduction + r.parking) to "TOTAL",
        )
        val bw = (W - 2 * M) / 4
        w.room(56f)
        boxes.forEachIndexed { i, (v, l) ->
            val x = M + i * bw
            w.c.drawRect(x + 3, w.y, x + bw - 3, w.y + 50f, Paint().apply { color = band })
            w.c.drawText(fit(v, big, bw - 16f), x + 10, w.y + 24f, big)
            w.c.drawText(l, x + 10, w.y + 40f, label)
        }
        w.y += 58f
        w.paragraph("${r.trips.size} business trips. Each trip uses the IRS standard mileage rate in effect on the day it was driven. " +
            "Parking fees and tolls for business trips are deductible in addition to the standard mileage rate.", paint(9f, color = muted))

        // ---- Vehicles (Schedule C, Part IV) ----
        w.heading("Vehicle information")
        w.paragraph("The numbers Schedule C (Form 1040), Part IV, asks for. Total miles come from the odometer readings entered in Settings.", paint(9f, color = muted), 2f)
        val vCols = listOf(0.26f to false, 0.14f to false, 0.12f to true, 0.12f to true, 0.12f to true, 0.12f to true, 0.12f to true)
        val head = paint(8.5f, bold = true, color = muted)
        val body = paint(9.5f)
        w.row(listOf("Vehicle", "In service", "Odometer", "Total miles", "Business", "Commuting", "Other"), vCols, head)
        r.vehicles.forEach { v ->
            val odo = v.odo?.let { o -> if (o.start != null && o.end != null) "${mi(o.start).removeSuffix(".0")}–${mi(o.end).removeSuffix(".0")}" else null }
            w.row(listOf(v.name, if (v.inService.isBlank()) "Not entered" else longDate(v.inService), odo ?: "Not entered",
                v.total?.let(::mi) ?: "Not entered", mi(v.business), mi(v.commuting), v.other?.let(::mi) ?: "Not entered"), vCols, body)
        }
        w.paragraph("Commuting is trips marked personal between your saved home and work addresses. Other is total miles minus business and commuting.",
            paint(8.5f, color = muted), 0f)

        // ---- Rates ----
        if (r.rates.isNotEmpty()) {
            w.heading("Rates used")
            val rCols = listOf(0.4f to false, 0.2f to true, 0.2f to true, 0.2f to true)
            w.row(listOf("Trips dated", "Rate", "Miles", "Deduction"), rCols, head)
            r.rates.forEach { rr ->
                w.row(listOf("${shortDate(rr.from)} to ${shortDate(rr.to)}", cents(rr.rate) + " per mile", mi(rr.miles), usd.format(rr.miles * rr.rate)), rCols, body)
            }
            w.paragraph("Source: IRS standard mileage rates, ${Store.IRS_SOURCE_URL}", paint(8.5f, color = muted), 0f)
        }

        // ---- Months ----
        w.heading("By month")
        val mCols = listOf(0.28f to false, 0.12f to true, 0.2f to true, 0.2f to true, 0.2f to true)
        w.row(listOf("Month", "Trips", "Miles", "Deduction", "Parking and tolls"), mCols, head)
        val monthNames = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        r.months.forEachIndexed { i, m ->
            w.row(listOf(monthNames[m.month - 1], m.count.toString(), mi(m.miles), usd.format(m.deduction), usd.format(m.parking)), mCols, body, shade = i % 2 == 1)
        }
        w.row(listOf("Total", r.trips.size.toString(), mi(r.miles), usd.format(r.deduction), usd.format(r.parking)), mCols, paint(9.5f, bold = true))

        // ---- Trip log ----
        w.newPage()
        w.heading("Trip log")
        val multi = s.vehicles.size > 1
        val tCols = if (multi) listOf(0.1f to false, 0.38f to false, 0.14f to false, 0.1f to true, 0.08f to true, 0.11f to true, 0.09f to true)
        else listOf(0.1f to false, 0.5f to false, 0.11f to true, 0.08f to true, 0.11f to true, 0.1f to true)
        val tHead = if (multi) listOf("Date", "Route and purpose", "Vehicle", "Miles", "Rate", "Deduction", "Parking")
        else listOf("Date", "Route and purpose", "Miles", "Rate", "Deduction", "Parking")
        val small = paint(8.5f)
        val sub = paint(8f, color = muted)
        fun header() = w.row(tHead, tCols, head)
        header()
        r.trips.forEachIndexed { i, t ->
            val rate = Store.rateOn(t.date, s)
            val route = listOf(t.from, t.to).filter { it.isNotBlank() }.joinToString(" → ").ifBlank { "Route not entered" } + if (t.roundTrip) " (round trip)" else ""
            val purpose = listOf(t.purpose, t.client).filter { it.isNotBlank() }.joinToString(" · ")
            val h = 30f
            if (w.room(h)) { header() }
            if (i % 2 == 1) w.c.drawRect(M, w.y, W - M, w.y + h, Paint().apply { color = band })
            val total = W - 2 * M
            var x = M
            val cells = listOf(shortDate(t.date), route) + (if (multi) listOf(Store.vehicleFor(t, s)?.name ?: "") else emptyList()) +
                listOf(mi(t.miles), cents(rate), usd.format(t.miles * rate), if (t.parking > 0) usd.format(t.parking) else "")
            cells.forEachIndexed { ci, cell ->
                val (share, right) = tCols[ci]
                val cw = total * share
                val txt = fit(cell, small, cw - 8f)
                val tx = if (right) x + cw - 4f - small.measureText(txt) else x + 4f
                w.c.drawText(txt, tx, w.y + 12f, small)
                if (ci == 1 && purpose.isNotBlank()) w.c.drawText(fit(purpose, sub, cw - 8f), x + 4f, w.y + 24f, sub)
                x += cw
            }
            w.y += h
            w.c.drawRect(M, w.y - 0.5f, W - M, w.y, Paint().apply { color = line })
        }
        if (r.trips.isEmpty()) w.paragraph("No business trips logged for $year.", body)
        w.y += 6f
        val totals = (if (multi) listOf("Total", "${r.trips.size} trips", "") else listOf("Total", "${r.trips.size} trips")) +
            listOf(mi(r.miles), "", usd.format(r.deduction), usd.format(r.parking))
        w.row(totals, tCols, paint(9f, bold = true), rule = false)

        w.y += 10f
        w.paragraph("Records kept with Tax Mileage Tracker. You're responsible for the accuracy of the trips you log. This report isn't tax advice; " +
            "check with a tax professional about your own situation.", paint(8.5f, color = muted))

        w.finish()
        doc.writeTo(out)
        doc.close()
    }
}
