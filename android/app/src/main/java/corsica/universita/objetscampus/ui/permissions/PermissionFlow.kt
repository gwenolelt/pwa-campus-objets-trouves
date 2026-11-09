package corsica.universita.objetscampus.ui.permissions

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Machine a 4 etats des permissions runtime Android (TD A7 - Jour 5) :
 * - NotAsked      : jamais demandee -> lancer le popup systeme au clic utilisateur
 * - Granted       : accordee -> executer l'action
 * - DeniedOnce    : refusee 1 fois (shouldShowRequestPermissionRationale == true) -> afficher un dialogue explicatif
 * - DeniedForever : refusee 2 fois ("Ne plus demander") -> proposer un bouton vers les Parametres systeme
 */
sealed interface PermissionStatus {
    data object NotAsked : PermissionStatus
    data object Granted : PermissionStatus
    data object DeniedOnce : PermissionStatus
    data object DeniedForever : PermissionStatus
}

class PermissionState(
    val status: PermissionStatus,
    val request: () -> Unit,
    val openAppSettings: () -> Unit
)

@Composable
fun rememberPermissionFlow(
    permissions: List<String>,
    rationale: String,
    requireAll: Boolean = true,
    onResult: (granted: Boolean) -> Unit = {}
): PermissionState {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var askedBefore by rememberSaveable(permissions.joinToString()) { mutableStateOf(false) }
    var showRationale by rememberSaveable(permissions.joinToString()) { mutableStateOf(false) }

    fun computeStatus(): PermissionStatus {
        if (permissions.isEmpty()) return PermissionStatus.Granted
        val checks = permissions.map {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        val granted = if (requireAll) checks.all { it } else checks.any { it }
        if (granted) return PermissionStatus.Granted

        val shouldExplain = activity != null && permissions.any {
            ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
        }
        return when {
            shouldExplain -> PermissionStatus.DeniedOnce
            askedBefore -> PermissionStatus.DeniedForever
            else -> PermissionStatus.NotAsked
        }
    }

    var status by remember { mutableStateOf(computeStatus()) }

    val launcher = rememberLauncherForActivityResult(RequestMultiplePermissions()) { results ->
        askedBefore = true
        status = computeStatus()
        val granted = if (requireAll) {
            results.values.all { it }
        } else {
            results.values.any { it }
        }
        onResult(granted)
    }

    // Rafraichir automatiquement au retour des Parametres systeme (ON_RESUME)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, permissions) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                status = computeStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("Autorisation requise") },
            text = { Text(rationale) },
            confirmButton = {
                Button(
                    onClick = {
                        showRationale = false
                        launcher.launch(permissions.toTypedArray())
                    }
                ) {
                    Text("Continuer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) {
                    Text("Pas maintenant")
                }
            }
        )
    }

    val openSettings = {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    val trigger = {
        val current = computeStatus()
        status = current
        when (current) {
            PermissionStatus.Granted -> onResult(true)
            PermissionStatus.NotAsked -> launcher.launch(permissions.toTypedArray())
            PermissionStatus.DeniedOnce -> showRationale = true
            PermissionStatus.DeniedForever -> openSettings()
        }
    }

    return PermissionState(
        status = status,
        request = trigger,
        openAppSettings = openSettings
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
