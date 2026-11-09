package corsica.universita.objetscampus

import android.app.Application
import corsica.universita.objetscampus.notif.Notifications

class CampusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Creation des canaux de notification des le demarrage (TD A6 - Jour 5)
        Notifications.createChannels(this)
    }
}
