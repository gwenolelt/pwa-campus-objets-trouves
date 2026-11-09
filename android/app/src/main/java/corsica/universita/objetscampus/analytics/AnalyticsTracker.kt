package corsica.universita.objetscampus.analytics

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent

/**
 * Plan de marquage Analytics conforme a l'Annexe 4 :
 * - Consentement RGPD prealable (`setAnalyticsCollectionEnabled`).
 * - Suivi manuel des ecrans Compose avec des constantes en dur (survit a R8).
 * - 3 evenements metier en `snake_case` au passe, sans aucune PII (ni titre libre, ni email, ni GPS) :
 *   1. Activation : `item_declared` (parametres : `category`, `offline`, `has_photo`, `has_gps`, `has_barcode`)
 *   2. Retention : `item_returned` (parametres : `category`, `conflict_resolved`) + `notification_opened`
 *   3. Fonctionnalite native : `barcode_scanned` (parametres : `result`, `format`)
 */
class AnalyticsTracker(context: Context) {
    private val fa: FirebaseAnalytics? = runCatching {
        Firebase.analytics
    }.getOrNull()

    fun setCollectionEnabled(enabled: Boolean) {
        Log.d(TAG, "setAnalyticsCollectionEnabled($enabled)")
        runCatching {
            fa?.setAnalyticsCollectionEnabled(enabled)
        }
    }

    fun logScreenView(screenName: String, screenClass: String) {
        Log.d(TAG, "screen_view: screen_name=$screenName, screen_class=$screenClass")
        runCatching {
            fa?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
                param(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                param(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass)
            }
        }
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
        runCatching {
            fa?.logEvent(EVENT_ITEM_DECLARED) {
                param("category", category.take(40))
                param("offline", if (offline) 1L else 0L)
                param("has_photo", if (hasPhoto) 1L else 0L)
                param("has_gps", if (hasGps) 1L else 0L)
                param("has_barcode", if (hasBarcode) 1L else 0L)
            }
        }
    }

    /**
     * Evenement 2 (Retention) : un objet trouve a ete marque comme restitue a son proprietaire.
     */
    fun logItemReturned(category: String, conflictResolved: Boolean) {
        Log.d(TAG, "event item_returned: category=$category, conflict_resolved=$conflictResolved")
        runCatching {
            fa?.logEvent(EVENT_ITEM_RETURNED) {
                param("category", category.take(40))
                param("conflict_resolved", if (conflictResolved) 1L else 0L)
            }
        }
    }

    /**
     * Evenement 3 (Fonctionnalite native) : scan d'un code-barres / QR code campus via CameraX + ML Kit.
     * Trace a la fois le succes ("ok"), le refus de permission ("denied") et l'annulation ("cancelled").
     */
    fun logBarcodeScanned(result: String, format: String = "none") {
        Log.d(TAG, "event barcode_scanned: result=$result, format=$format")
        runCatching {
            fa?.logEvent(EVENT_BARCODE_SCANNED) {
                param("result", result)
                param("format", format)
            }
        }
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
