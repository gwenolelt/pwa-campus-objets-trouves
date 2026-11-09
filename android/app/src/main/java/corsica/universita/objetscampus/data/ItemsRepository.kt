package corsica.universita.objetscampus.data

import android.content.Context
import corsica.universita.objetscampus.data.local.CampusDatabase
import corsica.universita.objetscampus.data.local.ItemDao
import corsica.universita.objetscampus.data.local.ItemEntity
import corsica.universita.objetscampus.data.local.PhotoStore
import corsica.universita.objetscampus.data.location.Fix
import corsica.universita.objetscampus.data.remote.Network
import corsica.universita.objetscampus.data.remote.RemoteItemDto
import corsica.universita.objetscampus.data.remote.SyncApi
import corsica.universita.objetscampus.widget.CampusLostFoundWidget
import corsica.universita.objetscampus.work.SyncWorker
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class ConflictState(
    val localItem: ItemEntity,
    val serverItem: RemoteItemDto?
)

sealed interface ReturnResult {
    data object Success : ReturnResult
    data object QueuedOffline : ReturnResult
    data class Conflict412(val conflict: ConflictState) : ReturnResult
}

/**
 * Source de verite unique (Offline-First par construction - Jour 4 & Jour 5).
 * - L'UI observe exclusivement `items: Flow<List<ItemEntity>>` issu de Room.
 * - Toute ecriture modifie d'abord Room (`synced = false`) puis planifie `SyncWorker`.
 */
class ItemsRepository(
    private val appContext: Context,
    private val dao: ItemDao = CampusDatabase.get(appContext).items(),
    private val api: SyncApi = Network.create(appContext),
    val photos: PhotoStore = PhotoStore(appContext)
) {
    val items: Flow<List<ItemEntity>> = dao.all()

    suspend fun ensureSeeded() {
        if (dao.count() == 0) {
            dao.upsertAll(CampusDatabase.defaultSeedItems())
            CampusLostFoundWidget.updateAllWidgets(appContext)
        }
    }

    suspend fun getById(id: Long): ItemEntity? = dao.get(id)

    suspend fun getByRemoteIdOrLocalId(rawId: String): ItemEntity? {
        dao.getByRemoteId(rawId)?.let { return it }
        rawId.toLongOrNull()?.let { dao.get(it)?.let { item -> return item } }
        return null
    }

    suspend fun add(
        title: String,
        category: String,
        description: String,
        dateFound: String,
        locationFound: String,
        depositLocation: String,
        lat: Double? = null,
        lng: Double? = null,
        accuracy: Float? = null,
        photoPath: String? = null,
        barcodeValue: String? = null
    ): Long {
        val now = System.currentTimeMillis()
        val entity = ItemEntity(
            remoteId = now.toString(),
            title = title.trim(),
            category = category,
            description = description.trim(),
            dateFound = dateFound.ifBlank { LocalDate.now().toString() },
            locationFound = locationFound.trim(),
            depositLocation = depositLocation,
            status = ItemEntity.STATUS_ACTIVE,
            dateReturned = null,
            version = 1,
            createdAt = now,
            pinned = false,
            synced = false,
            lat = lat,
            lng = lng,
            accuracy = accuracy,
            photoPath = photoPath,
            barcodeValue = barcodeValue?.takeIf { it.isNotBlank() }
        )
        val id = dao.upsert(entity)
        SyncWorker.schedule(appContext)
        CampusLostFoundWidget.updateAllWidgets(appContext)
        return id
    }

    suspend fun togglePin(item: ItemEntity) {
        dao.upsert(item.copy(pinned = !item.pinned))
        CampusLostFoundWidget.updateAllWidgets(appContext)
    }

    suspend fun attachLocation(item: ItemEntity, fix: Fix) {
        dao.upsert(
            item.copy(
                lat = fix.lat,
                lng = fix.lng,
                accuracy = fix.accuracy
            )
        )
    }

    suspend fun attachPhoto(item: ItemEntity, newPath: String) {
        photos.delete(item.photoPath)
        dao.upsert(item.copy(photoPath = newPath))
    }

    suspend fun attachBarcode(item: ItemEntity, barcode: String) {
        dao.upsert(item.copy(barcodeValue = barcode))
    }

    suspend fun delete(item: ItemEntity) {
        photos.delete(item.photoPath)
        dao.delete(item)
        CampusLostFoundWidget.updateAllWidgets(appContext)
    }

    /**
     * Marque un objet comme restitue avec gestion d'ETag / If-Match et conflit HTTP 412
     * (exactement comme dans la PWA `TP/app.js`).
     */
    suspend fun markAsReturned(
        item: ItemEntity,
        simulateConflict: Boolean = false
    ): ReturnResult {
        val today = LocalDate.now().toString()
        val updatedLocal = item.copy(
            status = ItemEntity.STATUS_RETURNED,
            dateReturned = today,
            synced = false
        )
        dao.upsert(updatedLocal)
        CampusLostFoundWidget.updateAllWidgets(appContext)

        val remoteId = updatedLocal.remoteId.ifBlank { updatedLocal.id.toString() }
        val expectedVersion = if (simulateConflict) 0 else updatedLocal.version

        return try {
            val response = api.updateItem(
                id = remoteId,
                ifMatchVersion = "\"$expectedVersion\"",
                item = updatedLocal.toDto()
            )
            when {
                response.isSuccessful -> {
                    val body = response.body()
                    val newVersion = body?.version ?: (updatedLocal.version + 1)
                    dao.markSynced(updatedLocal.id, newVersion, remoteId)
                    ReturnResult.Success
                }
                response.code() == 412 || simulateConflict -> {
                    ReturnResult.Conflict412(
                        ConflictState(
                            localItem = updatedLocal,
                            serverItem = RemoteItemDto(
                                id = remoteId,
                                title = item.title,
                                category = item.category,
                                description = item.description + "\n[Modifié par un autre agent sur le campus]",
                                dateFound = item.dateFound,
                                locationFound = item.locationFound,
                                depositLocation = item.depositLocation,
                                status = item.status,
                                dateReturned = item.dateReturned,
                                version = item.version + 1
                            )
                        )
                    )
                }
                else -> {
                    SyncWorker.schedule(appContext)
                    ReturnResult.QueuedOffline
                }
            }
        } catch (_: Exception) {
            if (simulateConflict) {
                return ReturnResult.Conflict412(
                    ConflictState(
                        localItem = updatedLocal,
                        serverItem = RemoteItemDto(
                            id = remoteId,
                            title = item.title,
                            category = item.category,
                            description = item.description + "\n[Version serveur concurrente v${item.version + 1}]",
                            dateFound = item.dateFound,
                            locationFound = item.locationFound,
                            depositLocation = item.depositLocation,
                            status = ItemEntity.STATUS_ACTIVE,
                            dateReturned = null,
                            version = item.version + 1
                        )
                    )
                )
            }
            SyncWorker.schedule(appContext)
            ReturnResult.QueuedOffline
        }
    }

    /**
     * Resolution du conflit HTTP 412 :
     * - forceLocal = true : force la version locale (version + 2) et synchronise
     * - forceLocal = false : accepte la version serveur
     */
    suspend fun resolveConflict(conflict: ConflictState, forceLocal: Boolean) {
        val local = conflict.localItem
        val server = conflict.serverItem
        if (forceLocal) {
            val newVersion = (server?.version ?: local.version) + 1
            val forced = local.copy(
                version = newVersion,
                synced = true
            )
            dao.upsert(forced)
            runCatching {
                val remoteId = forced.remoteId.ifBlank { forced.id.toString() }
                api.updateItem(remoteId, "\"${server?.version ?: local.version}\"", forced.toDto())
            }
        } else if (server != null) {
            dao.upsert(
                local.copy(
                    title = server.title,
                    category = server.category,
                    description = server.description,
                    locationFound = server.locationFound,
                    depositLocation = server.depositLocation,
                    status = server.status,
                    dateReturned = server.dateReturned,
                    version = server.version,
                    synced = true
                )
            )
        }
        CampusLostFoundWidget.updateAllWidgets(appContext)
    }

    /**
     * Synchronise l'Outbox locale (`synced = 0`) puis rafraichit depuis `/api/items`.
     * Appele par `SyncWorker` en arriere-plan ou manuellement (Pull-to-refresh / bouton Sync).
     */
    suspend fun syncPending(): Int {
        val pending = dao.unsynced()
        var pushedCount = 0

        for (item in pending) {
            val remoteId = item.remoteId.ifBlank { item.id.toString() }
            val dto = item.toDto()
            val syncedDto = if (item.status == ItemEntity.STATUS_RETURNED && item.version >= 1) {
                val resp = api.updateItem(remoteId, "\"${item.version}\"", dto)
                if (resp.isSuccessful) resp.body() else api.pushNew(dto)
            } else {
                api.pushNew(dto)
            }
            val finalVersion = syncedDto?.version ?: (item.version + 1)
            val finalRemoteId = syncedDto?.id?.ifBlank { remoteId } ?: remoteId
            dao.markSynced(item.id, finalVersion, finalRemoteId)
            pushedCount++
        }

        // Rafraichissement depuis le serveur vers Room (en preservant les donnees capteurs locales)
        runCatching {
            val remoteList = api.fetchAll()
            for (remote in remoteList) {
                if (remote.id.isBlank() || remote.title.isBlank()) continue
                val existing = dao.getByRemoteId(remote.id)
                if (existing == null) {
                    dao.upsert(
                        ItemEntity(
                            remoteId = remote.id,
                            title = remote.title,
                            category = remote.category,
                            description = remote.description,
                            dateFound = remote.dateFound,
                            locationFound = remote.locationFound,
                            depositLocation = remote.depositLocation,
                            status = remote.status,
                            dateReturned = remote.dateReturned,
                            version = remote.version,
                            createdAt = if (remote.createdAt > 0) remote.createdAt else System.currentTimeMillis(),
                            pinned = false,
                            synced = true
                        )
                    )
                } else if (existing.synced && remote.version >= existing.version) {
                    dao.upsert(
                        existing.copy(
                            title = remote.title,
                            category = remote.category,
                            description = remote.description,
                            dateFound = remote.dateFound,
                            locationFound = remote.locationFound,
                            depositLocation = remote.depositLocation,
                            status = remote.status,
                            dateReturned = remote.dateReturned,
                            version = remote.version,
                            synced = true
                        )
                    )
                }
            }
        }

        CampusLostFoundWidget.updateAllWidgets(appContext)
        return pushedCount
    }

    private fun ItemEntity.toDto(): RemoteItemDto = RemoteItemDto(
        id = remoteId.ifBlank { id.toString() },
        title = title,
        category = category,
        description = description,
        dateFound = dateFound,
        locationFound = locationFound,
        depositLocation = depositLocation,
        photo = null,
        status = status,
        dateReturned = dateReturned,
        version = version,
        synced = true,
        createdAt = createdAt
    )
}
