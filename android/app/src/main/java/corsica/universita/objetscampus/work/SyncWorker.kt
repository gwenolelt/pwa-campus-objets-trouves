package corsica.universita.objetscampus.work

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import corsica.universita.objetscampus.data.ItemsRepository
import corsica.universita.objetscampus.notif.Notifications
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Worker de synchronisation en arriere-plan (TD A5 - Jour 4).
 * Remplace Background Sync API (limite a Chromium sur PWA) par l'ordonnanceur systeme Android WorkManager.
 * - Persiste en SQLite systeme a travers le kill de l'app et le reboot du telephone.
 * - Contrainte NetworkType.CONNECTED + Backoff exponentiel.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repo = ItemsRepository(applicationContext)
        return try {
            val syncedCount = repo.syncPending()
            Log.d(TAG, "doWork: $syncedCount objet(s) synchronise(s)")
            if (syncedCount > 0) {
                Notifications.showSyncDone(applicationContext, syncedCount)
            }
            Result.success()
        } catch (e: IOException) {
            Log.w(TAG, "Erreur reseau transitoire, nouvel essai planifie par WorkManager", e)
            Result.retry()
        } catch (e: HttpException) {
            Log.w(TAG, "Erreur HTTP ${e.code()}", e)
            if (e.code() in 500..599) Result.retry() else Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "Erreur inattendue lors de la synchronisation", e)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        const val UNIQUE_WORK_NAME = "sync"

        fun schedule(context: Context) {
            val req = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, req)
        }
    }
}
