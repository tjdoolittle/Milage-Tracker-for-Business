package com.tjdoolittle.mileagelog.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.Locale

object Exports {
    private fun q(v: Any?): String {
        val s = v?.toString() ?: ""
        return if (s.contains(',') || s.contains('"') || s.contains('\n')) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    /** One row per business trip in the year, plus a totals row. */
    fun csv(year: Int): String {
        val d = Store.data
        val s = d.settings
        val trips = d.trips.filter { it.status == Status.LOGGED && it.year == year }.sortedWith(compareBy({ it.date }, { it.startTime ?: it.createdAt }))
        val rows = mutableListOf<List<Any>>(listOf("Date", "From", "To", "Business purpose", "Client", "Odometer start", "Odometer end",
            "Round trip", "Miles", "Rate per mile", "Deduction", "Notes", "Source"))
        var total = 0.0
        var totalDeduction = 0.0
        trips.forEach { t ->
            val rate = Store.rateOn(t.date, s)
            val ded = Math.round(t.miles * rate * 100) / 100.0
            total += t.miles; totalDeduction += ded
            rows += listOf(t.date, t.from, t.to, t.purpose, t.client, t.odoStart ?: "", t.odoEnd ?: "",
                if (t.roundTrip) "Yes" else "", "%.1f".format(Locale.US, t.miles), "%.3f".format(Locale.US, rate), "%.2f".format(Locale.US, ded),
                t.notes, t.source ?: "Manual")
        }
        rows += listOf("Total", "", "", "", "", "", "", "", "%.1f".format(Locale.US, total), "", "%.2f".format(Locale.US, totalDeduction), "", "")
        return rows.joinToString("\n") { r -> r.joinToString(",") { q(it) } }
    }

    /** Same format as the web app's backup, so files move between them. */
    fun backup(): String {
        val d = Store.data
        return JSONObject().apply {
            put("app", "mileage-log"); put("version", 1); put("platform", "android")
            put("exportedAt", Instant.now().toString())
            put("trips", JSONArray().also { a -> d.trips.forEach { a.put(it.toJson()) } })
            put("settings", d.settings.toJson())
        }.toString(1)
    }

    /** Adds trips not already present. Returns (added, skipped) or null if the file isn't a backup. */
    fun restore(text: String): Pair<Int, Int>? {
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return null
        if (o.optString("app") != "mileage-log") return null
        val arr = o.optJSONArray("trips") ?: return null
        val incoming = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(Trip::fromJson) }
        val have = Store.data.trips.map { it.id }.toSet()
        val add = incoming.filter { it.id !in have }
        val theirs = Settings.fromJson(o.optJSONObject("settings"))
        Store.update { d ->
            val mine = d.settings
            val places = mine.places + theirs.places.filter { p -> mine.places.none { Geo.meters(p.lat, p.lng, it.lat, it.lng) < 200 } }
            d.copy(trips = d.trips + add, settings = mine.copy(rates = theirs.rates + mine.rates, ratePeriods = mine.ratePeriods ?: theirs.ratePeriods, places = places))
        }
        return add.size to (incoming.size - add.size)
    }
}
