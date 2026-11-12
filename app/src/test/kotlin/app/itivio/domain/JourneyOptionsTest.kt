package app.itivio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyOptionsTest {
    @Test
    fun `a mode turns off and on again`() {
        val noBus = JourneyOptions().withMode(TransitMode.BUS, allowed = false)
        assertEquals(setOf(TransitMode.BUS), noBus.excludedModes)
        assertFalse(noBus.isDefault)
        assertTrue(noBus.withMode(TransitMode.BUS, allowed = true).isDefault)
    }

    @Test
    fun `the last selectable mode stays on`() {
        val onlyTram = JourneyOptions.SELECTABLE_MODES.filter { it != TransitMode.TRAM }
            .fold(JourneyOptions()) { options, mode -> options.withMode(mode, allowed = false) }

        assertEquals(onlyTram, onlyTram.withMode(TransitMode.TRAM, allowed = false))
    }
}
