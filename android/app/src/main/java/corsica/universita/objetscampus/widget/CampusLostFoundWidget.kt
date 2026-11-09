package corsica.universita.objetscampus.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import corsica.universita.objetscampus.MainActivity
import corsica.universita.objetscampus.data.local.CampusDatabase
import corsica.universita.objetscampus.data.local.ItemEntity

/**
 * Fonctionnalite Native 2 (impossible en PWA) :
 * Widget d'ecran d'accueil Android (Jetpack Glance AppWidget) connecte a Room.
 * Permet de voir d'un coup d'oeil sur l'ecran d'accueil le nombre d'objets a recuperer,
 * le dernier objet trouve sur le campus, et de lancer une declaration ou un scan QR en 1 tap.
 */
class CampusLostFoundWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = CampusDatabase.get(context).items()
        val allItems = runCatching { dao.getAllOnce() }.getOrDefault(emptyList())
        val activeCount = allItems.count { it.status == ItemEntity.STATUS_ACTIVE }
        val returnedCount = allItems.count { it.status == ItemEntity.STATUS_RETURNED }
        val latestActive = allItems.firstOrNull { it.status == ItemEntity.STATUS_ACTIVE }

        val openMainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val declareIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://timely-hamster-5836fc.netlify.app/?action=declare"),
            context,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val scanIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://timely-hamster-5836fc.netlify.app/?action=scan"),
            context,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        provideContent {
            GlanceTheme {
                WidgetContent(
                    activeCount = activeCount,
                    returnedCount = returnedCount,
                    latestTitle = latestActive?.title ?: "Aucun objet en attente",
                    latestLocation = latestActive?.locationFound ?: "Campus Università di Corsica",
                    openMainIntent = openMainIntent,
                    declareIntent = declareIntent,
                    scanIntent = scanIntent
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(
        activeCount: Int,
        returnedCount: Int,
        latestTitle: String,
        latestLocation: String,
        openMainIntent: Intent,
        declareIntent: Intent,
        scanIntent: Intent
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xFF1E3A8A)))
                .cornerRadius(16.dp)
                .padding(12.dp)
                .clickable(actionStartActivity(openMainIntent))
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🔍 Objets Trouvés Campus",
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                    text = "$activeCount à récupérer · $returnedCount restitués",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF93C5FD)),
                        fontSize = 11.sp
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            Text(
                text = "Dernier : $latestTitle",
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
            Text(
                text = "📍 $latestLocation",
                style = TextStyle(
                    color = ColorProvider(Color(0xFFDBEAFE)),
                    fontSize = 11.sp
                ),
                maxLines = 1
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    text = "➕ Déclarer",
                    onClick = actionStartActivity(declareIntent)
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Button(
                    text = "📷 Scanner QR",
                    onClick = actionStartActivity(scanIntent)
                )
            }
        }
    }

    companion object {
        suspend fun updateAllWidgets(context: Context) {
            runCatching {
                CampusLostFoundWidget().updateAll(context)
            }
        }
    }
}

class CampusLostFoundWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CampusLostFoundWidget()
}
