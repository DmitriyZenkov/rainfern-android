package dev.rdime.rainfern.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dev.rdime.rainfern.data.model.Coordinates
import kotlinx.coroutines.tasks.await

class DeviceLocationRepository(
    private val context: Context,
) {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    suspend fun getCurrentLocation(): Coordinates? {
        if (!hasForegroundPermission()) {
            return null
        }
        val precise = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
        val result = precise ?: client.lastLocation.await()
        return result?.let { Coordinates(it.latitude, it.longitude) }
    }

    fun hasForegroundPermission(): Boolean =
        hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasBackgroundPermission(): Boolean =
        hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
