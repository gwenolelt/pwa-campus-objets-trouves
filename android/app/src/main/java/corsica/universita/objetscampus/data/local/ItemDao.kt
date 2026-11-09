package corsica.universita.objetscampus.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY pinned DESC, createdAt DESC")
    fun all(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items ORDER BY pinned DESC, createdAt DESC")
    suspend fun getAllOnce(): List<ItemEntity>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: Long): ItemEntity?

    @Query("SELECT * FROM items WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): ItemEntity?

    @Query("SELECT * FROM items WHERE synced = 0")
    suspend fun unsynced(): List<ItemEntity>

    @Query("SELECT COUNT(*) FROM items")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(item: ItemEntity): Long

    @Upsert
    suspend fun upsertAll(items: List<ItemEntity>)

    @Query("UPDATE items SET synced = 1, version = :version, remoteId = :remoteId WHERE id = :id")
    suspend fun markSynced(id: Long, version: Int, remoteId: String)

    @Delete
    suspend fun delete(item: ItemEntity)
}
