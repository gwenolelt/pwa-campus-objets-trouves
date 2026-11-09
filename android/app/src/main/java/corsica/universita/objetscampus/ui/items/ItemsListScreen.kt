package corsica.universita.objetscampus.ui.items

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import corsica.universita.objetscampus.BuildConfig
import corsica.universita.objetscampus.data.local.ItemEntity
import corsica.universita.objetscampus.data.local.PhotoStore
import corsica.universita.objetscampus.ui.permissions.PermissionStatus
import corsica.universita.objetscampus.ui.permissions.rememberPermissionFlow
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ItemsListScreen(
    items: List<ItemEntity>,
    activeCount: Int,
    returnedCount: Int,
    unsyncedCount: Int,
    isOnline: Boolean,
    isSyncing: Boolean,
    searchQuery: String,
    statusFilter: StatusFilter,
    categoryFilter: String,
    fcmToken: String?,
    analyticsConsent: Boolean?,
    photoStore: PhotoStore,
    onSearchQueryChange: (String) -> Unit,
    onStatusFilterChange: (StatusFilter) -> Unit,
    onCategoryFilterChange: (String) -> Unit,
    onItemClick: (Long) -> Unit,
    onTogglePin: (ItemEntity) -> Unit,
    onDeclareClick: () -> Unit,
    onScanBarcodeClick: () -> Unit,
    onManualSync: () -> Unit,
    onSetAnalyticsConsent: (Boolean) -> Unit,
    onSendTestNotification: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var showDiagnosticsDialog by rememberSaveable { mutableStateOf(false) }

    val notifPermission = rememberPermissionFlow(
        permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyList()
        },
        rationale = "Autorisez les notifications pour être alerté lorsqu'un objet trouvé correspond au vôtre ou lorsque vos déclarations hors-ligne sont synchronisées."
    ) { granted ->
        if (granted) {
            onSendTestNotification()
        } else {
            onShowMessage("Notifications refusées : l'application continue de fonctionner normalement.")
        }
    }

    if (showDiagnosticsDialog) {
        DiagnosticsAndFcmDialog(
            fcmToken = fcmToken,
            analyticsConsent = analyticsConsent == true,
            notifStatus = notifPermission.status,
            onToggleAnalytics = onSetAnalyticsConsent,
            onTriggerTestNotification = {
                notifPermission.request()
            },
            onOpenSystemSettings = notifPermission.openAppSettings,
            onCopyToken = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("fcm_token", fcmToken ?: "Non disponible"))
                onShowMessage("Token FCM copié dans le presse-papiers")
            },
            onDismiss = { showDiagnosticsDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🔍 Objets Trouvés",
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        NetworkStatusPill(isOnline = isOnline)
                    }
                },
                actions = {
                    if (unsyncedCount > 0) {
                        Surface(
                            color = Color(0xFFFEF3C7),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .clickable { onManualSync() }
                        ) {
                            Text(
                                text = "⏳ $unsyncedCount",
                                color = Color(0xFF92400E),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    IconButton(onClick = onManualSync, enabled = !isSyncing) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Synchroniser avec le serveur"
                            )
                        }
                    }

                    IconButton(onClick = { showDiagnosticsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications FCM & RGPD"
                        )
                    }

                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PWA_URL))
                            runCatching { context.startActivity(intent) }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = "Ouvrir la PWA dans le navigateur"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onDeclareClick,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Déclarer un objet") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Banniere RGPD Analytics (Annexe 4) si l'utilisateur n'a pas encore fait son choix
            if (analyticsConsent == null) {
                item(key = "rgpd_banner") {
                    RgpdConsentBanner(
                        onAccept = { onSetAnalyticsConsent(true) },
                        onDecline = { onSetAnalyticsConsent(false) }
                    )
                }
            }

            // Hero Banner identique a la PWA + bouton Scanner QR Natif
            item(key = "hero_banner") {
                CampusHeroCard(
                    activeCount = activeCount,
                    returnedCount = returnedCount,
                    unsyncedCount = unsyncedCount,
                    onDeclareClick = onDeclareClick,
                    onScanBarcodeClick = onScanBarcodeClick
                )
            }

            // Barre de recherche et filtres (identiques a la PWA)
            item(key = "filters_bar") {
                SearchAndFiltersSection(
                    searchQuery = searchQuery,
                    statusFilter = statusFilter,
                    categoryFilter = categoryFilter,
                    onSearchQueryChange = onSearchQueryChange,
                    onStatusFilterChange = onStatusFilterChange,
                    onCategoryFilterChange = onCategoryFilterChange
                )
            }

            if (items.isEmpty()) {
                item(key = "empty_state") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("📭", style = MaterialTheme.typography.displaySmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Aucun objet trouvé ne correspond à vos critères.",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = onDeclareClick) {
                                Text("Déclarer le premier objet trouvé")
                            }
                        }
                    }
                }
            } else {
                items(items, key = { it.id }) { item ->
                    CampusItemCard(
                        item = item,
                        onClick = { onItemClick(item.id) },
                        onTogglePin = { onTogglePin(item) },
                        onShare = {
                            shareItemOut(context, item, photoStore)
                        }
                    )
                }
            }

            // Section "Comment ca marche ?" de la PWA
            item(key = "guide_section") {
                Spacer(modifier = Modifier.height(8.dp))
                CampusGuideSection()
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }
}

@Composable
private fun NetworkStatusPill(isOnline: Boolean) {
    val bgColor = if (isOnline) Color(0xFFD1FAE5) else Color(0xFFFEF3C7)
    val textColor = if (isOnline) Color(0xFF065F46) else Color(0xFF92400E)
    val dotColor = if (isOnline) Color(0xFF10B981) else Color(0xFFF59E0B)
    val label = if (isOnline) "En ligne" else "Hors-ligne"

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                color = textColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun RgpdConsentBanner(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "🛡️ Mesure d'audience respectueuse (RGPD - Annexe 4)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Acceptez-vous l'envoi d'événements d'usage anonymes (catégorie d'objet déclaré, scan QR) sans aucune donnée personnelle ni identifiant publicitaire ?",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAccept) {
                    Text("Accepter")
                }
                OutlinedButton(onClick = onDecline) {
                    Text("Refuser")
                }
            }
        }
    }
}

@Composable
private fun CampusHeroCard(
    activeCount: Int,
    returnedCount: Int,
    unsyncedCount: Int,
    onDeclareClick: () -> Unit,
    onScanBarcodeClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF1E3A8A), Color(0xFF2563EB))
                    )
                )
                .padding(18.dp)
        ) {
            Column {
                Text(
                    text = "Objets Trouvés du Campus",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Vous avez trouvé ou perdu un objet sur le campus ? Consultez la liste des objets signalés ou déclarez votre trouvaille en 30 secondes, même hors connexion.",
                    color = Color(0xFFDBEAFE),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatBadge(label = "À récupérer", count = activeCount, color = Color(0xFF93C5FD))
                    StatBadge(label = "Restitués", count = returnedCount, color = Color(0xFF6EE7B7))
                    if (unsyncedCount > 0) {
                        StatBadge(label = "Outbox locale", count = unsyncedCount, color = Color(0xFFFDE68A))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDeclareClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF1E3A8A)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Déclarer", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onScanBarcodeClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scanner QR", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBadge(label: String, count: Int, color: Color) {
    Column {
        Text(
            text = count.toString(),
            color = color,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = label,
            color = Color(0xFFE2E8F0),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun SearchAndFiltersSection(
    searchQuery: String,
    statusFilter: StatusFilter,
    categoryFilter: String,
    onSearchQueryChange: (String) -> Unit,
    onStatusFilterChange: (StatusFilter) -> Unit,
    onCategoryFilterChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Rechercher (ex: clés, calculatrice, BU, code QR...)") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Effacer la recherche")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        // Onglets de statut (Tous / A recuperer / Restitues)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusFilter.entries.forEach { filter ->
                FilterChip(
                    selected = statusFilter == filter,
                    onClick = { onStatusFilterChange(filter) },
                    label = { Text(filter.label) }
                )
            }
        }

        // Filtre par categorie en scroll horizontal
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = categoryFilter == "all",
                onClick = { onCategoryFilterChange("all") },
                label = { Text("Toutes catégories") }
            )
            ItemEntity.CATEGORIES.forEach { cat ->
                FilterChip(
                    selected = categoryFilter == cat,
                    onClick = { onCategoryFilterChange(cat) },
                    label = { Text(cat) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CampusItemCard(
    item: ItemEntity,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onShare: () -> Unit
) {
    val isReturned = item.isReturned
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Badge Statut (identique a la PWA)
                Surface(
                    color = if (isReturned) Color(0xFFD1FAE5) else Color(0xFFDBEAFE),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = if (isReturned) "Restitué ✓" else "À récupérer",
                        color = if (isReturned) Color(0xFF065F46) else Color(0xFF1E40AF),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = item.category,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                if (!item.synced) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "⏳ Hors-ligne",
                            color = Color(0xFF92400E),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(onClick = onTogglePin, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (item.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = "Épingler",
                        tint = if (item.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Partager",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!item.photoPath.isNullOrBlank() && File(item.photoPath).exists()) {
                    AsyncImage(
                        model = File(item.photoPath),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "📍 Trouvé : ${item.locationFound}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "🏛️ Dépôt : ${item.depositLocation}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "📅 ${item.dateFound}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!item.barcodeValue.isNullOrBlank()) {
                    Text(
                        text = "🏷️ ${item.barcodeValue}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF059669),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (item.lat != null && item.lng != null) {
                    Text(
                        text = "🛰️ GPS (±${item.accuracy?.toInt() ?: 10}m)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun CampusGuideSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Comment ça marche sur le campus ?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "1️⃣ Déclarez votre trouvaille (avec photo CameraX, scan QR/code-barres ML Kit ou position GPS, même hors-ligne).",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "2️⃣ Déposez l'objet en lieu sûr (Accueil Bâtiment A, Bibliothèque Universitaire, Secrétariat Sciences...).",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "3️⃣ Récupération & Restitution : le propriétaire récupère son objet et la fiche passe en « Restitué ✓ ».",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun DiagnosticsAndFcmDialog(
    fcmToken: String?,
    analyticsConsent: Boolean,
    notifStatus: PermissionStatus,
    onToggleAnalytics: (Boolean) -> Unit,
    onTriggerTestNotification: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onCopyToken: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notifications Push FCM & RGPD") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "État permission notifications : ${
                        when (notifStatus) {
                            PermissionStatus.Granted -> "Accordée ✓"
                            PermissionStatus.NotAsked -> "Non demandée"
                            PermissionStatus.DeniedOnce -> "Refusée 1 fois"
                            PermissionStatus.DeniedForever -> "Bloquée (Paramètres système)"
                        }
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )

                Button(
                    onClick = onTriggerTestNotification,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔔 Simuler une notification d'objet trouvé")
                }

                if (notifStatus == PermissionStatus.DeniedForever) {
                    OutlinedButton(
                        onClick = onOpenSystemSettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Ouvrir les paramètres Android")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Collecte Analytics anonyme (RGPD)",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = analyticsConsent,
                        onCheckedChange = onToggleAnalytics
                    )
                }

                AssistChip(
                    onClick = onCopyToken,
                    label = {
                        Text(
                            text = "Copier Token FCM (${fcmToken?.take(12) ?: "local"}...)",
                            maxLines = 1
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer")
            }
        }
    )
}

fun shareItemOut(
    context: Context,
    item: ItemEntity,
    photoStore: PhotoStore
) {
    val itemUrl = "${BuildConfig.PWA_URL}?item=${item.remoteId.ifBlank { item.id.toString() }}"
    val shareText = buildString {
        appendLine("🔍 Objet trouvé sur le campus : ${item.title}")
        appendLine("📂 Catégorie : ${item.category}")
        appendLine("📍 Trouvé à : ${item.locationFound} (le ${item.dateFound})")
        appendLine("🏛️ Déposé à : ${item.depositLocation}")
        if (!item.barcodeValue.isNullOrBlank()) {
            appendLine("🏷️ Code : ${item.barcodeValue}")
        }
        if (item.description.isNotBlank()) {
            appendLine("📝 Détails : ${item.description}")
        }
        append("🔗 Lien : $itemUrl")
    }

    val photoUri = item.photoPath?.let { photoStore.contentUri(it) }
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_SUBJECT, "Objet trouvé sur le campus : ${item.title}")
        putExtra(Intent.EXTRA_TEXT, shareText)
        if (photoUri != null) {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, photoUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            type = "text/plain"
        }
    }
    context.startActivity(Intent.createChooser(sendIntent, "Partager l'objet trouvé via"))
}
