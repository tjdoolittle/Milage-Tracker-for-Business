package com.tjdoolittle.mileagelog.tracking

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.tjdoolittle.mileagelog.MileageApp
import com.tjdoolittle.mileagelog.R
import com.tjdoolittle.mileagelog.data.ActiveTrip
import com.tjdoolittle.mileagelog.data.Geo
import com.tjdoolittle.mileagelog.data.Store
import com.tjdoolittle.mileagelog.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * Foreground service that measures a drive with GPS. It keeps running with the
 * screen off. Distance is the sum of hops between accurate fixes, ignoring GPS
 * drift while parked and impossible jumps.
 */
class TrackingService : Service() {

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var exitSeenAt = 0L
    private var lastNotify = 0L
    private var lastSave = 0L

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { onFix(it.latitude, it.longitude, it.accuracy.toDouble(), it.time) }
            checkAutoStop()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Store.init(this)
        when (intent?.action) {
            ACTION_FINISH -> { Tracker.finish(this); return START_NOT_STICKY }
            ACTION_DISCARD -> { Tracker.discard(this); return START_NOT_STICKY }
            ACTION_VEHICLE_EXIT -> { exitSeenAt = System.currentTimeMillis(); if (Tracker.current() == null) { stopSelf(); return START_NOT_STICKY } }
            ACTION_START_AUTO -> if (Tracker.current() == null) Tracker.update(ActiveTrip(startedAt = System.currentTimeMillis(), auto = true), persist = true)
            else -> if (Tracker.current() == null) Tracker.update(ActiveTrip(startedAt = System.currentTimeMillis(), auto = false), persist = true)
        }
        val cur = Tracker.current()
        if (cur == null) { stopSelf(); return START_NOT_STICKY }
        if (Tracker.live.value == null) Tracker.live.value = cur
        goForeground()
        startUpdates()
        return START_STICKY
    }

    private fun goForeground() {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
        _running.value = true
    }

    @SuppressLint("MissingPermission")
    private fun startUpdates() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
            .setMinUpdateIntervalMillis(2000L)
            .setWaitForAccurateLocation(false)
            .build()
        runCatching { fused.removeLocationUpdates(callback) }
        runCatching { fused.requestLocationUpdates(req, callback, Looper.getMainLooper()) }
            .onFailure { stopSelf() }
    }

    private fun onFix(lat: Double, lng: Double, acc: Double, t: Long) {
        val a = Tracker.current() ?: return
        _accuracy.value = acc
        if (acc > 50) { maybeNotify(); return }
        var n = a.copy(fixes = a.fixes + 1, lastFixAt = t)
        if (a.startLat == null) n = n.copy(startLat = lat, startLng = lng, lastMoveAt = t)
        val pLat = a.lastLat; val pLng = a.lastLng
        if (pLat == null || pLng == null) {
            n = n.copy(lastLat = lat, lastLng = lng)
        } else {
            val d = Geo.meters(pLat, pLng, lat, lng)
            val dt = (t - a.lastFixAt) / 1000.0
            when {
                d < maxOf(10.0, acc * 0.6) -> { /* parked or drifting: keep the old anchor */ }
                dt > 0 && d / dt > 70 -> { /* faster than 155 mph: a bad fix */ }
                else -> n = n.copy(
                    meters = a.meters + d, lastLat = lat, lastLng = lng, lastMoveAt = t,
                    gaps = a.gaps + if (dt > 90) 1 else 0,
                )
            }
        }
        val now = System.currentTimeMillis()
        val persist = now - lastSave > 15_000
        if (persist) lastSave = now
        Tracker.update(n, persist)
        maybeNotify()
    }

    /** Auto trips end on their own: after leaving the vehicle, or after sitting still. */
    private fun checkAutoStop() {
        val a = Tracker.current() ?: return
        if (!a.auto) return
        val now = System.currentTimeMillis()
        val stillFor = now - a.lastMoveAt
        val stop = (exitSeenAt > 0 && stillFor > 60_000) || stillFor > 5 * 60_000
        if (stop) Tracker.finish(this, endedByStop = true)
    }

    private fun maybeNotify() {
        val now = System.currentTimeMillis()
        if (now - lastNotify < 5000) return
        lastNotify = now
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val a = Tracker.current()
        val miles = a?.miles ?: 0.0
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val finish = PendingIntent.getService(this, 1, Intent(this, TrackingService::class.java).setAction(ACTION_FINISH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val title = if (a?.auto == true) "Drive detected" else "Tracking trip"
        return NotificationCompat.Builder(this, MileageApp.CH_TRACKING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$title · ${"%.1f".format(Locale.US, miles)} mi")
            .setContentText(if ((_accuracy.value ?: 0.0) > 50) "Waiting for a clear GPS signal" else "Measuring with GPS. Tap Finish when you arrive.")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setContentIntent(open)
            .addAction(0, "Finish", finish)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onDestroy() {
        runCatching { fused.removeLocationUpdates(callback) }
        _running.value = false
        _accuracy.value = null
        super.onDestroy()
    }

    companion object {
        const val NOTIF_ID = 42
        const val ACTION_START_MANUAL = "start_manual"
        const val ACTION_START_AUTO = "start_auto"
        const val ACTION_FINISH = "finish"
        const val ACTION_DISCARD = "discard"
        const val ACTION_VEHICLE_EXIT = "vehicle_exit"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running
        private val _accuracy = MutableStateFlow<Double?>(null)
        val accuracy: StateFlow<Double?> = _accuracy

        /** Start from the app (foreground). */
        fun startManual(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, TrackingService::class.java).setAction(ACTION_START_MANUAL))
        }

        /** Resume a trip after the app or phone restarted. */
        fun resume(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, TrackingService::class.java).setAction(ACTION_START_MANUAL))
        }

        fun send(ctx: Context, action: String) {
            runCatching { ctx.startService(Intent(ctx, TrackingService::class.java).setAction(action)) }
        }
    }
}
