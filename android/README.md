# 🔍 Objets Trouvés du Campus — Application Android Native (Kotlin / Jetpack Compose)

> **Projet M2 Dév. Full Stack — Université de Corse**  
> Portage Android natif de la PWA **Objets Trouvés du Campus** (`https://timely-hamster-5836fc.netlify.app/`).

---

## 📋 Présentation

Cette application Android native reprend l'intégralité du métier et de l'identité visuelle de la PWA **Objets Trouvés du Campus** (déclaration d'objets trouvés sur le campus, filtrage par statut/catégorie/recherche, restitution au propriétaire avec gestion des conflits `HTTP 412 / If-Match`, fonctionnement **Offline-First**), tout en y ajoutant les **piliers de l'architecture Android moderne** (Jours 4 & 5) ainsi que **3 fonctionnalités natives impossibles dans une PWA** et un **plan de marquage Analytics conforme RGPD** (Annexe 4).

---

## 🏗️ Architecture & Socle Technique (Jours 4 & 5)

```
┌──────────────────────────────────────────────────────────────────────────┐
│                         UI Jetpack Compose (M3)                          │
│  ItemsListScreen  │  DeclareItemScreen  │  ItemDetailScreen  │  Scanner  │
└────────────────────────────────────┬─────────────────────────────────────┘
                                     │ StateFlow + collectAsStateWithLifecycle()
                                     ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                      ItemsViewModel (AndroidViewModel)                   │
│  • SavedStateHandle : brouillon persistant (rotation + am kill)          │
│  • LifecycleEventObserver : journalisation Logcat tag "Carnet"           │
│  • AnalyticsTracker : screen_view manuels + 3 événements snake_case      │
└────────────────────────────────────┬─────────────────────────────────────┘
                                     │
                                     ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                ItemsRepository (Source de vérité unique)                 │
│  • Offline-First : l'UI observe uniquement Room (Flow<List<ItemEntity>>) │
│  • Toute écriture met à jour Room (synced = false) puis planifie Sync    │
└───────┬──────────────────┬──────────────────┬──────────────────┬─────────┘
        │                  │                  │                  │
        ▼                  ▼                  ▼                  ▼
┌───────────────┐  ┌───────────────┐  ┌───────────────┐  ┌─────────────────┐
│  Room SQLite  │  │   DataStore   │  │  PhotoStore   │  │ Retrofit/OkHttp │
│  Version = 4  │  │  Preferences  │  │ filesDir/     │  │ Cache HTTP 10Mo │
│  Migrations   │  │ • fcm_token   │  │ photos/*.jpg  │  │ API Netlify     │
│  1→2, 2→3, 3→4│  │ • rgpd_consent│  │ + FileProvider│  │ + Conflit 412   │
└───────────────┘  └───────────────┘  └───────────────┘  └─────────────────┘
```

### Détail des briques du socle :
1. **Persistance structurée Room (`version = 4`, `exportSchema = true`)** :
   - [`ItemEntity.kt`](app/src/main/java/corsica/universita/objetscampus/data/local/ItemEntity.kt), [`ItemDao.kt`](app/src/main/java/corsica/universita/objetscampus/data/local/ItemDao.kt), [`CampusDatabase.kt`](app/src/main/java/corsica/universita/objetscampus/data/local/CampusDatabase.kt).
   - Migrations SQL explicites sans `fallbackToDestructiveMigration` :
     - `MIGRATION_1_2` : `ALTER TABLE items ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0`
     - `MIGRATION_2_3` : `ALTER TABLE items ADD COLUMN synced INTEGER NOT NULL DEFAULT 0`
     - `MIGRATION_3_4` : ajout de `lat`, `lng`, `accuracy`, `photoPath` et `barcodeValue`
   - Schémas JSON exportés et versionnés dans `app/schemas/.../1.json`, `2.json`, `3.json`, `4.json`.
2. **Stockage clé-valeur typé (`DataStore Preferences`)** :
   - [`Settings.kt`](app/src/main/java/corsica/universita/objetscampus/data/local/Settings.kt) stocke le token FCM (`fcm_token`) et le choix de consentement RGPD Analytics (`analytics_consent`).
3. **Stockage fichiers & partage sécurisé (`PhotoStore` + `FileProvider`)** :
   - [`PhotoStore.kt`](app/src/main/java/corsica/universita/objetscampus/data/local/PhotoStore.kt) compresse et enregistre les photos JPEG dans `filesDir/photos/` sur `Dispatchers.IO`. Seul le chemin `photoPath` est stocké dans Room (évite `SQLiteBlobTooBigException`). Partage externe via `content://` (`FileProvider`).
4. **Réseau & Synchronisation en arrière-plan (`Retrofit` + `OkHttp` + `WorkManager`)** :
   - [`Network.kt`](app/src/main/java/corsica/universita/objetscampus/data/remote/Network.kt) configure un cache HTTP disque de **10 Mo** dans `cacheDir/http`.
   - [`SyncWorker.kt`](app/src/main/java/corsica/universita/objetscampus/work/SyncWorker.kt) (`CoroutineWorker`) utilise `enqueueUniqueWork("sync", ExistingWorkPolicy.KEEP, ...)` sous contrainte `NetworkType.CONNECTED` avec `BackoffPolicy.EXPONENTIAL`.
5. **Notifications locales & Push (`Firebase Cloud Messaging`)** :
   - [`Notifications.kt`](app/src/main/java/corsica/universita/objetscampus/notif/Notifications.kt) déclare 2 canaux dès [`CampusApp.onCreate`](app/src/main/java/corsica/universita/objetscampus/CampusApp.kt) : `sync` (`IMPORTANCE_LOW`) et `hub` (`IMPORTANCE_HIGH`).
   - `PendingIntent` avec `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT` et `requestCode = itemId.hashCode()` pour ouvrir directement la fiche de l'objet ciblé même lorsque l'application est tuée.
6. **Machine à états des permissions runtime (`PermissionFlow`)** :
   - [`PermissionFlow.kt`](app/src/main/java/corsica/universita/objetscampus/ui/permissions/PermissionFlow.kt) implémente les 4 états (`NotAsked`, `Granted`, `DeniedOnce`, `DeniedForever`) avec dialogue de rationale, bouton vers les Paramètres système (`ACTION_APPLICATION_DETAILS_SETTINGS`) et rafraîchissement automatique sur `ON_RESUME`. Supporte la localisation approximative d'Android 12+ (`requireAll = false`).
7. **Intents, App Links & Signature Release** :
   - Partage sortant `ACTION_SEND` (texte + photo via `FileProvider`), réception `ACTION_SEND` (`text/plain` pré-remplissant le brouillon), ouverture de la PWA via `ACTION_VIEW`, et App Link `autoVerify="true"` relié à [`TP/.well-known/assetlinks.json`](../TP/.well-known/assetlinks.json).

---

## 🚀 Ce que fait l'application Android et que ne pouvait pas faire la PWA ("Le Natif")

1. **Pipeline Caméra Temps-Réel `CameraX` + IA embarquée `Google ML Kit Barcode Scanning`** ([`BarcodeScannerScreen.kt`](app/src/main/java/corsica/universita/objetscampus/ui/scanner/BarcodeScannerScreen.kt)) :
   - Analyse vidéo à 30 fps directement sur le NPU/CPU du téléphone (`ImageAnalysis` + ML Kit) pour détecter instantanément les QR codes de salles du campus, cartes étudiantes/CROUS ou codes-barres d'objets, tout en permettant la prise de photo haute définition (`ImageCapture`) dans la même session caméra.
   - *Pourquoi la PWA ne pouvait pas le faire* : dans le navigateur, l'accès caméra passe par `<input type="file" capture>` ou `getUserMedia` sans accès au pipeline natif multi-use-case (`Preview` + `ImageAnalysis` + `ImageCapture`), et l'API Web `BarcodeDetector` n'est pas supportée sur tous les navigateurs (absente sur iOS/Firefox).
2. **Widget d'écran d'accueil Android (`Jetpack Glance AppWidget`)** ([`CampusLostFoundWidget.kt`](app/src/main/java/corsica/universita/objetscampus/widget/CampusLostFoundWidget.kt)) :
   - Widget interactif sur le bureau Android connecté directement à la base SQLite `Room` : affiche en temps réel le nombre d'objets à récupérer, le dernier objet trouvé sur le campus, et 2 boutons d'action rapide (`➕ Déclarer`, `📷 Scanner QR`).
   - *Pourquoi la PWA ne pouvait pas le faire* : une PWA installée sur Android (WebAPK) ne peut pas exposer de `GlanceAppWidget` / `AppWidgetProvider` natif sur l'écran d'accueil du système.
3. **Tuile de Réglages Rapides Système (`Quick Settings TileService`) + App Shortcuts + Réception `ACTION_SEND` avec `FileProvider`** ([`QuickDeclareTileService.kt`](app/src/main/java/corsica/universita/objetscampus/tile/QuickDeclareTileService.kt)) :
   - Tuile système dans le volet de notifications Android permettant de lancer une déclaration d'objet trouvé depuis n'importe quel écran de l'OS en 1 geste, plus raccourcis d'appui long sur l'icône (`res/xml/shortcuts.xml`).
   - *Pourquoi la PWA ne pouvait pas le faire* : l'API `TileService` (`BIND_QUICK_SETTINGS_TILE`) est réservée aux applications natives signées.
4. **Synchronisation `WorkManager` survivant au reboot et au kill de l'app** ([`SyncWorker.kt`](app/src/main/java/corsica/universita/objetscampus/work/SyncWorker.kt)) :
   - Contrairement à l'API Web `Background Sync` (absente sur Safari/Firefox et dépendante du processus du navigateur), `WorkManager` est persisté par le `JobScheduler` d'Android et s'exécute même après redémarrage complet du téléphone.
5. **Survie du brouillon à la mort du processus (`SavedStateHandle`)** ([`ItemsViewModel.kt`](app/src/main/java/corsica/universita/objetscampus/ui/items/ItemsViewModel.kt)) :
   - Si l'OS tue l'application en arrière-plan pour libérer de la RAM pendant que l'utilisateur prend une photo ou vérifie une salle, l'intégralité du formulaire en cours est restaurée par le système Android.

---

## 📊 Plan de Marquage Analytics & RGPD (Annexe 4)

Implémenté dans [`AnalyticsTracker.kt`](app/src/main/java/corsica/universita/objetscampus/analytics/AnalyticsTracker.kt) et [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml) :
- **Conformité RGPD** :
  - `firebase_analytics_collection_enabled = false` par défaut dans le Manifest.
  - Permission publicitaire `com.google.android.gms.permission.AD_ID` explicitement supprimée via `tools:node="remove"`.
  - Bannière de consentement affichée dans l'application ; la collecte n'est activée qu'après accord explicite (`setAnalyticsCollectionEnabled(true)`).
  - **Zéro PII** : aucun titre libre, aucune description, aucun email ni coordonnée GPS n'est envoyé à Analytics.
- **Suivi d'écrans manuel (`screen_view`)** avec constantes en dur survivant à l'obfuscation R8 :
  - `items_list`, `item_declare`, `item_detail`, `barcode_scanner`.
- **Les 3 événements métier (`snake_case` au passé)** :
  1. **Activation** : `item_declared` (paramètres : `category`, `offline` `0/1`, `has_photo` `0/1`, `has_gps` `0/1`, `has_barcode` `0/1`)
  2. **Rétention** : `item_returned` (paramètres : `category`, `conflict_resolved` `0/1`)
  3. **Fonctionnalité native** : `barcode_scanned` (paramètres : `result` = `"ok"` | `"denied"` | `"cancelled"`, `format` = `"QR_CODE"` | `"EAN_13"` | ...)

---

## 🛠️ Commandes de Build & Vérification (`adb`)

### 1. Compiler l'APK Debug / Release et lancer les tests
```powershell
cd android
.\gradlew.bat assembleDebug testDebugUnitTest
.\gradlew.bat assembleRelease
```

### 2. Tester la survie à la mort du processus (`SavedStateHandle` - TD A3)
```powershell
# 1. Ouvrir l'écran "Déclarer un objet", saisir un titre sans valider
# 2. Mettre l'application en arrière-plan (bouton Home)
adb shell am kill corsica.universita.objetscampus
# 3. Rouvrir l'application depuis les Récents -> le brouillon est intact !
```

### 3. Tester les App Links (`assetlinks.json` - TD A8)
```powershell
adb shell pm get-app-links corsica.universita.objetscampus
adb shell am start -a android.intent.action.VIEW -d "https://timely-hamster-5836fc.netlify.app/?item=seed-1"
```

### 4. Tester le DebugView Analytics en temps réel (Annexe 4)
```powershell
adb shell setprop debug.firebase.analytics.app corsica.universita.objetscampus
adb logcat -s CampusAnalytics Carnet SyncWorker FCM
```
