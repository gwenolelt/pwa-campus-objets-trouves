package corsica.universita.objetscampus.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class Fix(
    val lat: Double,
    val lng: Double,
    val accuracy: Float,
    val precise: Boolean
)

/**
 * Source de localisation GPS via FusedLocationProviderClient (TD A7 - Jour 5).
 * Supporte le choix Android 12+ entre localisation precise (FINE) et approximative (COARSE).
 */
class LocationSource(private val context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun currentFix(): Fix? {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fine && !coarse) return null

        val priority = if (fine) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val cts = CancellationTokenSource()
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { cts.cancel() }
            client.getCurrentLocation(priority, cts.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        cont.resume(
                            Fix(
                                lat = loc.latitude,
                                lng = loc.longitude,
                                accuracy = loc.accuracy,
                                precise = fine
                            )
                        )
                    } else {
                        // Fallback sur lastLocation si l'emulateur n'a pas encore emis de nouveau point GPS
                        client.lastLocation
                            .addOnSuccessListener { last ->
                                cont.resume(
                                    last?.let {
                                        Fix(it.latitude, it.longitude, it.accuracy, fine)
                                    }
                                )
                            }
                            .addOnFailureListener { cont.resume(null) }
                    }
                }
                .addOnFailureListener { cont.resume(null) }
        }
    }
}
