package com.ravert.guitar_trainer.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MembershipTierTest {
    @Test
    fun `parses supported API values`() {
        assertEquals(MembershipTier.PREMIUM, MembershipTier.fromApiValue("premium"))
        assertEquals(MembershipTier.PREMIUM_PLUS, MembershipTier.fromApiValue(" PREMIUM_PLUS "))
    }

    @Test
    fun `rejects unsupported API values`() {
        assertNull(MembershipTier.fromApiValue("custom"))
        assertNull(MembershipTier.fromApiValue(null))
    }
}
