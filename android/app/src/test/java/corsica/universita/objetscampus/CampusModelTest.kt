package corsica.universita.objetscampus

import corsica.universita.objetscampus.data.local.CampusDatabase
import corsica.universita.objetscampus.data.local.ItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusModelTest {

    @Test
    fun seedItems_matchPwaCatalogue() {
        val seeds = CampusDatabase.defaultSeedItems()
        assertEquals(3, seeds.size)
        assertEquals("Clés avec porte-clé bleu et badge CROUS", seeds[0].title)
        assertTrue(seeds[0].pinned)
        assertFalse(seeds[0].isReturned)

        assertEquals("Gourde isotherme inox noire 750ml", seeds[2].title)
        assertTrue(seeds[2].isReturned)
        assertEquals(ItemEntity.STATUS_RETURNED, seeds[2].status)
    }

    @Test
    fun categories_and_depositLocations_areComplete() {
        assertEquals(7, ItemEntity.CATEGORIES.size)
        assertTrue(ItemEntity.CATEGORIES.contains("Clés & Badges"))
        assertTrue(ItemEntity.DEPOSIT_LOCATIONS.contains("Accueil — Bâtiment A"))
    }
}
