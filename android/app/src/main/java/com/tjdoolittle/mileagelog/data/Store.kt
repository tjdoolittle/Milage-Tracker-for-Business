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

    /** Screenshot mode (debug builds only): sample data in memory, nothing saved. */
    var demo = false
        private set

    fun loadDemo(d: AppData) { demo = true; _state.value = d }

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
        if (demo) return
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
        // A custom schedule still picks up IRS rates that start after the user's last entry,
        // so a rate added in an app update reaches everyone who hasn't entered it themselves.
        val list = s.ratePeriods?.let { mine ->
            val last = mine.maxOfOrNull { it.from } ?: ""
            mine + IRS_RATES.filter { it.from > last }
        } ?: (
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

    /** True when the schedule has a rate that starts in `year` (built in or entered by the user). */
    fun hasRateFor(year: Int, s: Settings = data.settings): Boolean = schedule(s).any { it.from.startsWith("$year-") }

    /** Plain-English summary of the built-in IRS rates for a year, or null when this version doesn't have them. */
    fun irsSummary(year: Int): String? {
        val ps = IRS_RATES.filter { it.from.startsWith("$year-") }
        if (ps.isEmpty()) return null
        fun c(r: Double) = (Math.round(r * 1000) / 10.0).let { if (it == Math.floor(it)) "${it.toInt()}¢" else "$it¢" }
        if (ps.size == 1) return "${c(ps[0].rate)} per mile for all of $year"
        val fmt = java.time.format.DateTimeFormatter.ofPattern("MMMM d", Locale.US)
        return ps.mapIndexed { i, p ->
            val start = java.time.LocalDate.parse(p.from)
            val end = ps.getOrNull(i + 1)?.let { java.time.LocalDate.parse(it.from).minusDays(1) } ?: java.time.LocalDate.of(year, 12, 31)
            "${c(p.rate)} from ${start.format(fmt)} through ${end.format(fmt)}"
        }.joinToString(", then ")
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

    /** Common business purposes offered in the trip editor. "Other" lets the driver type their own. */
    val PURPOSES = listOf(
        "Travel between offices", "Client Visits", "Business Meeting", "Sales Calls",
        "Deliveries", "Banking", "Admin Errands", "Job Site Visits",
    )
    const val OTHER_PURPOSE = "Other"
    const val COMMUTE_NOTE = "Regular Everyday Commute is Not Deductible"
    val INTRO = listOf(
        "Tax Mileage Tracker automatically logs every trip you take.",
        "To create your log, you must click \u201cLog trip\u201d after completing each trip that is to be filed.",
        "Categorize your trips and provide other necessary tax information when logging, so that complete logs can simply be downloaded and submitted at tax time.",
    )
    const val IRS_SOURCE_URL = "https://www.irs.gov/tax-professionals/standard-mileage-rates"
    const val GOV_DISCLAIMER = "Tax Mileage Tracker is not affiliated with, endorsed by, or acting for the IRS or any government agency."
    const val RESPONSIBILITY = "Tax Mileage Tracker tracks all your trips, but you're responsible for the data you log. Check each trip before you count it."

    /**
     * Save the home or work address. When it was found on the map, it also becomes a saved
     * place, so GPS trips that start or end there get the address filled in.
     */
    fun setHomeWork(home: Boolean, address: String, lat: Double?, lng: Double?) = update { d ->
        val s = d.settings
        val old = if (home) s.homeAddress else s.workAddress
        var places = s.places.filterNot { old.isNotBlank() && it.name == old }
        if (lat != null && lng != null && address.isNotBlank()) {
            places = places.filterNot { Geo.meters(lat, lng, it.lat, it.lng) < 200 } + Place(address, lat, lng)
        }
        val next = if (home) s.copy(homeAddress = address) else s.copy(workAddress = address)
        d.copy(settings = next.copy(places = places))
    }

    /** True when a trip goes between the saved home and work addresses, in either direction. */
    fun isCommute(from: String, to: String, s: Settings = data.settings): Boolean {
        val h = s.homeAddress.trim().lowercase(); val w = s.workAddress.trim().lowercase()
        if (h.isEmpty() || w.isEmpty()) return false
        val f = from.trim().lowercase(); val t = to.trim().lowercase()
        return (f == h && t == w) || (f == w && t == h)
    }

    // ---------- vehicles ----------
    /** The vehicle a trip counts toward: its own, or the first one when it has none. */
    fun vehicleFor(t: Trip, s: Settings = data.settings): Vehicle? =
        s.vehicles.firstOrNull { it.id == t.vehicleId } ?: s.vehicles.firstOrNull()

    fun saveVehicle(v: Vehicle) = update { d ->
        val list = d.settings.vehicles
        val i = list.indexOfFirst { it.id == v.id }
        d.copy(settings = d.settings.copy(vehicles = if (i >= 0) list.toMutableList().also { it[i] = v } else list + v))
    }

    /** Removes a vehicle. Its trips move to the first remaining vehicle. */
    fun deleteVehicle(id: String) = update { d ->
        val left = d.settings.vehicles.filterNot { it.id == id }
        d.copy(
            settings = d.settings.copy(vehicles = left),
            trips = d.trips.map { if (it.vehicleId == id) it.copy(vehicleId = "") else it },
        )
    }

    fun newVehicleId(): String = "v-" + UUID.randomUUID().toString().replace("-", "").take(8)

    fun today(): String = isoDate(System.currentTimeMillis())

    fun isoDate(ms: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }
}
