package corsica.universita.objetscampus.tile

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import corsica.universita.objetscampus.MainActivity

/**
 * Fonctionnalite Native 3 (impossible en PWA) :
 * Tuile de reglages rapides systeme (Quick Settings Tile) dans le volet de notifications Android.
 * Permet de declarer un objet trouve en 1 geste depuis n'importe quel ecran du systeme.
 */
class QuickDeclareTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = "Objet Trouvé"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = "Déclarer / Scanner"
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://timely-hamster-5836fc.netlify.app/?action=declare"),
            this,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(
                this,
                2001,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
