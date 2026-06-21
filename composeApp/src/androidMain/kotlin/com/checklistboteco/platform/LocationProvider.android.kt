package com.checklistboteco.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.checklistboteco.domain.model.GeoPoint
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

actual object LocationProvider {
    private var appContext: Context? = null
    private var callback: LocationCallback? = null
    private var onUpdate: ((LocationUpdate) -> Unit)? = null

    actual fun initialize(platformContext: Any?) {
        appContext = platformContext as? Context ?: appContext
    }

    actual fun hasPermission(): Boolean {
        val context = appContext ?: return false
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    actual fun startUpdates(onUpdate: (LocationUpdate) -> Unit) {
        val context = appContext ?: run {
            onUpdate(LocationUpdate(null, null))
            return
        }
        this.onUpdate = onUpdate
        if (!hasPermission()) {
            onUpdate(LocationUpdate(null, null))
            return
        }
        stopUpdates()
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
            .setMinUpdateIntervalMillis(1_000L)
            .setMaxUpdates(Int.MAX_VALUE)
            .build()
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation
                if (location == null) {
                    onUpdate(LocationUpdate(null, null))
                } else {
                    onUpdate(
                        LocationUpdate(
                            point = GeoPoint(location.latitude, location.longitude),
                            accuracyMeters = location.accuracy
                        )
                    )
                }
            }
        }
        callback = locationCallback
        client.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    actual fun stopUpdates() {
        val context = appContext ?: return
        val activeCallback = callback ?: return
        LocationServices.getFusedLocationProviderClient(context).removeLocationUpdates(activeCallback)
        callback = null
        onUpdate = null
    }
}
