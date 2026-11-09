package corsica.universita.objetscampus.ui.items

import android.Manifest
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import corsica.universita.objetscampus.data.local.ItemEntity
import corsica.universita.objetscampus.ui.permissions.PermissionStatus
import corsica.universita.objetscampus.ui.permissions.rememberPermissionFlow
import java.io.File

/**
 * Ecran de declaration d'un objet trouve (TD A3, A5, A7).
 * - Tous les champs sont portes par `SavedStateHandle` dans `ItemsViewModel` :
 *   le brouillon survit a la rotation d'ecran ET a la mort du processus (`adb shell am kill`).
 * - Integre les permissions runtime (CameraX, Localisation Fine/Coarse) via `rememberPermissionFlow`.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeclareItemScreen(
    title: String,
    category: String,
    description: String,
    dateFound: String,
    locationFound: String,
    depositLocation: String,
    photoPath: String?,
    barcodeValue: String?,
    lat: Double?,
    lng: Double?,
    accuracy: Float?,
    onTitleChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDateFoundChange: (String) -> Unit,
    onLocationFoundChange: (String) -> Unit,
    onDepositLocationChange: (String) -> Unit,
    onImportPhotoUri: (Uri) -> Unit,
    onCaptureLocation: () -> Unit,
    onOpenCameraScanner: () -> Unit,
    onCameraDenied: () -> Unit,
    onClearDraft: () -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit
) {
    var categoryExpanded by remember { mutableStateOf(false) }
    var depositExpanded by remember { mutableStateOf(false) }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportPhotoUri(uri)
        }
    }

    val cameraPermission = rememberPermissionFlow(
        permissions = listOf(Manifest.permission.CAMERA),
        rationale = "L'accès à la caméra permet de photographier l'objet trouvé et de scanner un QR code ou code-barres campus."
    ) { granted ->
        if (granted) onOpenCameraScanner() else onCameraDenied()
    }

    val locationPermission = rememberPermissionFlow(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ),
        rationale = "La localisation permet d'indiquer automatiquement le bâtiment ou l'emplacement où l'objet a été trouvé sur le campus.",
        requireAll = false // Accepte la localisation approximative (Android 12+)
    ) { granted ->
        if (granted) onCaptureLocation()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Déclarer un objet trouvé", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    TextButton(onClick = onClearDraft) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Vider")
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
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "💡 Ce brouillon est sauvegardé automatiquement dans SavedStateHandle : il survit à la rotation de l'écran et même si Android tue l'application en arrière-plan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }

            // Boutons Capteurs & Natif (Scanner QR / Photo CameraX / Position GPS)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(onClick = { cameraPermission.request() }) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        when (cameraPermission.status) {
                            PermissionStatus.DeniedForever -> "Caméra (Paramètres)"
                            else -> "Scanner QR / Photo CameraX"
                        }
                    )
                }

                FilledTonalButton(onClick = { locationPermission.request() }) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        when (locationPermission.status) {
                            PermissionStatus.DeniedForever -> "GPS (Paramètres)"
                            else -> "Position GPS Campus"
                        }
                    )
                }

                OutlinedButton(onClick = { galleryPicker.launch("image/*") }) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Galerie")
                }
            }

            if (!barcodeValue.isNullOrBlank() || (lat != null && lng != null)) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (!barcodeValue.isNullOrBlank()) {
                            Text(
                                text = "🏷️ Code-barres / QR associé : $barcodeValue",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (lat != null && lng != null) {
                            Text(
                                text = "📍 Coordonnées GPS : ${"%.5f".format(lat)}, ${"%.5f".format(lng)} (précision ±${accuracy?.toInt() ?: 10}m)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            if (!photoPath.isNullOrBlank() && File(photoPath).exists()) {
                AsyncImage(
                    model = File(photoPath),
                    contentDescription = "Aperçu de la photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }

            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                label = { Text("Objet trouvé *") },
                placeholder = { Text("Ex : Trousseau de clés avec badge bleu") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Menu deroulant Categorie
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = !categoryExpanded }
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Catégorie *") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    ItemEntity.CATEGORIES.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                onCategoryChange(option)
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = dateFound,
                    onValueChange = onDateFoundChange,
                    label = { Text("Date découverte *") },
                    placeholder = { Text("AAAA-MM-JJ") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                OutlinedTextField(
                    value = locationFound,
                    onValueChange = onLocationFoundChange,
                    label = { Text("Lieu découverte *") },
                    placeholder = { Text("Ex : Amphi 2, BU...") },
                    modifier = Modifier.weight(1.4f),
                    singleLine = true
                )
            }

            // Menu deroulant Lieu de depot
            ExposedDropdownMenuBox(
                expanded = depositExpanded,
                onExpandedChange = { depositExpanded = !depositExpanded }
            ) {
                OutlinedTextField(
                    value = depositLocation,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Lieu de dépôt actuel *") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = depositExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = depositExpanded,
                    onDismissRequest = { depositExpanded = false }
                ) {
                    ItemEntity.DEPOSIT_LOCATIONS.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                onDepositLocationChange(option)
                                depositExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = onDescriptionChange,
                label = { Text("Description / signes distinctifs") },
                placeholder = { Text("Couleur, marque, pochette, autocollant...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Button(
                onClick = onSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Publier l'objet trouvé", fontWeight = FontWeight.Bold)
            }
        }
    }
}
