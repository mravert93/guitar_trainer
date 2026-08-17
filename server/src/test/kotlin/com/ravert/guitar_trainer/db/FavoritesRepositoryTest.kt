package com.ravert.guitar_trainer.db

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FavoritesRepositoryTest {
    @Test
    fun freeUsersCanAddUntilTheyHaveThreeFavorites() {
        assertFalse(favoriteLimitReached(hasPremium = false, favoriteCount = 0))
        assertFalse(favoriteLimitReached(hasPremium = false, favoriteCount = 2))
        assertTrue(favoriteLimitReached(hasPremium = false, favoriteCount = 3))
        assertTrue(favoriteLimitReached(hasPremium = false, favoriteCount = 4))
    }

    @Test
    fun premiumUsersHaveNoFavoriteLimit() {
        assertFalse(favoriteLimitReached(hasPremium = true, favoriteCount = 3))
        assertFalse(favoriteLimitReached(hasPremium = true, favoriteCount = 1_000))
    }
}
