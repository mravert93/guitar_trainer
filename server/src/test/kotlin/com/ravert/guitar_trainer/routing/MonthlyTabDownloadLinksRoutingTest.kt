package com.ravert.guitar_trainer.routing

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class MonthlyTabDownloadLinksRoutingTest {
    @Test
    fun monthlyWindowUsesTheStartOfTheMonthAsItsFixedCutoff() {
        val window = currentMonthlyLinkWindow(
            now = Instant.parse("2026-08-31T20:00:00Z"),
            zone = ZoneId.of("America/Phoenix"),
        )

        assertEquals("2026-08", window.monthKey)
        assertEquals(Instant.parse("2026-08-01T07:00:00Z").toEpochMilli(), window.cutoffAt)
        assertEquals(Instant.parse("2026-09-01T07:00:00Z").toEpochMilli(), window.nextMonthAt)
    }
}
