package com.tjdoolittle.mileagelog.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    fun meters(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
        val r = 6371000.0
        val dLa = Math.toRadians(bLat - aLat); val dLo = Math.toRadians(bLng - aLng)
        val h = sin(dLa / 2).pow(2) + cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLo / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }

    fun coordLabel(lat: Double, lng: Double) = "%.4f, %.4f".format(Locale.US, lat, lng)

    fun looksLikeCoords(s: String) = Regex("""^\s*-?\d+\.\d+,\s*-?\d+\.\d+\s*$""").matches(s)

    fun savedPlace(lat: Double?, lng: Double?, places: List<Place>): Place? {
        if (lat == null || lng == null) return null
        return places.minByOrNull { meters(lat, lng, it.lat, it.lng) }?.takeIf { meters(lat, lng, it.lat, it.lng) < 200 }
    }

    /** A saved place name, else a short street address, else coordinates. */
    suspend fun label(ctx: Context, lat: Double?, lng: Double?, places: List<Place>): String {
        if (lat == null || lng == null) return ""
        savedPlace(lat, lng, places)?.let { return it.name }
        val addr = reverseGeocode(ctx, lat, lng)
        return addr ?: coordLabel(lat, lng)
    }

    private suspend fun reverseGeocode(ctx: Context, lat: Double, lng: Double): String? {
        if (!Geocoder.isPresent()) return null
        val g = Geocoder(ctx, Locale.getDefault())
        val list: List<Address>? = withTimeoutOrNull(8000) {
            if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine { cont ->
                    g.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) { if (cont.isActive) cont.resume(addresses) }
                        override fun onError(errorMessage: String?) { if (cont.isActive) cont.resume(null) }
                    })
                }
            } else {
                @Suppress("DEPRECATION")
                runCatching { g.getFromLocation(lat, lng, 1) }.getOrNull()
            }
        }
        val a = list?.firstOrNull() ?: return null
        val street = listOfNotNull(a.subThoroughfare, a.thoroughfare).joinToString(" ").ifBlank { null }
        val town = a.locality ?: a.subAdminArea
        return listOfNotNull(street, town).joinToString(", ").ifBlank { a.getAddressLine(0) }
    }
}
