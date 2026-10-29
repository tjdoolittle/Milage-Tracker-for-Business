package com.tjdoolittle.mileagelog.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/**
 * All app data lives in one JSON file in the app's private storage.
 * The UI and the tracking service share this object (same process).
 */
object Store {
    /** IRS standard business rates. 2026 changed midyear: 72.5¢ through June 30, 76¢ from July 1. */
    val IRS_RATES = listOf(
        RatePeriod("2024-01-01", 0.67),
        RatePeriod("2025-01-01", 0.70),
        RatePeriod("2026-01-01", 0.725),
        RatePeriod("2026-07-01", 0.76),
    )

    private lateinit var file: File
    private val _state = MutableStateFlow(AppData())
    val state: StateFlow<AppData> = _state.asStateFlow()
    val data: AppData get() = _state.value

    @Synchronized
    fun init(ctx: Context) {
        if (::file.isInitialized) return
        file = File(ctx.filesDir, "mileage-log.json")
        _state.value = runCatching { parse(JSONObject(file.readText())) }.getOrDefault(AppData())
    }

    @Synchronized
    fun update(change: (AppData) -> AppData) {
        val next = change(_state.value)
        _state.value = next
        save(next)
    }

    private fun parse(o: JSONObject): AppData {
        val trips = mutableListOf<Trip>()
        o.optJSONArray("trips")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let { Trip.fromJson(it) }?.let(trips::add) }
        return AppData(trips, Settings.fromJson(o.optJSONObject("settings")), ActiveTrip.fromJson(o.optJSONObject("active")))
    }

    private fun save(d: AppData) {
        val o = JSONObject()
        o.put("trips", JSONArray().also { a -> d.trips.forEach { a.put(it.toJson()) } })
        o.put("settings", d.settings.toJson())
        d.active?.let { o.put("active", it.toJson()) }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(o.toString())
        if (!tmp.renameTo(file)) { file.writeText(o.toString()); tmp.delete() }
    }

    // ---------- helpers ----------
    fun newId(): String = "t-" + UUID.randomUUID().toString().replace("-", "").take(12)

    /** The rate schedule in effect, oldest first. */
    fun schedule(s: Settings = data.settings): List<RatePeriod> {
        val list = s.ratePeriods ?: (
            IRS_RATES.filter { p -> s.rates[p.from.take(4).toInt()] == null } +
                s.rates.map { (y, r) -> RatePeriod("%04d-01-01".format(Locale.US, y), r) }
            )
        return list.sortedBy { it.from }
    }

    /** The rate for a trip on `date` (yyyy-MM-dd): the latest rate that started on or before it. */
    fun rateOn(date: String, s: Settings = data.settings): Double {
        val sch = schedule(s)
        if (sch.isEmpty()) return 0.725
        return sch.lastOrNull { it.from <= date }?.rate ?: sch.first().rate
    }

    fun deduction(t: Trip, s: Settings = data.settings): Double = t.miles * rateOn(t.date, s)

    fun setSchedule(list: List<RatePeriod>) =
        setSettings(data.settings.copy(ratePeriods = list.distinctBy { it.from }.sortedBy { it.from }, rates = emptyMap()))

    fun saveTrip(t: Trip) = update { d ->
        val i = d.trips.indexOfFirst { it.id == t.id }
        d.copy(trips = if (i >= 0) d.trips.toMutableList().also { it[i] = t } else d.trips + t)
    }

    fun deleteTrip(id: String) = update { d -> d.copy(trips = d.trips.filterNot { it.id == id }) }

    fun setActive(a: ActiveTrip?) = update { it.copy(active = a) }

    fun setSettings(s: Settings) = update { it.copy(settings = s) }

    /** Remember a named spot so later trips that start or end there get labeled. */
    fun learnPlace(name: String, lat: Double?, lng: Double?) {
        if (lat == null || lng == null || name.isBlank() || Geo.looksLikeCoords(name)) return
        val s = data.settings
        if (s.places.any { Geo.meters(lat, lng, it.lat, it.lng) < 200 }) return
        setSettings(s.copy(places = (s.places + Place(name.trim(), lat, lng)).takeLast(300)))
    }

    fun today(): String = isoDate(System.currentTimeMillis())

    fun isoDate(ms: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }
}
