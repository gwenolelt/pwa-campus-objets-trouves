package corsica.universita.objetscampus.ui.items

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import corsica.universita.objetscampus.BuildConfig
import corsica.universita.objetscampus.data.ConflictState
import corsica.universita.objetscampus.data.local.ItemEntity
import corsica.universita.objetscampus.data.local.PhotoStore
import corsica.universita.objetscampus.ui.permissions.rememberPermissionFlow
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ItemDetailScreen(
    item: ItemEntity?,
    conflictState: ConflictState?,
    photoStore: PhotoStore,
    onAttachLocation: () -> Unit,
    onOpenCameraScanner: () -> Unit,
    onCameraDenied: () -> Unit,
    onAttachPhotoUri: (Uri) -> Unit,
    onMarkReturned: (simulateConflict: Boolean) -> Unit,
    onResolveConflict: (forceLocal: Boolean) -> Unit,
    onDismissConflict: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current

    if (conflictState != null) {
        ConflictResolutionDialog(
            conflict = conflictState,
            onForceLocal = { onResolveConflict(true) },
            onAcceptServer = { onResolveConflict(false) },
            onDismiss = onDismissConflict
        )
    }

    if (item == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Fiche de l'objet") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Objet introuvable ou supprimé.")
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onBack) { Text("Retour à la liste") }
            }
        }
        return
    }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) onAttachPhotoUri(uri)
    }

    val cameraPermission = rememberPermissionFlow(
        permissions = listOf(Manifest.permission.CAMERA),
        rationale = "Autorisez la caméra pour associer une photo ou scanner un code-barres/QR sur cet objet."
    ) { granted ->
        if (granted) onOpenCameraScanner() else onCameraDenied()
    }

    val locationPermission = rememberPermissionFlow(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ),
        rationale = "Autorisez la localisation pour enregistrer les coordonnées GPS exactes du lieu de découverte.",
        requireAll = false
    ) { granted ->
        if (granted) onAttachLocation()
    }

    val publicUrl = "${BuildConfig.PWA_URL}?item=${item.remoteId.ifBlank { item.id.toString() }}"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Détail de l'objet", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = { shareItemOut(context, item, photoStore) }) {
                        Icon(Icons.Default.Share, contentDescription = "Partager")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Supprimer")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Photo de l'objet si disponible
            if (!item.photoPath.isNullOrBlank() && File(item.photoPath).exists()) {
                AsyncImage(
                    model = File(item.photoPath),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            }

            // Badges d'etat (Statut, Categorie, Version / Sync)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = if (item.isReturned) Color(0xFFD1FAE5) else Color(0xFFDBEAFE),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = if (item.isReturned) {
                            "Restitué ✓ ${item.dateReturned?.let { "($it)" } ?: ""}"
                        } else {
                            "À récupérer"
                        },
                        color = if (item.isReturned) Color(0xFF065F46) else Color(0xFF1E40AF),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = item.category,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Surface(
                    color = if (item.synced) Color(0xFFE0F2FE) else Color(0xFFFEF3C7),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = if (item.synced) "v${item.version} · Synchronisé" else "⏳ En attente de sync",
                        color = if (item.synced) Color(0xFF0369A1) else Color(0xFF92400E),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Text(
                text = item.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )

            if (item.description.isNotBlank()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Fiche d'informations Campus
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DetailRow(label = "📅 Date de découverte", value = item.dateFound)
                    HorizontalDivider()
                    DetailRow(label = "📍 Lieu de découverte", value = item.locationFound)
                    HorizontalDivider()
                    DetailRow(label = "🏛️ Lieu de dépôt actuel", value = item.depositLocation, highlight = true)

                    if (!item.barcodeValue.isNullOrBlank()) {
                        HorizontalDivider()
                        DetailRow(label = "🏷️ Code QR / Code-barres", value = item.barcodeValue)
                    }

                    if (item.lat != null && item.lng != null) {
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🛰️ Coordonnées GPS",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${"%.5f".format(item.lat)}, ${"%.5f".format(item.lng)} (à ±${item.accuracy?.toInt() ?: 10}m près)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            TextButton(
                                onClick = {
                                    val geoUri = Uri.parse("geo:${item.lat},${item.lng}?q=${item.lat},${item.lng}(${Uri.encode(item.title)})")
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, geoUri))
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Carte")
                            }
                        }
                    }
                }
            }

            // Enrichissement Capteurs & CameraX (TD A7)
            Text(
                text = "Enrichir la fiche (Capteurs & Caméra native)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(onClick = { locationPermission.request() }) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Position GPS")
                }

                FilledTonalButton(onClick = { cameraPermission.request() }) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("CameraX / Scanner QR")
                }

                OutlinedButton(onClick = { galleryPicker.launch("image/*") }) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Galerie photo")
                }
            }

            // Lien public App Link + Partage systeme
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "🔗 Lien public App Link (ouvre directement l'application Android)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = publicUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("url", publicUrl))
                                onShowMessage("Lien copié dans le presse-papiers !")
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copier le lien")
                        }

                        Button(onClick = { shareItemOut(context, item, photoStore) }) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Partager la fiche")
                        }
                    }
                }
            }

            // Actions de restitution et simulation de conflit HTTP 412 (comme dans la PWA)
            if (!item.isReturned) {
                Button(
                    onClick = { onMarkReturned(false) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Marquer comme restitué au propriétaire", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { onMarkReturned(true) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tester la réconciliation de conflit (HTTP 412 / If-Match)")
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ConflictResolutionDialog(
    conflict: ConflictState,
    onForceLocal: () -> Unit,
    onAcceptServer: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⚠️ Conflit de modification détecté (HTTP 412)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Cet objet a été modifié en parallèle sur un autre terminal. Quelle version souhaitez-vous conserver ?",
                    style = MaterialTheme.typography.bodySmall
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFDBEAFE)
                    )
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Votre modification locale (v${conflict.localItem.version})",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "Statut : Restitué ✓ (${conflict.localItem.dateReturned ?: "aujourd'hui"})",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFEF3C7)
                    )
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Version serveur (v${conflict.serverItem?.version ?: (conflict.localItem.version + 1)})",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = conflict.serverItem?.description ?: "Version concurrente sur le serveur",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onForceLocal) {
                Text("Forcer ma modification")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onAcceptServer) {
                Text("Accepter le serveur")
            }
        }
    )
}
