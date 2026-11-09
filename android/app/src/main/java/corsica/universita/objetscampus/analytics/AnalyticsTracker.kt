package corsica.universita.objetscampus.analytics

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent
import corsica.universita.objetscampus.BuildConfig
import corsica.universita.objetscampus.data.local.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Plan de marquage Analytics conforme a l'Annexe 4 (Firebase Analytics + Umami HTTP API) :
 * - Supporte simultanement :
 *   1. Firebase Analytics (DebugView / Logcat)
 *   2. Umami Cloud / auto-heberge via `POST /api/send` + file d'attente hors-ligne (Annexe 4 diapo 7)
 * - Consentement RGPD prealable (`setCollectionEnabled`).
 * - Suivi manuel des ecrans Compose avec des constantes en dur (survit a R8).
 * - 3 evenements metier en `snake_case` au passe, sans aucune PII :
 *   1. Activation : `item_declared` (parametres : `category`, `offline`, `has_photo`, `has_gps`, `has_barcode`)
 *   2. Retention : `item_returned` (parametres : `category`, `conflict_resolved`)
 *   3. Fonctionnalite native : `barcode_scanned` (parametres : `result`, `format`)
 */
class AnalyticsTracker(context: Context) {
    private val appContext = context.applicationContext
    private val settings = Settings(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient()

    // File d'attente hors-ligne pour Umami (Annexe 4 diapo 7 : "la file hors ligne est a ecrire : l'outbox du Jour 5")
    private val umamiOfflineQueue = ConcurrentLinkedQueue<String>()

    @Volatile
    private var collectionEnabled: Boolean = true

    @Volatile
    private var currentScreenName: String = SCREEN_ITEMS_LIST

    private val fa: FirebaseAnalytics? = runCatching {
        Firebase.analytics
    }.getOrNull()

    fun setCollectionEnabled(enabled: Boolean) {
        collectionEnabled = enabled
        Log.d(TAG, "setAnalyticsCollectionEnabled($enabled)")
        runCatching {
            fa?.setAnalyticsCollectionEnabled(enabled)
        }
        if (!enabled) {
            umamiOfflineQueue.clear()
        }
    }

    fun logScreenView(screenName: String, screenClass: String) {
        currentScreenName = screenName
        Log.d(TAG, "screen_view: screen_name=$screenName, screen_class=$screenClass")
        if (!collectionEnabled) return

        runCatching {
            fa?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
                param(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                param(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass)
            }
        }

        // Envoi a Umami (pageview sur /android/<screenName>)
        sendToUmami(
            screenName = screenName,
            eventName = null,
            eventData = null
        )
    }

    /**
     * Evenement 1 (Activation) : un objet trouve a ete declare.
     * Note RGPD : on n'envoie JAMAIS le titre, la description ni la position GPS exacte.
     */
    fun logItemDeclared(
        category: String,
        offline: Boolean,
        hasPhoto: Boolean,
        hasGps: Boolean,
        hasBarcode: Boolean
    ) {
        Log.d(
            TAG,
            "event item_declared: category=$category, offline=$offline, has_photo=$hasPhoto, has_gps=$hasGps, has_barcode=$hasBarcode"
        )
        if (!collectionEnabled) return

        runCatching {
            fa?.logEvent(EVENT_ITEM_DECLARED) {
                param("category", category.take(40))
                param("offline", if (offline) 1L else 0L)
                param("has_photo", if (hasPhoto) 1L else 0L)
                param("has_gps", if (hasGps) 1L else 0L)
                param("has_barcode", if (hasBarcode) 1L else 0L)
            }
        }

        sendToUmami(
            screenName = currentScreenName,
            eventName = EVENT_ITEM_DECLARED,
            eventData = buildJsonObject {
                put("category", category.take(40))
                put("offline", if (offline) 1 else 0)
                put("has_photo", if (hasPhoto) 1 else 0)
                put("has_gps", if (hasGps) 1 else 0)
                put("has_barcode", if (hasBarcode) 1 else 0)
            }
        )
    }

    /**
     * Evenement 2 (Retention) : un objet trouve a ete marque comme restitue a son proprietaire.
     */
    fun logItemReturned(category: String, conflictResolved: Boolean) {
        Log.d(TAG, "event item_returned: category=$category, conflict_resolved=$conflictResolved")
        if (!collectionEnabled) return

        runCatching {
            fa?.logEvent(EVENT_ITEM_RETURNED) {
                param("category", category.take(40))
                param("conflict_resolved", if (conflictResolved) 1L else 0L)
            }
        }

        sendToUmami(
            screenName = currentScreenName,
            eventName = EVENT_ITEM_RETURNED,
            eventData = buildJsonObject {
                put("category", category.take(40))
                put("conflict_resolved", if (conflictResolved) 1 else 0)
            }
        )
    }

    /**
     * Evenement 3 (Fonctionnalite native) : scan d'un code-barres / QR code campus via CameraX + ML Kit.
     * Trace a la fois le succes ("ok"), le refus de permission ("denied") et l'annulation ("cancelled").
     */
    fun logBarcodeScanned(result: String, format: String = "none") {
        Log.d(TAG, "event barcode_scanned: result=$result, format=$format")
        if (!collectionEnabled) return

        runCatching {
            fa?.logEvent(EVENT_BARCODE_SCANNED) {
                param("result", result)
                param("format", format)
            }
        }

        sendToUmami(
            screenName = currentScreenName,
            eventName = EVENT_BARCODE_SCANNED,
            eventData = buildJsonObject {
                put("result", JsonPrimitive(result))
                put("format", JsonPrimitive(format))
            }
        )
    }

    /**
     * Envoi HTTP POST /api/send vers Umami avec file d'attente hors-ligne (Annexe 4 diapo 7).
     */
    private fun sendToUmami(
        screenName: String,
        eventName: String?,
        eventData: JsonObject?
    ) {
        scope.launch {
            val websiteId = settings.umamiWebsiteId.first().trim()
            val endpoint = settings.umamiEndpoint.first().trim()
                .ifBlank { Settings.DEFAULT_UMAMI_ENDPOINT }

            if (websiteId.isBlank()) {
                Log.d(TAG, "Umami non configure (aucun Website ID renseigne dans la cloche 🔔)")
                return@launch
            }

            val bodyJson = buildJsonObject {
                put("type", "event")
                put(
                    "payload",
                    buildJsonObject {
                        put("website", websiteId)
                        put("hostname", BuildConfig.PWA_HOST)
                        put("screen", "1080x2400")
                        put("language", "fr-FR")
                        put("title", "Android · $screenName")
                        put("url", "/android/$screenName")
                        if (!eventName.isNullOrBlank()) {
                            put("name", eventName)
                        }
                        if (eventData != null) {
                            put("data", eventData)
                        }
                    }
                )
            }.toString()

            umamiOfflineQueue.add(bodyJson)
            flushUmamiQueue(endpoint)
        }
    }

    fun flushUmamiNow() {
        scope.launch {
            val endpoint = settings.umamiEndpoint.first().trim()
                .ifBlank { Settings.DEFAULT_UMAMI_ENDPOINT }
            flushUmamiQueue(endpoint)
        }
    }

    private fun flushUmamiQueue(endpoint: String) {
        while (true) {
            val nextPayload = umamiOfflineQueue.peek() ?: break
            val success = postUmamiPayload(endpoint, nextPayload)
            if (success) {
                umamiOfflineQueue.poll()
            } else {
                Log.w(
                    TAG,
                    "Umami hors-ligne : evenement conserve dans la file (${umamiOfflineQueue.size} en attente)"
                )
                break
            }
        }
    }

    private fun postUmamiPayload(endpoint: String, jsonPayload: String): Boolean {
        return runCatching {
            val req = Request.Builder()
                .url(endpoint)
                .header("Content-Type", "application/json")
                // Umami exige un User-Agent standard pour ne pas filtrer la requete comme un bot
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                )
                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                Log.d(TAG, "Umami POST $endpoint -> HTTP ${resp.code}")
                resp.isSuccessful
            }
        }.getOrDefault(false)
    }

    companion object {
        private const val TAG = "CampusAnalytics"

        // Noms d'ecrans en dur (survivent a l'obfuscation R8)
        const val SCREEN_ITEMS_LIST = "items_list"
        const val SCREEN_ITEM_DETAIL = "item_detail"
        const val SCREEN_ITEM_DECLARE = "item_declare"
        const val SCREEN_BARCODE_SCANNER = "barcode_scanner"

        // Noms d'evenements en snake_case au passe (Annexe 4)
        const val EVENT_ITEM_DECLARED = "item_declared"
        const val EVENT_ITEM_RETURNED = "item_returned"
        const val EVENT_BARCODE_SCANNED = "barcode_scanned"
    }
}
