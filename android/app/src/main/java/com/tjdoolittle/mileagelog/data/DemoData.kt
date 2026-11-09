package com.tjdoolittle.mileagelog.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random

/**
 * Made-up sample trips for store screenshots. Only reachable in debug builds
 * (launch with the "demo" extra); nothing is saved.
 */
object DemoData {
    fun make(active: Boolean): AppData {
        val places = listOf(
            Place("Shop", 33.7900, -84.3880),
            Place("Hartwell residence", 33.8480, -84.3730),
            Place("Peachtree Dental", 33.8030, -84.3880),
            Place("Morrow office park", 33.5830, -84.3390),
            Place("Alpharetta model home", 34.0750, -84.2940),
            Place("AV supply house", 33.7550, -84.4200),
        )
        data class Job(val purpose: String, val client: String, val place: Int, val miles: Double)
        val jobs = listOf(
            Job("Job Site Visits", "Hartwell residence", 1, 12.6),
            Job("Client Visits", "Peachtree Dental", 2, 4.8),
            Job("Sales Calls", "Morrow office park", 3, 21.3),
            Job("Job Site Visits", "Alpharetta model home", 4, 26.9),
            Job("Admin Errands", "", 5, 6.2),
        )
        val rng = Random(7)
        val today = LocalDate.now()
        val trips = mutableListOf<Trip>()
        var day = LocalDate.of(today.year, 1, 6)
        var n = 0
        while (day.isBefore(today)) {
            if (day.dayOfWeek != DayOfWeek.SATURDAY && day.dayOfWeek != DayOfWeek.SUNDAY && rng.nextDouble() < 0.42) {
                val j = jobs[rng.nextInt(jobs.size)]
                val round = rng.nextDouble() < 0.4
                trips += Trip(
                    id = "demo-$n", date = day.toString(), miles = if (round) j.miles * 2 else j.miles,
                    purpose = j.purpose, client = j.client, from = "Shop", to = places[j.place].name,
                    roundTrip = round, source = if (rng.nextDouble() < 0.5) "Auto GPS" else null, createdAt = n.toLong(),
                    parking = if (rng.nextDouble() < 0.2) listOf(6.0, 12.5, 3.75, 8.0)[rng.nextInt(4)] else 0.0,
                    vehicleId = if (rng.nextDouble() < 0.8) "v-van" else "v-car",
                )
                n++
            }
            day = day.plusDays(1)
        }
        fun todayAt(h: Int, m: Int): Long =
            LocalDateTime.of(today.year, today.month, today.dayOfMonth, h, m).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        trips += Trip(
            id = "demo-r1", date = today.toString(), miles = 18.4, from = "Shop", to = "1180 Peachtree St NE, Atlanta",
            source = "Auto GPS", status = Status.REVIEW, startTime = todayAt(7, 52), createdAt = now,
        )
        trips += Trip(
            id = "demo-r2", date = today.toString(), miles = 3.1, from = "1180 Peachtree St NE, Atlanta", to = "Home",
            source = "Auto GPS", status = Status.REVIEW, startTime = todayAt(8, 31), createdAt = now + 1,
        )
        val activeTrip = if (active) ActiveTrip(
            startedAt = todayAt(9, 15), auto = true, meters = 12.3 * 1609.344, fixes = 540,
            startLat = 33.79, startLng = -84.388, lastMoveAt = now,
        ) else null
        return AppData(
            trips = trips,
            settings = Settings(
                places = places, autoDetect = true, introDismissed = true, reportName = "Hartley AV Installs",
                vehicles = listOf(
                    Vehicle("v-van", "2022 Ford Transit", "2022-03-14", mapOf(today.year to YearOdo(41_250.0, null))),
                    Vehicle("v-car", "2019 Honda Accord", "2019-08-02", mapOf(today.year to YearOdo(68_910.0, 79_420.0))),
                ),
            ),
            active = activeTrip,
        )
    }
}
