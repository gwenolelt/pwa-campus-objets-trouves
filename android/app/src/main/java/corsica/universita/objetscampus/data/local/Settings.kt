package corsica.universita.objetscampus.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class Settings(private val context: Context) {
    private val fcmTokenKey = stringPreferencesKey("fcm_token")
    private val analyticsConsentKey = booleanPreferencesKey("analytics_consent")
    private val umamiWebsiteIdKey = stringPreferencesKey("umami_website_id")
    private val umamiEndpointKey = stringPreferencesKey("umami_endpoint")

    val fcmToken: Flow<String?> = context.dataStore.data.map { it[fcmTokenKey] }
    val umamiWebsiteId: Flow<String> = context.dataStore.data.map { it[umamiWebsiteIdKey] ?: "" }
    val umamiEndpoint: Flow<String> = context.dataStore.data.map {
        it[umamiEndpointKey] ?: DEFAULT_UMAMI_ENDPOINT
    }

    /**
     * Consentement RGPD Analytics (Annexe 4) :
     * - null : l'utilisateur n'a pas encore fait de choix (banniere affichee)
     * - true : collecte activee
     * - false : collecte refusee
     */
    val analyticsConsent: Flow<Boolean?> = context.dataStore.data.map { it[analyticsConsentKey] }

    suspend fun saveFcmToken(token: String) {
        context.dataStore.edit { it[fcmTokenKey] = token }
    }

    suspend fun setAnalyticsConsent(granted: Boolean) {
        context.dataStore.edit { it[analyticsConsentKey] = granted }
    }

    suspend fun saveUmamiConfig(websiteId: String, endpoint: String = DEFAULT_UMAMI_ENDPOINT) {
        context.dataStore.edit {
            it[umamiWebsiteIdKey] = websiteId.trim()
            it[umamiEndpointKey] = endpoint.trim().ifBlank { DEFAULT_UMAMI_ENDPOINT }
        }
    }

    companion object {
        const val DEFAULT_UMAMI_ENDPOINT = "https://cloud.umami.is/api/send"
    }

    fun wasPermissionAsked(permissionId: String): Flow<Boolean> {
        val key = booleanPreferencesKey("perm_asked_$permissionId")
        return context.dataStore.data.map { it[key] ?: false }
    }

    suspend fun markPermissionAsked(permissionId: String) {
        val key = booleanPreferencesKey("perm_asked_$permissionId")
        context.dataStore.edit { it[key] = true }
    }
}
