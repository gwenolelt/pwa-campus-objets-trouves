package corsica.universita.objetscampus.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String = "",
    val title: String,
    val category: String,
    val description: String = "",
    val dateFound: String,
    val locationFound: String,
    val depositLocation: String,
    val status: String = STATUS_ACTIVE,
    val dateReturned: String? = null,
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    // Ajoute en v2 (MIGRATION_1_2)
    val pinned: Boolean = false,
    // Ajoute en v3 (MIGRATION_2_3)
    val synced: Boolean = false,
    // Ajoutes en v4 (MIGRATION_3_4 : Capteurs GPS, Photo interne & Scanner Code-barres/QR natif)
    val lat: Double? = null,
    val lng: Double? = null,
    val accuracy: Float? = null,
    val photoPath: String? = null,
    val barcodeValue: String? = null
) {
    val isReturned: Boolean
        get() = status == STATUS_RETURNED

    companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_RETURNED = "returned"

        val CATEGORIES = listOf(
            "Clés & Badges",
            "Électronique",
            "Vêtements",
            "Livres & Cours",
            "Sacs & Portefeuilles",
            "Vie quotidienne",
            "Autre"
        )

        val DEPOSIT_LOCATIONS = listOf(
            "Accueil — Bâtiment A",
            "Bibliothèque Universitaire",
            "Secrétariat Sciences",
            "Loge gardien — Entrée principale",
            "Bureau de la Vie Étudiante"
        )
    }
}
