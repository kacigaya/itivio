package app.itivio.ui

import app.itivio.ui.results.spareMinutes
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpareMinutesTest {
    private val target = Instant.parse("2026-11-24T09:30:00Z")

    @Test fun `whole minutes before the target are spare`() =
        assertEquals(3L, spareMinutes(target.minusSeconds(3 * 60 + 40), target))

    @Test fun `no spare minute when the route arrives late or on time`() {
        assertNull(spareMinutes(target.plusSeconds(60), target))
        assertNull(spareMinutes(target.minusSeconds(30), target))
    }

    @Test fun `an hour or more is not shown`() = assertNull(spareMinutes(target.minusSeconds(3_600), target))
}
