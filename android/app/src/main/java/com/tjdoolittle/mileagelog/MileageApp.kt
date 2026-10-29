package com.tjdoolittle.mileagelog

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.tjdoolittle.mileagelog.data.Store
import com.tjdoolittle.mileagelog.tracking.DriveDetector

class MileageApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_TRACKING, "Trip tracking", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows while a drive is being measured"
        })
        nm.createNotificationChannel(NotificationChannel(CH_DRIVES, "Drives to review", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Lets you know when a detected drive is ready to classify"
        })
        if (Store.data.settings.autoDetect) DriveDetector.enable(this)
    }

    companion object {
        const val CH_TRACKING = "tracking"
        const val CH_DRIVES = "drives"
    }
}
