package com.tjdoolittle.mileagelog.data

import org.json.JSONArray
import org.json.JSONObject

/** Trip status: logged business trips count toward totals; review trips wait for a decision. */
object Status {
    const val LOGGED = "logged"
    const val REVIEW = "review"
    const val PERSONAL = "personal"
}

data class Trip(
    val id: String,
    val date: String,              // yyyy-MM-dd, local
    val miles: Double,
    val purpose: String = "",
    val client: String = "",
    val from: String = "",
    val to: String = "",
    val notes: String = "",
    val roundTrip: Boolean = false,
    val odoStart: Double? = null,
    val odoEnd: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val source: String? = null,    // "GPS", "Auto GPS", "Google Maps", null = manual
    val status: String = Status.LOGGED,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val startLat: Double? = null,
    val startLng: Double? = null,
    val endLat: Double? = null,
    val endLng: Double? = null,
    val gKey: String? = null,
    val parking: Double = 0.0,     // parking and tolls paid on this trip, in dollars
    val vehicleId: String = "",    // "" = the first vehicle
) {
    val year: Int get() = date.take(4).toIntOrNull() ?: 0
    val month: Int get() = date.substring(5, 7).toIntOrNull() ?: 1

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("date", date); put("miles", miles)
        put("purpose", purpose); put("client", client); put("from", from); put("to", to)
        put("notes", notes); put("roundTrip", roundTrip)
        put("odoStart", odoStart ?: JSONObject.NULL); put("odoEnd", odoEnd ?: JSONObject.NULL)
        put("createdAt", createdAt)
        if (source != null) put("source", source)
        put("status", status)
        startTime?.let { put("startTime", it) }; endTime?.let { put("endTime", it) }
        startLat?.let { put("startLat", it) }; startLng?.let { put("startLng", it) }
        endLat?.let { put("endLat", it) }; endLng?.let { put("endLng", it) }
        gKey?.let { put("gKey", it) }
        if (parking > 0) put("parking", parking)
        if (vehicleId.isNotEmpty()) put("vehicleId", vehicleId)
    }

    companion object {
        fun fromJson(o: JSONObject): Trip? {
            val id = o.optString("id"); val date = o.optString("date")
            if (id.isEmpty() || date.length < 10) return null
            return Trip(
                id = id, date = date.take(10), miles = o.optDouble("miles", 0.0),
                purpose = o.optString("purpose"), client = o.optString("client"),
                from = o.optString("from"), to = o.optString("to"), notes = o.optString("notes"),
                roundTrip = o.optBoolean("roundTrip", false),
                odoStart = o.optDoubleOrNull("odoStart"), odoEnd = o.optDoubleOrNull("odoEnd"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                source = o.optStringOrNull("source"),
                status = o.optString("status", Status.LOGGED).ifEmpty { Status.LOGGED },
                startTime = o.optLongOrNull("startTime"), endTime = o.optLongOrNull("endTime"),
                startLat = o.optDoubleOrNull("startLat"), startLng = o.optDoubleOrNull("startLng"),
                endLat = o.optDoubleOrNull("endLat"), endLng = o.optDoubleOrNull("endLng"),
                gKey = o.optStringOrNull("gKey"),
                parking = o.optDouble("parking", 0.0).takeIf { !it.isNaN() && it > 0 } ?: 0.0,
                vehicleId = o.optString("vehicleId"),
            )
        }
    }
}

data class Place(val name: String, val lat: Double, val lng: Double) {
    fun toJson(): JSONObject = JSONObject().put("name", name).put("lat", lat).put("lng", lng)
    companion object {
        fun fromJson(o: JSONObject): Place? {
            val n = o.optString("name"); if (n.isEmpty()) return null
            return Place(n, o.optDouble("lat"), o.optDouble("lng"))
        }
    }
}

/** Odometer readings for one tax year. */
data class YearOdo(val start: Double? = null, val end: Double? = null)

/** A vehicle used for business. Odometer readings by year feed Schedule C, Part IV in the tax report. */
data class Vehicle(
    val id: String,
    val name: String,
    val inService: String = "",              // yyyy-MM-dd the vehicle was first used for business
    val odometer: Map<Int, YearOdo> = emptyMap(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("inService", inService)
        put("odometer", JSONObject().also { o ->
            odometer.forEach { (y, r) ->
                o.put(y.toString(), JSONObject().put("start", r.start ?: JSONObject.NULL).put("end", r.end ?: JSONObject.NULL))
            }
        })
    }
    companion object {
        fun fromJson(o: JSONObject): Vehicle? {
            val id = o.optString("id"); if (id.isEmpty()) return null
            val odo = mutableMapOf<Int, YearOdo>()
            o.optJSONObject("odometer")?.let { m ->
                m.keys().forEach { k -> val y = k.toIntOrNull(); val r = m.optJSONObject(k); if (y != null && r != null) odo[y] = YearOdo(r.optDoubleOrNull("start"), r.optDoubleOrNull("end")) }
            }
            return Vehicle(id, o.optString("name").ifBlank { "Vehicle" }, o.optString("inService"), odo)
        }
    }
}

/** A mileage rate that applies from `from` (yyyy-MM-dd) until the next rate starts. */
data class RatePeriod(val from: String, val rate: Double)

data class Settings(
    val rates: Map<Int, Double> = emptyMap(),          // older per-year overrides (web app backups)
    val ratePeriods: List<RatePeriod>? = null,         // null = use the IRS schedule
    val places: List<Place> = emptyList(),
    val autoDetect: Boolean = false,
    val minAutoMiles: Double = 0.5,
    val homeAddress: String = "",
    val workAddress: String = "",
    val introDismissed: Boolean = false,
    val vehicles: List<Vehicle> = emptyList(),
    val reportName: String = "",                       // name or business printed on the tax report
    val theme: String = "system",                      // "system", "light", or "dark"
    val seenNews: String = "",                         // id of the last "What's new" card dismissed
    val reviewAsked: Boolean = false,                  // the store's rating prompt has been requested once
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("rates", JSONObject().also { r -> rates.forEach { (y, v) -> r.put(y.toString(), v) } })
        ratePeriods?.let { list -> put("ratePeriods", JSONArray().also { a -> list.forEach { a.put(JSONObject().put("from", it.from).put("rate", it.rate)) } }) }
        put("places", JSONArray().also { a -> places.forEach { a.put(it.toJson()) } })
        put("autoDetect", autoDetect)
        put("minAutoMiles", minAutoMiles)
        put("homeAddress", homeAddress)
        put("workAddress", workAddress)
        put("introDismissed", introDismissed)
        put("vehicles", JSONArray().also { a -> vehicles.forEach { a.put(it.toJson()) } })
        put("reportName", reportName)
        put("theme", theme)
        put("seenNews", seenNews)
        put("reviewAsked", reviewAsked)
    }
    companion object {
        fun fromJson(o: JSONObject?): Settings {
            if (o == null) return Settings()
            val rates = mutableMapOf<Int, Double>()
            o.optJSONObject("rates")?.let { r -> r.keys().forEach { k -> k.toIntOrNull()?.let { y -> rates[y] = r.optDouble(k) } } }
            val places = mutableListOf<Place>()
            o.optJSONArray("places")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let { Place.fromJson(it) }?.let(places::add) }
            val periods = o.optJSONArray("ratePeriods")?.let { a ->
                (0 until a.length()).mapNotNull { i ->
                    a.optJSONObject(i)?.let { r -> r.optString("from").takeIf { it.length == 10 }?.let { RatePeriod(it, r.optDouble("rate")) } }
                }.filter { !it.rate.isNaN() && it.rate > 0 }
            }
            return Settings(
                rates = rates, ratePeriods = periods, places = places,
                autoDetect = o.optBoolean("autoDetect", false), minAutoMiles = o.optDouble("minAutoMiles", 0.5),
                homeAddress = o.optString("homeAddress"), workAddress = o.optString("workAddress"),
                introDismissed = o.optBoolean("introDismissed", false),
                vehicles = o.optJSONArray("vehicles")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(Vehicle::fromJson) } } ?: emptyList(),
                reportName = o.optString("reportName"),
                theme = o.optString("theme").ifBlank { "system" },
                seenNews = o.optString("seenNews"),
                reviewAsked = o.optBoolean("reviewAsked", false),
            )
        }
    }
}

/** A drive being measured right now. Saved often so a restart doesn't lose it. */
data class ActiveTrip(
    val startedAt: Long,
    val auto: Boolean,
    val meters: Double = 0.0,
    val gaps: Int = 0,
    val fixes: Int = 0,
    val startLat: Double? = null,
    val startLng: Double? = null,
    val lastLat: Double? = null,
    val lastLng: Double? = null,
    val lastFixAt: Long = 0,
    val lastMoveAt: Long = startedAt,
    val purpose: String = "",
    val client: String = "",
    val from: String = "",
) {
    val miles: Double get() = meters / 1609.344

    fun toJson(): JSONObject = JSONObject().apply {
        put("startedAt", startedAt); put("auto", auto); put("meters", meters); put("gaps", gaps); put("fixes", fixes)
        startLat?.let { put("startLat", it) }; startLng?.let { put("startLng", it) }
        lastLat?.let { put("lastLat", it) }; lastLng?.let { put("lastLng", it) }
        put("lastFixAt", lastFixAt); put("lastMoveAt", lastMoveAt)
        put("purpose", purpose); put("client", client); put("from", from)
    }
    companion object {
        fun fromJson(o: JSONObject?): ActiveTrip? {
            if (o == null || !o.has("startedAt")) return null
            return ActiveTrip(
                startedAt = o.optLong("startedAt"), auto = o.optBoolean("auto"),
                meters = o.optDouble("meters", 0.0), gaps = o.optInt("gaps"), fixes = o.optInt("fixes"),
                startLat = o.optDoubleOrNull("startLat"), startLng = o.optDoubleOrNull("startLng"),
                lastLat = o.optDoubleOrNull("lastLat"), lastLng = o.optDoubleOrNull("lastLng"),
                lastFixAt = o.optLong("lastFixAt"), lastMoveAt = o.optLong("lastMoveAt"),
                purpose = o.optString("purpose"), client = o.optString("client"), from = o.optString("from"),
            )
        }
    }
}

data class AppData(
    val trips: List<Trip> = emptyList(),
    val settings: Settings = Settings(),
    val active: ActiveTrip? = null,
)

internal fun JSONObject.optDoubleOrNull(k: String): Double? =
    if (!has(k) || isNull(k)) null else optDouble(k).takeIf { !it.isNaN() }
internal fun JSONObject.optLongOrNull(k: String): Long? =
    if (!has(k) || isNull(k)) null else optLong(k)
internal fun JSONObject.optStringOrNull(k: String): String? =
    if (!has(k) || isNull(k)) null else optString(k).takeIf { it.isNotEmpty() }
