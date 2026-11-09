package corsica.universita.objetscampus

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import corsica.universita.objetscampus.analytics.AnalyticsTracker
import corsica.universita.objetscampus.notif.Notifications
import corsica.universita.objetscampus.ui.items.DeclareItemScreen
import corsica.universita.objetscampus.ui.items.ItemDetailScreen
import corsica.universita.objetscampus.ui.items.ItemsListScreen
import corsica.universita.objetscampus.ui.items.ItemsViewModel
import corsica.universita.objetscampus.ui.permissions.rememberPermissionFlow
import corsica.universita.objetscampus.ui.scanner.BarcodeScannerScreen
import corsica.universita.objetscampus.ui.theme.ObjetsCampusTheme

sealed interface NavigationCommand {
    data object OpenDeclare : NavigationCommand
    data object OpenScannerForDeclare : NavigationCommand
    data class OpenItemByRawId(val rawId: String) : NavigationCommand
}

class MainActivity : ComponentActivity() {

    private val viewModel: ItemsViewModel by viewModels()
    private var pendingNavCommand by mutableStateOf<NavigationCommand?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState == null) {
            handleIncomingIntent(intent)
        }

        setContent {
            ObjetsCampusTheme {
                // Observation et trace Logcat du cycle de vie Android (TD A3 - Jour 4)
                LifecycleLogger()

                CampusRootApp(
                    viewModel = viewModel,
                    pendingNavCommand = pendingNavCommand,
                    onConsumeNavCommand = { pendingNavCommand = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        // 1. Deep link depuis une notification locale ou FCM (TD A6)
        val notifItemId = intent.getStringExtra(Notifications.EXTRA_ITEM_ID)
        if (!notifItemId.isNullOrBlank()) {
            Log.d(TAG_LIFECYCLE, "Ouverture depuis notification pour itemId=$notifItemId")
            pendingNavCommand = NavigationCommand.OpenItemByRawId(notifItemId)
            return
        }

        when (intent.action) {
            // 2. Reception d'un texte partage depuis une autre application (TD A8 : ACTION_SEND)
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (!sharedText.isNullOrBlank()) {
                        Log.d(TAG_LIFECYCLE, "Texte recu via ACTION_SEND: $sharedText")
                        viewModel.prefillDraftFromSharedText(sharedText)
                        pendingNavCommand = NavigationCommand.OpenDeclare
                    }
                }
            }

            // 3. Ouverture d'un App Link https://timely-hamster-5836fc.netlify.app/... ou Raccourci / Widget / Tile
            Intent.ACTION_VIEW -> {
                val data = intent.data ?: return
                Log.d(TAG_LIFECYCLE, "Ouverture via ACTION_VIEW: $data")
                val actionParam = data.getQueryParameter("action")
                val itemParam = data.getQueryParameter("item")
                    ?: data.lastPathSegment?.takeIf { it.isNotBlank() && it != "items" }

                when {
                    actionParam == "declare" -> {
                        pendingNavCommand = NavigationCommand.OpenDeclare
                    }
                    actionParam == "scan" -> {
                        pendingNavCommand = NavigationCommand.OpenScannerForDeclare
                    }
                    !itemParam.isNullOrBlank() -> {
                        pendingNavCommand = NavigationCommand.OpenItemByRawId(itemParam)
                    }
                }
            }
        }
    }

    companion object {
        const val TAG_LIFECYCLE = "Carnet"
    }
}

@Composable
private fun LifecycleLogger() {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            Log.d(MainActivity.TAG_LIFECYCLE, "Lifecycle event -> $event")
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

@Composable
private fun CampusRootApp(
    viewModel: ItemsViewModel,
    pendingNavCommand: NavigationCommand?,
    onConsumeNavCommand: () -> Unit
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val allItems by viewModel.allItems.collectAsStateWithLifecycle()
    val filteredItems by viewModel.filteredItems.collectAsStateWithLifecycle()
    val activeCount by viewModel.activeCount.collectAsStateWithLifecycle()
    val returnedCount by viewModel.returnedCount.collectAsStateWithLifecycle()
    val unsyncedCount by viewModel.unsyncedCount.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()
    val fcmToken by viewModel.fcmToken.collectAsStateWithLifecycle()
    val analyticsConsent by viewModel.analyticsConsent.collectAsStateWithLifecycle()
    val umamiWebsiteId by viewModel.umamiWebsiteId.collectAsStateWithLifecycle()
    val conflictState by viewModel.conflictState.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    // Brouillon SavedStateHandle
    val draftTitle by viewModel.draftTitle.collectAsStateWithLifecycle()
    val draftCategory by viewModel.draftCategory.collectAsStateWithLifecycle()
    val draftDescription by viewModel.draftDescription.collectAsStateWithLifecycle()
    val draftDateFound by viewModel.draftDateFound.collectAsStateWithLifecycle()
    val draftLocationFound by viewModel.draftLocationFound.collectAsStateWithLifecycle()
    val draftDepositLocation by viewModel.draftDepositLocation.collectAsStateWithLifecycle()
    val draftPhotoPath by viewModel.draftPhotoPath.collectAsStateWithLifecycle()
    val draftBarcode by viewModel.draftBarcode.collectAsStateWithLifecycle()
    val draftLat by viewModel.draftLat.collectAsStateWithLifecycle()
    val draftLng by viewModel.draftLng.collectAsStateWithLifecycle()
    val draftAccuracy by viewModel.draftAccuracy.collectAsStateWithLifecycle()

    val heroCameraPermission = rememberPermissionFlow(
        permissions = listOf(Manifest.permission.CAMERA),
        rationale = "Autorisez la caméra pour scanner un QR code ou code-barres d'objet trouvé sur le campus."
    ) { granted ->
        if (granted) {
            navController.navigate("scanner/declare")
        } else {
            viewModel.analytics.logBarcodeScanned(result = "denied")
            viewModel.showMessage("Permission caméra refusée : vous pouvez saisir l'objet manuellement.")
        }
    }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearSnackbar()
    }

    // Traitement des commandes de navigation issues des Intents (Notifications, App Links, Partage, Widget, Tile)
    LaunchedEffect(pendingNavCommand, allItems) {
        val cmd = pendingNavCommand ?: return@LaunchedEffect
        when (cmd) {
            NavigationCommand.OpenDeclare -> {
                navController.navigate("declare") { launchSingleTop = true }
                onConsumeNavCommand()
            }
            NavigationCommand.OpenScannerForDeclare -> {
                heroCameraPermission.request()
                onConsumeNavCommand()
            }
            is NavigationCommand.OpenItemByRawId -> {
                if (allItems.isNotEmpty()) {
                    val match = allItems.firstOrNull {
                        it.remoteId == cmd.rawId || it.id.toString() == cmd.rawId
                    } ?: allItems.first()
                    navController.navigate("detail/${match.id}") { launchSingleTop = true }
                    onConsumeNavCommand()
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = "list"
            ) {
                composable("list") {
                    LaunchedEffect(Unit) {
                        viewModel.analytics.logScreenView(
                            screenName = AnalyticsTracker.SCREEN_ITEMS_LIST,
                            screenClass = "ItemsListScreen"
                        )
                    }
                    ItemsListScreen(
                        items = filteredItems,
                        activeCount = activeCount,
                        returnedCount = returnedCount,
                        unsyncedCount = unsyncedCount,
                        isOnline = isOnline,
                        isSyncing = isSyncing,
                        searchQuery = searchQuery,
                        statusFilter = statusFilter,
                        categoryFilter = categoryFilter,
                        fcmToken = fcmToken,
                        analyticsConsent = analyticsConsent,
                        umamiWebsiteId = umamiWebsiteId,
                        photoStore = viewModel.repository.photos,
                        onSearchQueryChange = viewModel::onSearchQueryChange,
                        onStatusFilterChange = viewModel::onStatusFilterChange,
                        onCategoryFilterChange = viewModel::onCategoryFilterChange,
                        onItemClick = { id -> navController.navigate("detail/$id") },
                        onTogglePin = viewModel::togglePin,
                        onDeclareClick = { navController.navigate("declare") },
                        onScanBarcodeClick = { heroCameraPermission.request() },
                        onManualSync = viewModel::manualSync,
                        onSetAnalyticsConsent = viewModel::setAnalyticsConsent,
                        onSaveUmamiWebsiteId = viewModel::saveUmamiWebsiteId,
                        onSendTestNotification = {
                            viewModel.sendTestNotification(navController.context)
                        },
                        onShowMessage = viewModel::showMessage
                    )
                }

                composable("declare") {
                    LaunchedEffect(Unit) {
                        viewModel.analytics.logScreenView(
                            screenName = AnalyticsTracker.SCREEN_ITEM_DECLARE,
                            screenClass = "DeclareItemScreen"
                        )
                    }
                    DeclareItemScreen(
                        title = draftTitle,
                        category = draftCategory,
                        description = draftDescription,
                        dateFound = draftDateFound,
                        locationFound = draftLocationFound,
                        depositLocation = draftDepositLocation,
                        photoPath = draftPhotoPath,
                        barcodeValue = draftBarcode,
                        lat = draftLat,
                        lng = draftLng,
                        accuracy = draftAccuracy,
                        onTitleChange = viewModel::onDraftTitleChange,
                        onCategoryChange = viewModel::onDraftCategoryChange,
                        onDescriptionChange = viewModel::onDraftDescriptionChange,
                        onDateFoundChange = viewModel::onDraftDateFoundChange,
                        onLocationFoundChange = viewModel::onDraftLocationFoundChange,
                        onDepositLocationChange = viewModel::onDraftDepositLocationChange,
                        onImportPhotoUri = viewModel::importDraftPhotoFromUri,
                        onCaptureLocation = viewModel::captureDraftLocation,
                        onOpenCameraScanner = {
                            navController.navigate("scanner/declare")
                        },
                        onCameraDenied = {
                            viewModel.analytics.logBarcodeScanned(result = "denied")
                            viewModel.showMessage("Permission caméra refusée : vous pouvez déclarer sans photo.")
                        },
                        onClearDraft = viewModel::clearDraft,
                        onSubmit = {
                            viewModel.submitDeclaration { newId ->
                                navController.popBackStack()
                                navController.navigate("detail/$newId")
                            }
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = "detail/{itemId}",
                    arguments = listOf(navArgument("itemId") { type = NavType.LongType })
                ) { backStackEntry ->
                    val itemId = backStackEntry.arguments?.getLong("itemId") ?: -1L
                    val item = allItems.firstOrNull { it.id == itemId }

                    LaunchedEffect(itemId) {
                        viewModel.analytics.logScreenView(
                            screenName = AnalyticsTracker.SCREEN_ITEM_DETAIL,
                            screenClass = "ItemDetailScreen"
                        )
                    }

                    ItemDetailScreen(
                        item = item,
                        conflictState = conflictState,
                        photoStore = viewModel.repository.photos,
                        onAttachLocation = {
                            if (item != null) viewModel.attachLocationToItem(item)
                        },
                        onOpenCameraScanner = {
                            navController.navigate("scanner/item_$itemId")
                        },
                        onCameraDenied = {
                            viewModel.analytics.logBarcodeScanned(result = "denied")
                            viewModel.showMessage("Permission caméra refusée.")
                        },
                        onAttachPhotoUri = { uri ->
                            if (item != null) viewModel.attachPhotoUriToItem(item, uri)
                        },
                        onMarkReturned = { simulateConflict ->
                            if (item != null) viewModel.markItemReturned(item, simulateConflict)
                        },
                        onResolveConflict = viewModel::resolveConflict,
                        onDismissConflict = viewModel::dismissConflict,
                        onDelete = {
                            if (item != null) {
                                viewModel.deleteItem(item) {
                                    navController.popBackStack()
                                }
                            }
                        },
                        onBack = { navController.popBackStack() },
                        onShowMessage = viewModel::showMessage
                    )
                }

                composable(
                    route = "scanner/{target}",
                    arguments = listOf(navArgument("target") { type = NavType.StringType })
                ) { backStackEntry ->
                    val target = backStackEntry.arguments?.getString("target") ?: "declare"

                    LaunchedEffect(target) {
                        viewModel.analytics.logScreenView(
                            screenName = AnalyticsTracker.SCREEN_BARCODE_SCANNER,
                            screenClass = "BarcodeScannerScreen"
                        )
                    }

                    BarcodeScannerScreen(
                        modeTitle = if (target == "declare") {
                            "Scanner QR / Photo (Nouvelle déclaration)"
                        } else {
                            "Scanner QR / Photo (Fiche objet)"
                        },
                        onBarcodeDetected = { rawValue, formatName ->
                            if (target.startsWith("item_")) {
                                val id = target.removePrefix("item_").toLongOrNull()
                                val item = allItems.firstOrNull { it.id == id }
                                if (item != null) {
                                    viewModel.attachBarcodeToItem(item, rawValue, formatName)
                                }
                                navController.popBackStack()
                            } else {
                                viewModel.onDraftBarcodeScanned(rawValue, formatName)
                                navController.popBackStack()
                                if (navController.currentDestination?.route != "declare") {
                                    navController.navigate("declare")
                                }
                            }
                        },
                        onPhotoCaptured = { path ->
                            if (target.startsWith("item_")) {
                                val id = target.removePrefix("item_").toLongOrNull()
                                val item = allItems.firstOrNull { it.id == id }
                                if (item != null) {
                                    viewModel.attachPhotoToItem(item, path)
                                }
                                navController.popBackStack()
                            } else {
                                viewModel.onDraftPhotoCaptured(path)
                                navController.popBackStack()
                                if (navController.currentDestination?.route != "declare") {
                                    navController.navigate("declare")
                                }
                            }
                        },
                        onCancel = {
                            viewModel.analytics.logBarcodeScanned(result = "cancelled")
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
}
