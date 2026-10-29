package com.tjdoolittle.mileagelog.tracking

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tjdoolittle.mileagelog.MileageApp
import com.tjdoolittle.mileagelog.R
import com.tjdoolittle.mileagelog.data.ActiveTrip
import com.tjdoolittle.mileagelog.data.Geo
import com.tjdoolittle.mileagelog.data.Status
import com.tjdoolittle.mileagelog.data.Store
import com.tjdoolittle.mileagelog.data.Trip
import com.tjdoolittle.mileagelog.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToLong

/** Starts, updates, and finishes the trip being measured. */
object Tracker {
    /** The in-progress trip, updated on every GPS fix. The Store copy is saved less often. */
    val live = MutableStateFlow<ActiveTrip?>(null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun current(): ActiveTrip? = live.value ?: Store.data.active

    fun update(a: ActiveTrip, persist: Boolean) {
        live.value = a
        if (persist) Store.setActive(a)
    }

    fun startManual(ctx: Context) {
        if (current() == null) update(ActiveTrip(startedAt = System.currentTimeMillis(), auto = false), persist = true)
        TrackingService.startManual(ctx)
    }

    fun discard(ctx: Context) {
        live.value = null
        Store.setActive(null)
        ctx.stopService(Intent(ctx, TrackingService::class.java))
    }

    /**
     * Ends the trip and saves it to the review list. Returns the new trip's id,
     * or null when an automatic drive was too short to keep.
     */
    fun finish(ctx: Context, endedByStop: Boolean = false): String? {
        val a = current()
        live.value = null
        Store.setActive(null)
        ctx.stopService(Intent(ctx, TrackingService::class.java))
        if (a == null) return null

        val miles = (a.miles * 10).roundToLong() / 10.0
        val settings = Store.data.settings
        if (a.auto && miles < settings.minAutoMiles) return null

        val end = if (endedByStop) a.lastMoveAt else System.currentTimeMillis()
        val places = settings.places
        val notes = buildString {
            append(if (a.auto) "Detected automatically, " else "GPS tracked, ")
            append("${a.fixes} fixes")
            if (a.gaps > 0) append(", ${a.gaps} signal gap${if (a.gaps > 1) "s" else ""}")
        }
        val trip = Trip(
            id = Store.newId(),
            date = Store.isoDate(a.startedAt),
            miles = miles,
            purpose = a.purpose,
            client = a.client,
            from = a.from.ifBlank { Geo.savedPlace(a.startLat, a.startLng, places)?.name ?: "" },
            to = Geo.savedPlace(a.lastLat, a.lastLng, places)?.name ?: "",
            notes = notes,
            source = if (a.auto) "Auto GPS" else "GPS",
            status = Status.REVIEW,
            startTime = a.startedAt,
            endTime = end,
            startLat = a.startLat, startLng = a.startLng,
            endLat = a.lastLat, endLng = a.lastLng,
        )
        Store.saveTrip(trip)
        fillAddresses(ctx.applicationContext, trip)
        if (a.auto) notifyReview(ctx, trip)
        return trip.id
    }

    /** Look up street addresses for unnamed endpoints without blocking the UI. */
    private fun fillAddresses(ctx: Context, t: Trip) {
        if (t.from.isNotBlank() && t.to.isNotBlank()) return
        scope.launch {
            val places = Store.data.settings.places
            val from = if (t.from.isBlank()) Geo.label(ctx, t.startLat, t.startLng, places) else t.from
            val to = if (t.to.isBlank()) Geo.label(ctx, t.endLat, t.endLng, places) else t.to
            Store.update { d ->
                d.copy(trips = d.trips.map { x ->
                    if (x.id != t.id) x
                    else x.copy(from = x.from.ifBlank { from }, to = x.to.ifBlank { to })
                })
            }
        }
    }

    private fun notifyReview(ctx: Context, t: Trip) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33) return
        val open = PendingIntent.getActivity(ctx, 2,
            Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_TRIP, t.id).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, MileageApp.CH_DRIVES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Drive logged: ${"%.1f".format(Locale.US, t.miles)} mi")
            .setContentText("Tap to mark it business or personal.")
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(t.id.hashCode(), n)
    }
}
