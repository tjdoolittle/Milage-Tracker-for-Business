package com.tjdoolittle.mileagelog.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
        handleIntent(intent)

        setContent {
            MileageTheme {
                val data by Store.state.collectAsState()
                val live by Tracker.live.collectAsState()
                val accuracy by TrackingService.accuracy.collectAsState()
                var year by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }

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

                when (val s = screen) {
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
                    is Screen.Editor -> TripEditor(s.trip, s.isNew) { screen = Screen.Home }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
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
    }
}
