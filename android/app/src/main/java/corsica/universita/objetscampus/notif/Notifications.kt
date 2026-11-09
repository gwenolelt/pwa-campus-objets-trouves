package corsica.universita.objetscampus.notif

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import corsica.universita.objetscampus.MainActivity
import corsica.universita.objetscampus.R

/**
 * Gestion des canaux et notifications locales / push FCM (TD A6 - Jour 5).
 * - Deux canaux distincts crees au demarrage (`sync` IMPORTANCE_LOW, `hub` IMPORTANCE_HIGH).
 * - PendingIntent avec FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT et requestCode = itemId.hashCode()
 *   pour ne jamais ecraser l'extra `itemId` entre deux notifications.
 */
object Notifications {
    const val CHANNEL_SYNC = "sync"
    const val CHANNEL_HUB = "hub"
    const val EXTRA_ITEM_ID = "itemId"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SYNC,
                "Synchronisation hors-ligne",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications discrètes lorsque les objets déclarés hors-ligne sont synchronisés."
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_HUB,
                "Alertes Objets Trouvés Campus",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertes lorsqu'un nouvel objet trouvé correspond ou est signalé sur le campus."
            }
        )
    }

    fun canPostNotifications(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return true
    }

    fun showSyncDone(context: Context, count: Int) {
        if (!canPostNotifications(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_SYNC)
            .setSmallIcon(R.drawable.ic_tile_campus)
            .setContentTitle("Synchronisation terminée")
            .setContentText("$count objet(s) trouvé(s) envoyé(s) au serveur du campus")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(1001, notif)
        }
    }

    fun showItemNotification(
        context: Context,
        itemId: String,
        title: String,
        body: String
    ) {
        if (!canPostNotifications(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ITEM_ID, itemId)
        }
        val pi = PendingIntent.getActivity(
            context,
            itemId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_HUB)
            .setSmallIcon(R.drawable.ic_tile_campus)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(itemId.hashCode(), notif)
        }
    }
}
