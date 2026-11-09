package corsica.universita.objetscampus.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ItemEntity::class],
    version = 4,
    exportSchema = true
)
abstract class CampusDatabase : RoomDatabase() {
    abstract fun items(): ItemDao

    companion object {
        // Migration v1 -> v2 : ajout de la colonne pinned (TD A4)
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Migration v2 -> v3 : ajout de la colonne synced pour l'Outbox offline-first (TD A5)
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Migration v3 -> v4 : ajout des coordonnees GPS, du chemin photo interne et du code-barres/QR scanne (TD A7 + Natif)
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN lat REAL")
                db.execSQL("ALTER TABLE items ADD COLUMN lng REAL")
                db.execSQL("ALTER TABLE items ADD COLUMN accuracy REAL")
                db.execSQL("ALTER TABLE items ADD COLUMN photoPath TEXT")
                db.execSQL("ALTER TABLE items ADD COLUMN barcodeValue TEXT")
            }
        }

        @Volatile
        private var instance: CampusDatabase? = null

        fun get(context: Context): CampusDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CampusDatabase::class.java,
                    "objets_campus.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }

        /**
         * Les 3 objets initiaux de demonstration identiques a la PWA (seed-1, seed-2, seed-3).
         */
        fun defaultSeedItems(): List<ItemEntity> = listOf(
            ItemEntity(
                remoteId = "seed-1",
                title = "Clés avec porte-clé bleu et badge CROUS",
                category = "Clés & Badges",
                description = "Trousseau de 3 clés avec un porte-clé en plastique bleu et un badge magnétique CROUS.",
                dateFound = "2026-03-18",
                locationFound = "Bibliothèque Universitaire — 2e étage",
                depositLocation = "Accueil — Bâtiment A",
                status = ItemEntity.STATUS_ACTIVE,
                dateReturned = null,
                version = 1,
                createdAt = 1773830400000L,
                pinned = true,
                synced = true,
                lat = 42.3094,
                lng = 9.1492,
                accuracy = 12.5f,
                barcodeValue = "CROUS-CORTE-2026-8841"
            ),
            ItemEntity(
                remoteId = "seed-2",
                title = "Calculatrice graphique TI-84 Plus CE",
                category = "Électronique",
                description = "Calculatrice noire avec étui rigide. Nom effacé au dos, autocollant d'une association étudiante.",
                dateFound = "2026-03-17",
                locationFound = "Amphi 3 — Bâtiment Sciences",
                depositLocation = "Secrétariat Sciences",
                status = ItemEntity.STATUS_ACTIVE,
                dateReturned = null,
                version = 1,
                createdAt = 1773744000000L,
                pinned = false,
                synced = true,
                lat = 42.3088,
                lng = 9.1505,
                accuracy = 8.0f
            ),
            ItemEntity(
                remoteId = "seed-3",
                title = "Gourde isotherme inox noire 750ml",
                category = "Vie quotidienne",
                description = "Gourde de marque Qwetch, quelques rayures sur le bas, bouchon sport.",
                dateFound = "2026-03-15",
                locationFound = "Cafétéria du CROUS",
                depositLocation = "Accueil — Bâtiment A",
                status = ItemEntity.STATUS_RETURNED,
                dateReturned = "2026-03-16",
                version = 2,
                createdAt = 1773571200000L,
                pinned = false,
                synced = true
            )
        )
    }
}
