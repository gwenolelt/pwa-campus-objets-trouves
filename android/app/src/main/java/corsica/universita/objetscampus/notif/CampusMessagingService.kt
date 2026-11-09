package corsica.universita.objetscampus.notif

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import corsica.universita.objetscampus.data.local.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Service Firebase Cloud Messaging (TD A6 - Jour 5).
 * Recoit les messages push meme lorsque l'application est tuee (contrairement au Web Push PWA
 * plus restreint par la batterie / le navigateur).
 */
class CampusMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        Log.d("FCM", "Nouveau token FCM: $token")
        serviceScope.launch {
            Settings(applicationContext).saveFcmToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val itemId = message.data["itemId"] ?: "seed-1"
        val title = message.data["title"]
            ?: message.notification?.title
            ?: "Nouvel objet trouvé sur le campus"
        val body = message.data["body"]
            ?: message.notification?.body
            ?: "Appuyez pour consulter la fiche de l'objet."

        Log.d("FCM", "Message push recu pour itemId=$itemId : $title")
        Notifications.showItemNotification(
            context = applicationContext,
            itemId = itemId,
            title = title,
            body = body
        )
    }
}
