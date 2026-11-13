package com.tjdoolittle.mileagelog.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import android.graphics.Color as AndroidColor
import com.google.android.play.core.review.ReviewManagerFactory
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.tjdoolittle.mileagelog.BuildConfig
import com.tjdoolittle.mileagelog.data.DemoData
import com.tjdoolittle.mileagelog.data.Status
import com.tjdoolittle.mileagelog.data.Store
import com.tjdoolittle.mileagelog.data.Trip
import com.tjdoolittle.mileagelog.tracking.DriveDetector
import com.tjdoolittle.mileagelog.tracking.Tracker
import com.tjdoolittle.mileagelog.tracking.TrackingService
import java.util.Calendar

class MainActivity : ComponentActivity() {

    private sealed interface Screen {
        data object Home : Screen
        data object Settings : Screen
        data class Editor(val trip: Trip, val isNew: Boolean) : Screen
    }

    private var screen by mutableStateOf<Screen>(Screen.Home)
    private var resumeTick by mutableIntStateOf(0)
    private var pendingStart by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Store.init(this)
        if (BuildConfig.DEBUG && intent.getBooleanExtra("demo", false)) startDemo(intent)
        savedInstanceState?.getString(STATE_SCREEN)?.let { screen = decodeScreen(it) }
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val data by Store.state.collectAsState()
            val dark = when (data.settings.theme) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
            // Keep status and navigation bar icons readable when the app's theme differs from the phone's.
            LaunchedEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                enableEdgeToEdge(bars, bars)
            }
            // Ask the store for a rating once, after the log has some real use.
            val loggedCount = data.trips.count { it.status == Status.LOGGED }
            LaunchedEffect(loggedCount >= 10) {
                if (loggedCount >= 10 && !data.settings.reviewAsked && !Store.demo) {
                    Store.setSettings(Store.data.settings.copy(reviewAsked = true))
                    runCatching {
                        val rm = ReviewManagerFactory.create(this@MainActivity)
                        rm.requestReviewFlow().addOnCompleteListener { t ->
                            if (t.isSuccessful) rm.launchReviewFlow(this@MainActivity, t.result)
                        }
                    }
                }
            }
            MileageTheme(dark) {
                val live by Tracker.live.collectAsState()
                val accuracy by TrackingService.accuracy.collectAsState()
                var year by rememberSaveable { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }

                val askForTracking = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    if (hasLocation(this)) Tracker.startManual(this)
                    else toast(this, "Mileage Log needs location access to measure trips. You can still log trips by hand.")
                }
                fun startTrip() {
                    val need = buildList {
                        if (!hasLocation(this@MainActivity)) { add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION) }
                        if (Build.VERSION.SDK_INT >= 33 && !hasNotifications(this@MainActivity)) add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (need.isEmpty()) Tracker.startManual(this) else askForTracking.launch(need.toTypedArray())
                }
                LaunchedEffect(pendingStart) { if (pendingStart) { pendingStart = false; startTrip() } }

                BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

                if (!data.settings.introDismissed) Walkthrough {
                    Store.setSettings(Store.data.settings.copy(introDismissed = true, seenNews = Store.WHATS_NEW_ID))
                } else when (val s = screen) {
                    Screen.Home -> HomeScreen(
                        data = data, live = live, gpsAccuracy = accuracy, year = year,
                        onYear = { year = it },
                        onOpenTrip = { screen = Screen.Editor(it, isNew = false) },
                        onPersonal = { Store.saveTrip(it.copy(status = Status.PERSONAL)); toast(this, "Marked personal. It won't count toward your deduction.") },
                        onStart = { startTrip() },
                        onFinish = {
                            val id = Tracker.finish(this)
                            val t = id?.let { tid -> Store.data.trips.firstOrNull { it.id == tid } }
                            if (t != null) screen = Screen.Editor(t, isNew = false)
                        },
                        onDiscard = { Tracker.discard(this) },
                        onLog = { screen = Screen.Editor(Trip(id = Store.newId(), date = Store.today(), miles = 0.0), isNew = true) },
                        onSettings = { screen = Screen.Settings },
                    )
                    Screen.Settings -> SettingsScreen(data, year, resumeTick) { screen = Screen.Home }
                    // Keyed by trip, so opening a different trip (say, from a notification) starts a fresh form.
                    is Screen.Editor -> key(s.trip.id) { TripEditor(s.trip, s.isNew) { screen = Screen.Home } }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SCREEN, encodeScreen(screen))
    }

    private fun encodeScreen(s: Screen): String = when (s) {
        Screen.Home -> "home"
        Screen.Settings -> "settings"
        is Screen.Editor -> "editor|${s.trip.id}|${s.isNew}|${s.trip.date}"
    }

    private fun decodeScreen(v: String): Screen {
        val p = v.split("|")
        return when (p[0]) {
            "settings" -> Screen.Settings
            "editor" -> {
                val id = p.getOrNull(1) ?: return Screen.Home
                val isNew = p.getOrNull(2) == "true"
                // A logged trip comes back from storage; a new one is rebuilt and the editor restores what was typed.
                val trip = Store.data.trips.firstOrNull { it.id == id }
                    ?: if (isNew) Trip(id = id, date = p.getOrNull(3) ?: Store.today(), miles = 0.0) else return Screen.Home
                Screen.Editor(trip, isNew)
            }
            else -> Screen.Home
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Debug-only screenshot mode: am start -n ... --ez demo true [--ez active true] [--es screen settings|editor] */
    private fun startDemo(i: Intent) {
        val active = i.getBooleanExtra("active", false)
        Store.loadDemo(DemoData.make(active))
        if (active) { Tracker.live.value = Store.data.active; TrackingService.demoAccuracy(5.0) }
        // Screenshot job extras: show the walkthrough, a theme, or the What's new card.
        if (i.getBooleanExtra("walkthrough", false)) Store.setSettings(Store.data.settings.copy(introDismissed = false))
        if (i.getBooleanExtra("news", false)) Store.setSettings(Store.data.settings.copy(seenNews = ""))
        i.getStringExtra("theme")?.let { Store.setSettings(Store.data.settings.copy(theme = it)) }
        // Screenshot job: write a sample tax report the script can pull off the emulator.
        if (i.getBooleanExtra("pdf", false)) {
            runCatching {
                java.io.File(getExternalFilesDir(null), "sample-report.pdf").outputStream().use {
                    com.tjdoolittle.mileagelog.data.ReportPdf.write(it, java.util.Calendar.getInstance().get(java.util.Calendar.YEAR))
                }
            }.onFailure { android.util.Log.e("MileageLog", "sample report failed", it) }
        }
        screen = when (i.getStringExtra("screen")) {
            "settings" -> Screen.Settings
            "editor" -> Store.data.trips.firstOrNull { it.status == Status.REVIEW }?.let { Screen.Editor(it, isNew = false) } ?: Screen.Home
            else -> Screen.Home
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
        if (Store.demo) return
        // A trip survives the app or phone restarting: pick tracking back up.
        val active = Store.data.active
        if (active != null && !TrackingService.running.value && hasLocation(this)) {
            if (Tracker.live.value == null) Tracker.live.value = active
            runCatching { TrackingService.resume(this) }
        }
        if (Store.data.settings.autoDetect) DriveDetector.enable(this)
    }

    private fun handleIntent(i: Intent?) {
        if (i == null) return
        i.getStringExtra(EXTRA_TRIP)?.let { id ->
            Store.data.trips.firstOrNull { it.id == id }?.let { screen = Screen.Editor(it, isNew = false) }
            i.removeExtra(EXTRA_TRIP)
        }
        if (i.getBooleanExtra(EXTRA_START, false)) {
            pendingStart = true
            i.removeExtra(EXTRA_START)
        }
    }

    companion object {
        const val EXTRA_TRIP = "trip_id"
        const val EXTRA_START = "start_trip"
        private const val STATE_SCREEN = "screen"
    }
}
