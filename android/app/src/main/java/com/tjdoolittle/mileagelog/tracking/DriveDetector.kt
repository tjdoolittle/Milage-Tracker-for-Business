package com.tjdoolittle.mileagelog.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.tjdoolittle.mileagelog.MileageApp
import com.tjdoolittle.mileagelog.R
import com.tjdoolittle.mileagelog.data.Store
import com.tjdoolittle.mileagelog.ui.MainActivity

/** Uses Google's activity recognition to notice when you get in and out of a vehicle. */
object DriveDetector {
    private const val TAG = "DriveDetector"

    private fun pendingIntent(ctx: Context): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(ctx, 7, Intent(ctx, ActivityReceiver::class.java), flags)
    }

    fun hasPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun enable(ctx: Context) {
        if (!hasPermission(ctx)) return
        val transitions = listOf(
            ActivityTransition.Builder().setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build(),
            ActivityTransition.Builder().setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT).build(),
        )
        ActivityRecognition.getClient(ctx)
            .requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), pendingIntent(ctx))
            .addOnFailureListener { Log.w(TAG, "Could not register for drive detection", it) }
    }

    @SuppressLint("MissingPermission")
    fun disable(ctx: Context) {
        if (!hasPermission(ctx)) return
        ActivityRecognition.getClient(ctx).removeActivityTransitionUpdates(pendingIntent(ctx))
    }
}

class ActivityReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Store.init(ctx)
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        if (!Store.data.settings.autoDetect) return
        for (e in result.transitionEvents) {
            if (e.activityType != DetectedActivity.IN_VEHICLE) continue
            if (e.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) startAutoTrip(ctx)
            else if (Tracker.current()?.auto == true) TrackingService.send(ctx, TrackingService.ACTION_VEHICLE_EXIT)
        }
    }

    private fun startAutoTrip(ctx: Context) {
        if (Tracker.current() != null) return
        val ok = runCatching {
            ContextCompat.startForegroundService(ctx, Intent(ctx, TrackingService::class.java).setAction(TrackingService.ACTION_START_AUTO))
        }.isSuccess
        if (!ok) promptToTrack(ctx)
    }

    /** Android sometimes blocks starting GPS from the background; ask the driver with one tap instead. */
    private fun promptToTrack(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(ctx, 3,
            Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_START, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, MileageApp.CH_DRIVES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Driving? Tap to track this trip")
            .setContentText("Battery saver stopped automatic tracking. Turn it off for Mileage Log in Settings.")
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(99, n)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Store.init(ctx)
        if (Store.data.settings.autoDetect) DriveDetector.enable(ctx)
    }
}
