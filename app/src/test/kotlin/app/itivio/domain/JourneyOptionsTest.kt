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

    @Test
    fun `each control away from its default counts as one change`() {
        assertEquals(0, JourneyOptions().changeCount)
        val changed = JourneyOptions(
            excludedModes = setOf(TransitMode.COACH, TransitMode.FERRY),
            maxTransfers = 1,
            walkingSpeed = WalkingSpeed.FAST,
        )
        assertEquals(4, changed.changeCount)
    }

    @Test
    fun `the walking speed does not count while the wheelchair switch is on`() {
        assertEquals(1, JourneyOptions(wheelchair = true, walkingSpeed = WalkingSpeed.FAST).changeCount)
    }
}
