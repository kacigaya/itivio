package app.itivio.domain

import app.itivio.data.boarding.IdfmBoardingRepository
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardingTest {
    private val idfm = "fr-reseau-urbain-et-interurbain-dile-de-france-mobilites_IDFM"
    private val t0 = Instant.parse("2026-11-16T09:00:00Z")

    // Platform 1 of line C1 has a transfer to platform 9 and two exits.
    private val table = """
        1	C1	9	F	
        1	C1	9	R	L
        1	C1	exit	M	
        1	C1	exit	M	L
        2	C1	exit	F	
        2	C1	exit	R	
        3	C1	7	F	
        bad line
        4	C1	exit	7	
    """.trimIndent()
    private val repository = IdfmBoardingRepository { table.byteInputStream() }

    private fun stop(id: String?) = Stop("Stop", GeoPoint(48.8, 2.3), stopId = id?.let { "$idfm:$it" })
    private fun ride(to: String?, from: String? = "0", line: String? = "C1") = Leg.Transit(
        TransitMode.SUBWAY, stop(from), stop(to), StopTime(t0), StopTime(t0.plusSeconds(600)), emptyList(),
        Line("1", id = line?.let { "$idfm:$it" }), null, null, emptyList(),
    )
    private fun walk() = Leg.Street(TransitMode.WALK, stop(null), stop(null), StopTime(t0), StopTime(t0.plusSeconds(120)), emptyList(), 100.0)
    private fun journey(vararg legs: Leg) = Journey("j", legs.toList(), StopTime(t0), StopTime(t0), 0)

    @Test
    fun `a transfer gets the part that stops nearest to the next platform`() = runTest {
        val next = ride("5", from = "9")
        val hints = repository.hints(journey(ride("1"), walk(), next), wheelchair = false)
        assertEquals(BoardingHint(TrainPart.FRONT, next), hints[0])
    }

    @Test
    fun `a wheelchair user gets the part near the lift`() = runTest {
        val hints = repository.hints(journey(ride("1"), walk(), ride("5", from = "9")), wheelchair = true)
        assertEquals(TrainPart.REAR, hints[0]?.part)
    }

    @Test
    fun `the last train gets the exit when all exits agree`() = runTest {
        assertEquals(BoardingHint(TrainPart.MIDDLE, null), repository.hints(journey(ride("1"), walk()), wheelchair = false)[0])
        // The exits of platform 2 are at the two ends: no hint is better than a wrong one.
        assertTrue(repository.hints(journey(ride("2")), wheelchair = false).isEmpty())
    }

    @Test
    fun `an unlisted transfer, another line, or another operator gets no hint`() = runTest {
        assertTrue(repository.hints(journey(ride("1"), ride("5", from = "8")), wheelchair = false).isEmpty())
        assertTrue(repository.hints(journey(ride("1", line = "C2")), wheelchair = false).isEmpty())
        val other = ride("1").let { it.copy(to = it.to.copy(stopId = "de-VBB_1"), line = it.line.copy(id = "de-VBB_C1")) }
        assertTrue(repository.hints(journey(other), wheelchair = false).isEmpty())
        assertTrue(repository.hints(journey(ride("1").copy(cancelled = true)), wheelchair = false).isEmpty())
    }

    @Test
    fun `the table skips rows without a part of the train`() {
        val parsed = IdfmBoardingRepository.parse(table.lines())
        assertNull(parsed["4" to "C1"])
        assertEquals(2, parsed["1" to "C1"]?.count { it.transferStop == "9" })
        assertEquals("22194", IdfmBoardingRepository.idfmId("$idfm:22194"))
        assertNull(IdfmBoardingRepository.idfmId("de-VBB_900"))
    }

    @Test
    fun `the shipped table parses and covers the metro`() {
        val file = java.io.File("src/main/assets/boarding/idfm.tsv")
        val parsed = IdfmBoardingRepository.parse(file.readLines())
        assertTrue(parsed.size > 500)
        // Line 14 at Madeleine: the front is nearest to the exits.
        assertEquals(TrainPart.FRONT, parsed["463064" to "C01384"]?.filter { it.transferStop == null }?.onePart(false))
    }
}
