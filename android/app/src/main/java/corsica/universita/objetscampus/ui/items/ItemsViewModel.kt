package corsica.universita.objetscampus.ui.items

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import corsica.universita.objetscampus.analytics.AnalyticsTracker
import corsica.universita.objetscampus.data.ConflictState
import corsica.universita.objetscampus.data.ItemsRepository
import corsica.universita.objetscampus.data.ReturnResult
import corsica.universita.objetscampus.data.local.ItemEntity
import corsica.universita.objetscampus.data.local.Settings
import corsica.universita.objetscampus.data.location.Fix
import corsica.universita.objetscampus.data.location.LocationSource
import corsica.universita.objetscampus.notif.Notifications
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class StatusFilter(val label: String) {
    ALL("Tous"),
    ACTIVE("À récupérer"),
    RETURNED("Restitués")
}

/**
 * ViewModel principal (TD A3, A4, A5, A6, A7 + Annexe 4).
 * - Utilise `SavedStateHandle` pour que tout le brouillon de declaration survive a la rotation
 *   ET a la mort du processus en arriere-plan (`adb shell am kill`).
 * - Expose des `StateFlow` immuables consommes par Compose via `collectAsStateWithLifecycle()`.
 */
class ItemsViewModel(
    app: Application,
    private val savedState: SavedStateHandle
) : AndroidViewModel(app) {

    val repository = ItemsRepository(app)
    private val settings = Settings(app)
    private val locationSource = LocationSource(app)
    val analytics = AnalyticsTracker(app)

    // --- Etat reseau temps-reel ---
    private val _isOnline = MutableStateFlow(checkCurrentConnectivity(app))
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    // --- Filtres de recherche (comme dans la PWA) ---
    val searchQuery: StateFlow<String> = savedState.getStateFlow(KEY_SEARCH, "")
    val statusFilter: StateFlow<StatusFilter> = savedState.getStateFlow(KEY_STATUS_FILTER, StatusFilter.ALL.name)
        .map { runCatching { StatusFilter.valueOf(it) }.getOrDefault(StatusFilter.ALL) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatusFilter.ALL)
    val categoryFilter: StateFlow<String> = savedState.getStateFlow(KEY_CATEGORY_FILTER, "all")

    // --- Brouillon de formulaire persiste dans SavedStateHandle (survit a `am kill`) ---
    val draftTitle: StateFlow<String> = savedState.getStateFlow(KEY_DRAFT_TITLE, "")
    val draftCategory: StateFlow<String> = savedState.getStateFlow(KEY_DRAFT_CATEGORY, ItemEntity.CATEGORIES.first())
    val draftDescription: StateFlow<String> = savedState.getStateFlow(KEY_DRAFT_DESC, "")
    val draftDateFound: StateFlow<String> = savedState.getStateFlow(KEY_DRAFT_DATE, LocalDate.now().toString())
    val draftLocationFound: StateFlow<String> = savedState.getStateFlow(KEY_DRAFT_LOCATION, "")
    val draftDepositLocation: StateFlow<String> = savedState.getStateFlow(
        KEY_DRAFT_DEPOSIT,
        ItemEntity.DEPOSIT_LOCATIONS.first()
    )
    val draftPhotoPath: StateFlow<String?> = savedState.getStateFlow(KEY_DRAFT_PHOTO, null)
    val draftBarcode: StateFlow<String?> = savedState.getStateFlow(KEY_DRAFT_BARCODE, null)
    val draftLat: StateFlow<Double?> = savedState.getStateFlow(KEY_DRAFT_LAT, null)
    val draftLng: StateFlow<Double?> = savedState.getStateFlow(KEY_DRAFT_LNG, null)
    val draftAccuracy: StateFlow<Float?> = savedState.getStateFlow(KEY_DRAFT_ACC, null)

    // --- Source de verite unique Room ---
    val allItems: StateFlow<List<ItemEntity>> = repository.items
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filteredItems: StateFlow<List<ItemEntity>> = combine(
        allItems,
        searchQuery,
        statusFilter,
        categoryFilter
    ) { list, query, status, category ->
        val q = query.trim().lowercase()
        list.filter { item ->
            val matchesStatus = when (status) {
                StatusFilter.ALL -> true
                StatusFilter.ACTIVE -> item.status == ItemEntity.STATUS_ACTIVE
                StatusFilter.RETURNED -> item.status == ItemEntity.STATUS_RETURNED
            }
            val matchesCategory = category == "all" || item.category == category
            val matchesSearch = q.isEmpty() ||
                item.title.lowercase().contains(q) ||
                item.description.lowercase().contains(q) ||
                item.locationFound.lowercase().contains(q) ||
                item.category.lowercase().contains(q) ||
                (item.barcodeValue?.lowercase()?.contains(q) == true)
            matchesStatus && matchesCategory && matchesSearch
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unsyncedCount: StateFlow<Int> = allItems
        .map { list -> list.count { !it.synced } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val activeCount: StateFlow<Int> = allItems
        .map { list -> list.count { it.status == ItemEntity.STATUS_ACTIVE } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val returnedCount: StateFlow<Int> = allItems
        .map { list -> list.count { it.status == ItemEntity.STATUS_RETURNED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // --- Parametres DataStore (Token FCM, Consentement Analytics RGPD & Umami Website ID) ---
    val fcmToken: StateFlow<String?> = settings.fcmToken
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val analyticsConsent: StateFlow<Boolean?> = settings.analyticsConsent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val umamiWebsiteId: StateFlow<String> = settings.umamiWebsiteId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    // --- Etats UI transitoires ---
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _conflictState = MutableStateFlow<ConflictState?>(null)
    val conflictState: StateFlow<ConflictState?> = _conflictState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val connectivityManager =
        app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = true
            analytics.flushUmamiNow()
        }

        override fun onLost(network: Network) {
            _isOnline.value = checkCurrentConnectivity(getApplication())
        }
    }

    init {
        runCatching {
            connectivityManager?.registerDefaultNetworkCallback(networkCallback)
        }
        viewModelScope.launch {
            repository.ensureSeeded()
            // Synchronisation initiale silencieuse au lancement
            runCatching { repository.syncPending() }
        }
        viewModelScope.launch {
            settings.analyticsConsent.collect { consent ->
                analytics.setCollectionEnabled(consent == true)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        runCatching {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        }
    }

    // --- Actions de filtrage ---
    fun onSearchQueryChange(q: String) {
        savedState[KEY_SEARCH] = q
    }

    fun onStatusFilterChange(filter: StatusFilter) {
        savedState[KEY_STATUS_FILTER] = filter.name
    }

    fun onCategoryFilterChange(cat: String) {
        savedState[KEY_CATEGORY_FILTER] = cat
    }

    // --- Actions sur le brouillon (SavedStateHandle) ---
    fun onDraftTitleChange(v: String) {
        savedState[KEY_DRAFT_TITLE] = v
    }

    fun onDraftCategoryChange(v: String) {
        savedState[KEY_DRAFT_CATEGORY] = v
    }

    fun onDraftDescriptionChange(v: String) {
        savedState[KEY_DRAFT_DESC] = v
    }

    fun onDraftDateFoundChange(v: String) {
        savedState[KEY_DRAFT_DATE] = v
    }

    fun onDraftLocationFoundChange(v: String) {
        savedState[KEY_DRAFT_LOCATION] = v
    }

    fun onDraftDepositLocationChange(v: String) {
        savedState[KEY_DRAFT_DEPOSIT] = v
    }

    fun onDraftPhotoCaptured(path: String) {
        savedState[KEY_DRAFT_PHOTO] = path
        showMessage("📷 Photo enregistrée dans le stockage interne")
    }

    fun importDraftPhotoFromUri(uri: Uri) = viewModelScope.launch {
        val path = repository.photos.saveFromUri(uri)
        if (path != null) {
            savedState[KEY_DRAFT_PHOTO] = path
            showMessage("🖼️ Photo importée et compressée")
        } else {
            showMessage("Impossible de lire cette image")
        }
    }

    fun onDraftBarcodeScanned(code: String, format: String) {
        savedState[KEY_DRAFT_BARCODE] = code
        if (draftTitle.value.isBlank()) {
            savedState[KEY_DRAFT_TITLE] = "Objet identifié ($code)"
        }
        analytics.logBarcodeScanned(result = "ok", format = format)
        showMessage("🔍 Code scanné : $code")
    }

    fun captureDraftLocation() = viewModelScope.launch {
        val fix: Fix? = locationSource.currentFix()
        if (fix != null) {
            savedState[KEY_DRAFT_LAT] = fix.lat
            savedState[KEY_DRAFT_LNG] = fix.lng
            savedState[KEY_DRAFT_ACC] = fix.accuracy
            val precisionLabel = if (fix.precise) "précise" else "approx."
            if (draftLocationFound.value.isBlank()) {
                savedState[KEY_DRAFT_LOCATION] =
                    "Campus (${"%.4f".format(fix.lat)}, ${"%.4f".format(fix.lng)})"
            }
            showMessage("📍 Position GPS ($precisionLabel) acquise à ±${fix.accuracy.toInt()}m")
        } else {
            showMessage("Position GPS indisponible sur cet appareil")
        }
    }

    fun prefillDraftFromSharedText(sharedText: String) {
        val lines = sharedText.trim().lines()
        val firstLine = lines.firstOrNull().orEmpty().take(80)
        if (draftTitle.value.isBlank() && firstLine.isNotBlank()) {
            savedState[KEY_DRAFT_TITLE] = firstLine
        }
        savedState[KEY_DRAFT_DESC] = sharedText.trim()
        showMessage("Texte partagé pré-rempli dans le brouillon !")
    }

    fun clearDraft() {
        savedState[KEY_DRAFT_TITLE] = ""
        savedState[KEY_DRAFT_CATEGORY] = ItemEntity.CATEGORIES.first()
        savedState[KEY_DRAFT_DESC] = ""
        savedState[KEY_DRAFT_DATE] = LocalDate.now().toString()
        savedState[KEY_DRAFT_LOCATION] = ""
        savedState[KEY_DRAFT_DEPOSIT] = ItemEntity.DEPOSIT_LOCATIONS.first()
        savedState[KEY_DRAFT_PHOTO] = null
        savedState[KEY_DRAFT_BARCODE] = null
        savedState[KEY_DRAFT_LAT] = null
        savedState[KEY_DRAFT_LNG] = null
        savedState[KEY_DRAFT_ACC] = null
    }

    fun submitDeclaration(onCreated: (Long) -> Unit) = viewModelScope.launch {
        val title = draftTitle.value.trim()
        val loc = draftLocationFound.value.trim()
        if (title.isBlank() || loc.isBlank()) {
            showMessage("Veuillez renseigner l'objet trouvé et le lieu de découverte.")
            return@launch
        }

        val offline = !_isOnline.value
        val hasPhoto = !draftPhotoPath.value.isNullOrBlank()
        val hasGps = draftLat.value != null && draftLng.value != null
        val hasBarcode = !draftBarcode.value.isNullOrBlank()
        val cat = draftCategory.value

        val newId = repository.add(
            title = title,
            category = cat,
            description = draftDescription.value,
            dateFound = draftDateFound.value,
            locationFound = loc,
            depositLocation = draftDepositLocation.value,
            lat = draftLat.value,
            lng = draftLng.value,
            accuracy = draftAccuracy.value,
            photoPath = draftPhotoPath.value,
            barcodeValue = draftBarcode.value
        )

        // Annexe 4 : Evenement d'activation item_declared (sans aucune PII)
        analytics.logItemDeclared(
            category = cat,
            offline = offline,
            hasPhoto = hasPhoto,
            hasGps = hasGps,
            hasBarcode = hasBarcode
        )

        clearDraft()
        if (offline) {
            showMessage("📦 Enregistré hors-ligne ! Synchronisation automatique dès le retour du réseau.")
        } else {
            showMessage("✅ Objet déclaré avec succès sur le campus !")
        }
        onCreated(newId)
    }

    // --- Actions sur un objet existant ---
    fun togglePin(item: ItemEntity) = viewModelScope.launch {
        repository.togglePin(item)
    }

    fun deleteItem(item: ItemEntity, onDeleted: () -> Unit = {}) = viewModelScope.launch {
        repository.delete(item)
        showMessage("Objet supprimé")
        onDeleted()
    }

    fun attachLocationToItem(item: ItemEntity) = viewModelScope.launch {
        val fix = locationSource.currentFix()
        if (fix != null) {
            repository.attachLocation(item, fix)
            showMessage("📍 Coordonnées GPS associées (±${fix.accuracy.toInt()}m)")
        } else {
            showMessage("Impossible d'obtenir un point GPS")
        }
    }

    fun attachPhotoToItem(item: ItemEntity, path: String) = viewModelScope.launch {
        repository.attachPhoto(item, path)
        showMessage("📷 Photo associée à la fiche")
    }

    fun attachPhotoUriToItem(item: ItemEntity, uri: Uri) = viewModelScope.launch {
        val path = repository.photos.saveFromUri(uri)
        if (path != null) {
            repository.attachPhoto(item, path)
            showMessage("🖼️ Photo associée à la fiche")
        }
    }

    fun attachBarcodeToItem(item: ItemEntity, barcode: String, format: String) = viewModelScope.launch {
        repository.attachBarcode(item, barcode)
        analytics.logBarcodeScanned(result = "ok", format = format)
        showMessage("🔍 Code $barcode associé à l'objet")
    }

    fun markItemReturned(item: ItemEntity, simulateConflict: Boolean = false) = viewModelScope.launch {
        when (val res = repository.markAsReturned(item, simulateConflict)) {
            is ReturnResult.Success -> {
                analytics.logItemReturned(category = item.category, conflictResolved = false)
                showMessage("🎉 Objet marqué comme restitué à son propriétaire !")
            }
            is ReturnResult.QueuedOffline -> {
                analytics.logItemReturned(category = item.category, conflictResolved = false)
                showMessage("⏳ Restitution enregistrée hors-ligne (en attente de synchronisation)")
            }
            is ReturnResult.Conflict412 -> {
                _conflictState.value = res.conflict
            }
        }
    }

    fun resolveConflict(forceLocal: Boolean) = viewModelScope.launch {
        val conflict = _conflictState.value ?: return@launch
        repository.resolveConflict(conflict, forceLocal)
        _conflictState.value = null
        if (forceLocal) {
            analytics.logItemReturned(
                category = conflict.localItem.category,
                conflictResolved = true
            )
            showMessage("⚡ Modification locale forcée et synchronisée !")
        } else {
            showMessage("🔄 Version du serveur restaurée.")
        }
    }

    fun dismissConflict() {
        _conflictState.value = null
    }

    fun manualSync() = viewModelScope.launch {
        _isSyncing.value = true
        try {
            val count = repository.syncPending()
            if (count > 0) {
                showMessage("🔄 $count objet(s) synchronisé(s) avec le serveur !")
            } else {
                showMessage("✅ Base locale à jour avec le serveur du campus.")
            }
        } catch (_: Exception) {
            showMessage("⚠️ Serveur injoignable : vos données restent sauvegardées en local.")
        } finally {
            _isSyncing.value = false
        }
    }

    fun setAnalyticsConsent(granted: Boolean) = viewModelScope.launch {
        settings.setAnalyticsConsent(granted)
        analytics.setCollectionEnabled(granted)
        showMessage(
            if (granted) "Mesure d'audience anonyme activée" else "Mesure d'audience désactivée (RGPD)"
        )
    }

    fun saveUmamiWebsiteId(websiteId: String) = viewModelScope.launch {
        settings.saveUmamiConfig(websiteId)
        if (websiteId.isNotBlank()) {
            analytics.logScreenView(AnalyticsTracker.SCREEN_ITEMS_LIST, "ItemsListScreen")
            showMessage("🌿 Umami configuré ! Événement de test envoyé à cloud.umami.is")
        } else {
            showMessage("Umami désactivé")
        }
    }

    fun sendTestNotification(context: Context) {
        val target = allItems.value.firstOrNull()
        val itemId = target?.remoteId?.ifBlank { target.id.toString() } ?: "seed-1"
        val title = "🔔 Objet trouvé sur le campus"
        val body = target?.let { "${it.title} — déposé à ${it.depositLocation}" }
            ?: "Clés avec porte-clé bleu — déposé à Accueil Bâtiment A"
        Notifications.showItemNotification(context, itemId, title, body)
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    private fun checkCurrentConnectivity(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        private const val KEY_SEARCH = "search_query"
        private const val KEY_STATUS_FILTER = "status_filter"
        private const val KEY_CATEGORY_FILTER = "category_filter"

        private const val KEY_DRAFT_TITLE = "draft_title"
        private const val KEY_DRAFT_CATEGORY = "draft_category"
        private const val KEY_DRAFT_DESC = "draft_description"
        private const val KEY_DRAFT_DATE = "draft_date_found"
        private const val KEY_DRAFT_LOCATION = "draft_location_found"
        private const val KEY_DRAFT_DEPOSIT = "draft_deposit_location"
        private const val KEY_DRAFT_PHOTO = "draft_photo_path"
        private const val KEY_DRAFT_BARCODE = "draft_barcode"
        private const val KEY_DRAFT_LAT = "draft_lat"
        private const val KEY_DRAFT_LNG = "draft_lng"
        private const val KEY_DRAFT_ACC = "draft_acc"
    }
}
