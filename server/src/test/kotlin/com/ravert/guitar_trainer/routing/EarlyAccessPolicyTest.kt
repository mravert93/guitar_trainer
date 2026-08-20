package com.ravert.guitar_trainer.routing

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EarlyAccessPolicyTest {
    private val now = 1_000_000L

    @Test
    fun `songs without a release date are public`() {
        assertTrue(canAccessSong(releaseAt = null, hasPremium = false, now = now))
    }

    @Test
    fun `released songs are public`() {
        assertTrue(canAccessSong(releaseAt = now - 1, hasPremium = false, now = now))
        assertTrue(canAccessSong(releaseAt = now, hasPremium = false, now = now))
    }

    @Test
    fun `future songs require premium`() {
        assertFalse(canAccessSong(releaseAt = now + 1, hasPremium = false, now = now))
        assertTrue(canAccessSong(releaseAt = now + 1, hasPremium = true, now = now))
    }
}
