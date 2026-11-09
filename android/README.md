# 🔍 Objets Trouvés du Campus — Application Android Native (Kotlin / Jetpack Compose)

> **Projet M2 Dév. Full Stack — Université de Corse**  
> Portage Android natif de la PWA **Objets Trouvés du Campus** ([`https://timely-hamster-5836fc.netlify.app/`](https://timely-hamster-5836fc.netlify.app/)).

---

## 🔗 Liens Rapides (Vidéo de Démo & APK Release GitHub)

| Ressource | Lien d'accès |
| :--- | :--- |
| 🎥 **Vidéo de démonstration** | [**Voir la démo sur YouTube (`https://youtu.be/HBkg435O5ew`)**](https://youtu.be/HBkg435O5ew) |
| 📦 **APK (Release GitHub)** | [**Télécharger l'APK depuis GitHub Releases**](https://github.com/gwenolelt/pwa-campus-objets-trouves-android/releases/latest) *(ou sur le [dépôt principal](https://github.com/gwenolelt/pwa-campus-objets-trouves/releases/latest))* |
| 🌐 **PWA en production (Backend & App Links)** | [`https://timely-hamster-5836fc.netlify.app/`](https://timely-hamster-5836fc.netlify.app/) |

---

## 📱 1. L'Application

### Présentation générale
**Objets Trouvés du Campus** est l'application mobile native destinée aux étudiants et au personnel de l'Université de Corse pour **déclarer, rechercher et restituer les objets trouvés sur le campus** (clés, cartes étudiantes/CROUS, matériel informatique, vêtements, documents...), même dans les bâtiments sans couverture réseau (amphithéâtres en sous-sol, parkings, zones blanches).

Elle reprend l'intégralité du métier et de l'identité visuelle de la PWA tout en exploitant les capacités matérielles et système d'Android :
- **Consultation & Recherche instantanée (100% Offline-First)** : liste des objets trouvés synchronisée avec le serveur Netlify, filtrage par statut (*Tous*, *À récupérer*, *Restitués*), par catégorie, recherche plein texte et épinglage local favori (`pinned`).
- **Déclaration enrichie d'un objet trouvé** : saisie du titre, de la catégorie, du lieu de découverte, du lieu de dépôt (accueil BU, secrétariat, loge...), avec **prise de photo**, **géolocalisation GPS** et **scan de QR code / code-barres**.
- **Restitution & Gestion des conflits de concurrence (`HTTP 412 / If-Match`)** : marquage d'un objet comme restitué à son propriétaire avec détection de modification concurrente et dialogue interactif de résolution (*Garder ma version* vs *Prendre la version du serveur*).
- **Partage & Intégration OS** : partage natif de fiche avec photo jointe (`ACTION_SEND` + `FileProvider`), création rapide de brouillon en partageant du texte depuis une autre application vers *Objets Campus*, et ouverture automatique des liens web `https://timely-hamster-5836fc.netlify.app/?item=...` directement dans l'application native (**Android App Links**).

### Ce que fait l'application Android et que ne pouvait pas faire la PWA ("Le Natif")
1. **Pipeline Caméra Temps-Réel `CameraX` + IA embarquée `Google ML Kit Barcode Scanning`** ([`BarcodeScannerScreen.kt`](app/src/main/java/corsica/universita/objetscampus/ui/scanner/BarcodeScannerScreen.kt)) :
   - Analyse vidéo à 30 fps directement sur le NPU/CPU du téléphone (`ImageAnalysis` + ML Kit) pour détecter instantanément les QR codes de salles du campus, cartes étudiantes/CROUS ou codes-barres d'objets, tout en permettant la prise de photo haute définition (`ImageCapture`) dans la même session caméra.
   - *Pourquoi la PWA ne pouvait pas le faire* : dans le navigateur, l'accès caméra ne donne pas accès au pipeline natif multi-use-case (`Preview` + `ImageAnalysis` + `ImageCapture`), et l'API Web `BarcodeDetector` n'est pas supportée sur tous les navigateurs.
2. **Widget d'écran d'accueil Android (`Jetpack Glance AppWidget`)** ([`CampusLostFoundWidget.kt`](app/src/main/java/corsica/universita/objetscampus/widget/CampusLostFoundWidget.kt)) :
   - Widget interactif sur le bureau Android connecté directement à la base SQLite `Room` : affiche en temps réel le nombre d'objets à récupérer, le dernier objet trouvé sur le campus, et 2 boutons d'action rapide (`➕ Déclarer`, `📷 Scanner QR`).
3. **Tuile de Réglages Rapides Système (`Quick Settings TileService`) & App Shortcuts** ([`QuickDeclareTileService.kt`](app/src/main/java/corsica/universita/objetscampus/tile/QuickDeclareTileService.kt)) :
   - Tuile système dans le volet de notifications Android permettant de lancer une déclaration d'objet trouvé depuis n'importe quel écran de l'OS en 1 geste, complétée par des raccourcis d'appui long sur l'icône de l'application (`res/xml/shortcuts.xml`).
4. **Synchronisation `WorkManager` survivant au redémarrage du téléphone** ([`SyncWorker.kt`](app/src/main/java/corsica/universita/objetscampus/work/SyncWorker.kt)) :
   - Contrairement à l'API Web `Background Sync` dépendante du navigateur, `WorkManager` est persisté par le `JobScheduler` d'Android et synchronise les déclarations hors-ligne même après fermeture de l'app ou reboot de l'appareil.
5. **Survie du brouillon à la mort du processus (`SavedStateHandle`)** ([`ItemsViewModel.kt`](app/src/main/java/corsica/universita/objetscampus/ui/items/ItemsViewModel.kt)) :
   - Si Android tue l'application en arrière-plan pour libérer de la RAM pendant que l'utilisateur prend une photo, l'intégralité du formulaire en cours est restaurée.

---

## 🏗️ 2. Nos Choix Techniques

### Schéma d'architecture (Unidirectional Data Flow & Offline-First)

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
│  • AnalyticsTracker : Firebase + Umami HTTP POST /api/send (Outbox)      │
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

### Justification des choix techniques par brique

| Domaine | Choix technique | Justification |
| :--- | :--- | :--- |
| **Langage & UI** | **Kotlin 2.0 + Jetpack Compose (Material 3)** | UI déclarative moderne, réactive via `StateFlow` et `collectAsStateWithLifecycle()` pour éviter tout gaspillage CPU/batterie lorsque l'application passe en arrière-plan. |
| **Cycle de vie & État** | **`AndroidViewModel` + `SavedStateHandle`** | Séparation stricte entre l'état d'écran et l'Activity. `SavedStateHandle` garantit la survie du brouillon de déclaration à la rotation d'écran ET au *Process Death* (`am kill`). |
| **Base de données locale** | **Room SQLite (`version = 4`, `exportSchema = true`)** | Source de vérité unique (*Offline-First*). L'UI n'attend jamais le réseau : elle observe un `Flow<List<ItemEntity>>`. Inclut **3 migrations SQL explicites** (`MIGRATION_1_2`, `MIGRATION_2_3`, `MIGRATION_3_4`) sans `fallbackToDestructiveMigration`, avec schémas JSON versionnés dans [`app/schemas/`](app/schemas/corsica.universita.objetscampus.data.local.CampusDatabase/). |
| **Préférences** | **Jetpack DataStore Preferences** | Remplace `SharedPreferences` de manière asynchrone et *thread-safe* sur `Dispatchers.IO` ([`Settings.kt`](app/src/main/java/corsica/universita/objetscampus/data/local/Settings.kt)) pour stocker le token FCM, le consentement RGPD et la configuration Umami. |
| **Stockage Photos & Partage** | **`PhotoStore` (`filesDir/photos/`) + `FileProvider`** | Les photos sont compressées en JPEG (85%) sur `Dispatchers.IO` ([`PhotoStore.kt`](app/src/main/java/corsica/universita/objetscampus/data/local/PhotoStore.kt)). Seul le chemin `photoPath` est stocké dans Room pour éviter `SQLiteBlobTooBigException`. Le partage externe utilise des URI sécurisées `content://`. |
| **Réseau & Cache HTTP** | **Retrofit 2 + OkHttp 4 + `kotlinx.serialization`** | Sérialisation Kotlin native légère et typée, couplée à un cache HTTP disque de **10 Mo** (`cacheDir/http`) dans [`Network.kt`](app/src/main/java/corsica/universita/objetscampus/data/remote/Network.kt) et gestion des préconditions `If-Match` / `HTTP 412`. |
| **Sync Arrière-Plan** | **WorkManager (`CoroutineWorker`)** | [`SyncWorker.kt`](app/src/main/java/corsica/universita/objetscampus/work/SyncWorker.kt) planifie une tâche unique (`enqueueUniqueWork("sync", ExistingWorkPolicy.KEEP, ...)`) sous contrainte `NetworkType.CONNECTED` avec `BackoffPolicy.EXPONENTIAL`. |
| **Permissions Runtime** | **Machine à états `PermissionFlow` custom** | [`PermissionFlow.kt`](app/src/main/java/corsica/universita/objetscampus/ui/permissions/PermissionFlow.kt) gère les 4 états (`NotAsked`, `Granted`, `DeniedOnce`, `DeniedForever`), affiche un dialogue d'explication (*rationale*), propose un bouton vers les Paramètres système et supporte la localisation approximative d'Android 12+ (`requireAll = false`). |
| **Notifications & Push** | **Firebase Cloud Messaging (FCM) + Canaux Android 8+** | [`Notifications.kt`](app/src/main/java/corsica/universita/objetscampus/notif/Notifications.kt) déclare 2 canaux dès `CampusApp.onCreate()` : `sync` (`IMPORTANCE_LOW`) et `hub` (`IMPORTANCE_HIGH`). `PendingIntent` avec `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT` et `requestCode = itemId.hashCode()` pour cibler la fiche exacte. |
| **Deep Links Vérifiés** | **Android App Links (`autoVerify="true"`)** | Vérification cryptographique SHA-256 via [`TP/.well-known/assetlinks.json`](../TP/.well-known/assetlinks.json) permettant d'ouvrir toute URL d'objet de la PWA directement dans l'application Android native. |
| **Build Release** | **R8 (`isMinifyEnabled = true`, `isShrinkResources = true`)** | Règles [`proguard-rules.pro`](app/proguard-rules.pro) préservant les DTO `kotlinx.serialization`, les entités Room et les constantes Analytics en dur. |

### 📊 Plan de Marquage Analytics & RGPD (Annexe 4)
Implémenté dans [`AnalyticsTracker.kt`](app/src/main/java/corsica/universita/objetscampus/analytics/AnalyticsTracker.kt) et [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml), avec **double envoi** vers **Firebase Analytics** et **Umami (`POST /api/send` + file d'attente hors-ligne)** :
- **Conformité RGPD** :
  - `firebase_analytics_collection_enabled = false` par défaut dans le Manifest.
  - Permission publicitaire `com.google.android.gms.permission.AD_ID` explicitement supprimée via `tools:node="remove"`.
  - Bannière de consentement affichée au lancement : aucune donnée n'est envoyée tant que l'utilisateur n'a pas accepté.
  - **Zéro PII** : ni titre libre, ni description, ni email, ni coordonnée GPS exacte ne sont transmis.
- **Suivi d'écrans manuel (`screen_view`)** avec constantes en dur survivant à l'obfuscation R8 : `items_list`, `item_declare`, `item_detail`, `barcode_scanner`.
- **Les 3 événements métier (`snake_case` au passé)** :
  1. **Activation** : `item_declared` (`category`, `offline` `0/1`, `has_photo` `0/1`, `has_gps` `0/1`, `has_barcode` `0/1`)
  2. **Rétention** : `item_returned` (`category`, `conflict_resolved` `0/1`)
  3. **Fonctionnalité native** : `barcode_scanned` (`result` = `"ok"` | `"denied"` | `"cancelled"`, `format` = `"QR_CODE"` | `"EAN_13"` | ...)

---

## 🚀 3. Comment lancer l'application

### Option A — Installer directement l'APK (le plus rapide)
1. Téléchargez le fichier APK depuis la page [**GitHub Releases**](https://github.com/gwenolelt/pwa-campus-objets-trouves-android/releases/latest) (ou [ici sur le dépôt principal](https://github.com/gwenolelt/pwa-campus-objets-trouves/releases/latest)).
2. **Sur un smartphone Android** : ouvrez le fichier `.apk` téléchargé et autorisez l'installation.
3. **Sur un émulateur Android** : glissez-déposez simplement le fichier `.apk` sur la fenêtre de l'émulateur, ou exécutez :
   ```powershell
   adb install -r app-release.apk
   ```

### Option B — Lancer depuis Android Studio
1. **Prérequis** :
   - **Android Studio** (Ladybug / Meerkat ou plus récent)
   - **JDK 17** (intégré à Android Studio : *Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK = jbr-17*)
   - **Android SDK 36** (minSdk = 26 / Android 8.0+)
2. Ouvrez Android Studio et sélectionnez **Open** → choisissez le dossier **`android/`**.
3. Laissez **Gradle Sync** télécharger les dépendances (le projet inclut déjà un fichier `google-services.json` de démonstration et un `debug.keystore` versionné pour fonctionner immédiatement sans configuration externe).
4. Sélectionnez un émulateur (API 26+) ou un appareil physique en débogage USB, puis cliquez sur **Run `▶` (`app`)**.

### Option C — Compiler et lancer en ligne de commande (`gradlew`)

```powershell
# Se placer dans le dossier android
cd android

# 1. Compiler l'APK Debug et exécuter les tests unitaires
.\gradlew.bat assembleDebug testDebugUnitTest

# 2. Installer l'APK Debug sur l'appareil / émulateur connecté
.\gradlew.bat installDebug

# 3. Compiler l'APK Release signé et minifié par R8
.\gradlew.bat assembleRelease
```
Les APK générés se trouvent dans :
- **Debug** : `app/build/outputs/apk/debug/app-debug.apk`
- **Release** : `app/build/outputs/apk/release/app-release.apk`

---

## 🧪 Commandes `adb` de vérification rapide (TP)

### 1. Tester la survie à la mort du processus (`SavedStateHandle` - TD A3)
```powershell
# 1. Ouvrir l'écran "Déclarer un objet", saisir un titre sans valider
# 2. Mettre l'application en arrière-plan (bouton Home)
adb shell am kill corsica.universita.objetscampus
# 3. Rouvrir l'application depuis les Récents -> le brouillon est intact !
```

### 2. Tester les App Links (`assetlinks.json` - TD A8)
```powershell
adb shell pm get-app-links corsica.universita.objetscampus
adb shell am start -a android.intent.action.VIEW -d "https://timely-hamster-5836fc.netlify.app/?item=seed-1"
```

### 3. Tester le DebugView Analytics & Umami en temps réel (Annexe 4)
```powershell
adb shell setprop debug.firebase.analytics.app corsica.universita.objetscampus
adb logcat -s CampusAnalytics Carnet SyncWorker FCM
```
