package app.itivio.domain

import app.itivio.data.boarding.IdfmBoardingRepository
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    private val repository = IdfmBoardingRepository({ table.byteInputStream() }, { byteArrayOf().inputStream() }, { byteArrayOf().inputStream() })

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

    private val exits = """
        near	3	Near street	48.846	2.3736
        far		Far street	48.85	2.38
        invalid	1	No point	NaN	2.3
        outside	1	Bad point	91	2.3
        missing	1	No point		
    """.trimIndent()

    private fun exitRepository(rows: String) = IdfmBoardingRepository(
        { rows.trimIndent().byteInputStream() }, { exits.byteInputStream() }, { byteArrayOf().inputStream() },
    )

    private fun destinationWalk() = walk().let { it.copy(to = it.to.copy(point = GeoPoint(48.8460, 2.3736))) }

    @Test
    fun `the nearest exit uses the journey destination and only its own positions`() = runTest {
        val repo = exitRepository("""
            1	C1	exit:far	F	
            1	C1	exit:near	M	
            1	C1	exit:near	R	L
        """)
        val journey = journey(ride("1"), destinationWalk())
        val hint = repo.hints(journey, wheelchair = false)[0]
        assertEquals("near", hint?.exit?.id)
        assertEquals("3", hint?.exit?.number)
        assertEquals("Near street", hint?.exit?.name)
        assertEquals(TrainPart.MIDDLE, hint?.part)
        assertNull(hint?.transfer)
        assertEquals(TrainPart.REAR, repo.hints(journey, wheelchair = true)[0]?.part)
    }

    @Test
    fun `conflicting positions keep the chosen exit without a train part`() = runTest {
        val repo = exitRepository("""
            1	C1	exit:near	F	
            1	C1	exit:near	R	
            1	C1	exit:far	M	
        """)
        val hint = repo.hints(journey(ride("1"), destinationWalk()), wheelchair = false)[0]
        assertEquals("near", hint?.exit?.id)
        assertNull(hint?.part)
    }

    @Test
    fun `exit rows without coordinates use the old agreement rule`() = runTest {
        val repo = exitRepository("""
            1	C1	exit:missing	F	
            1	C1	exit:invalid	F	
            2	C1	exit:missing	F	
            2	C1	exit:invalid	R	
        """)
        assertEquals(BoardingHint(TrainPart.FRONT, null), repo.hints(journey(ride("1")), false)[0])
        assertTrue(repo.hints(journey(ride("2")), false).isEmpty())
        val parsed = IdfmBoardingRepository.parseExits(exits.lines())
        assertEquals(setOf("near", "far"), parsed.keys)
        assertNull(parsed["far"]?.number)
    }

    @Test
    fun `a known exit does not change a transfer hint`() = runTest {
        val repo = exitRepository("""
            1	C1	exit:near	R	
            1	C1	9	F	
        """)
        val next = ride("5", from = "9")
        assertEquals(BoardingHint(TrainPart.FRONT, next), repo.hints(journey(ride("1"), next), false)[0])
    }

    @Test
    fun `station transfers require agreement and exact platforms keep priority`() = runTest {
        val rows = """
            1	C1	9	M
            1	C1	10	M
            1	C1	exit:near	R
            2	C1	9	F
            2	C1	10	R
            3	C1	9	F
            3	C1	9	R	L
            3	C1	10	F
            3	C1	10	R	L
            9	C2	exit	F
            10	C2	exit	F
        """.trimIndent()
        val stations = "9\tarea\tstation\n10\tarea\tstation\n"
        val repo = IdfmBoardingRepository(
            { rows.byteInputStream() }, { exits.byteInputStream() }, { stations.byteInputStream() },
        )
        for (id in listOf("area", "station")) {
            val next = ride("5", from = id, line = "C2")
            assertEquals(BoardingHint(TrainPart.MIDDLE, next), repo.hints(journey(ride("1"), walk(), next), false)[0])
            assertNull(repo.hints(journey(ride("2"), next), false)[0])
            assertEquals(TrainPart.FRONT, repo.hints(journey(ride("3"), next), false)[0]?.part)
            assertEquals(TrainPart.REAR, repo.hints(journey(ride("3"), next), true)[0]?.part)
        }
        val exact = ride("5", from = "9")
        assertEquals(BoardingHint(TrainPart.FRONT, exact), repo.hints(journey(ride("2"), exact), false)[0])
        assertNull(repo.hints(journey(ride("1"), ride("5", from = "unknown")), false)[0])
        val unmapped = ride("5", from = "11").let { it.copy(from = it.from.copy(stationId = "$idfm:station")) }
        assertNull(repo.hints(journey(ride("1"), unmapped), false)[0])
        assertNull(repo.hints(journey(ride("1"), ride("5", from = "station", line = "C3")), false)[0])
        assertNull(repo.hints(journey(ride("1"), ride("5", from = "station", line = null)), false)[0])
    }

    @Test
    fun `station table skips incomplete rows`() {
        assertEquals(
            mapOf("9" to setOf("area", "station")),
            IdfmBoardingRepository.parseStations(listOf("9\tarea\tstation", "bad", "10\t\tstation", "\tarea\tstation")),
        )
    }

    @Test
    fun `station arrivals use shared exits without guessing a train part`() = runTest {
        val rows = """
            1	C1	exit:near	F
            1	C1	exit:far	R
            2	C1	exit:near	F
            2	C1	exit:far	M
            3	C2	exit:far	R
        """.trimIndent()
        val stations = "1\tarea\tstation\n2\tarea\tstation\n3\tarea\tstation\n"
        val repo = IdfmBoardingRepository(
            { rows.byteInputStream() }, { exits.byteInputStream() }, { stations.byteInputStream() },
        )
        for (id in listOf("area", "station")) {
            for (wheelchair in listOf(false, true)) {
                val hint = repo.hints(journey(ride(id), destinationWalk()), wheelchair)[0]
                assertEquals("near", hint?.exit?.id)
                assertNull(hint?.part)
            }
            val farWalk = walk().let { it.copy(to = it.to.copy(point = GeoPoint(48.85, 2.38))) }
            assertEquals("far", repo.hints(journey(ride(id), farWalk), false)[0]?.exit?.id)
            assertEquals("far", repo.hints(journey(ride(id, line = "C2"), destinationWalk()), false)[0]?.exit?.id)
            assertTrue(repo.hints(journey(ride(id), ride("5", from = "9")), false).isEmpty())
        }
        assertEquals(TrainPart.FRONT, repo.hints(journey(ride("1"), destinationWalk()), false)[0]?.part)
        assertTrue(repo.hints(journey(ride("station", line = "unknown")), false).isEmpty())
        assertTrue(repo.hints(journey(ride("station", line = null)), false).isEmpty())
        assertTrue(repo.hints(journey(ride("unknown")), false).isEmpty())
        val unknown = ride("unknown").let { it.copy(to = it.to.copy(stationId = "$idfm:station")) }
        assertTrue(repo.hints(journey(unknown), false).isEmpty())
    }

    @Test
    fun `station arrivals exclude exits missing from another platform`() = runTest {
        val rows = """
            1	C1	exit:near	F
            1	C1	exit:far	F
            2	C1	exit:far	R
            3	C2	exit:near	M
            4	C2	9	M
            5	C3	exit:missing	F
        """.trimIndent()
        val stations = (1..5).joinToString("\n") { "$it\tarea\tstation" }
        val repo = IdfmBoardingRepository(
            { rows.byteInputStream() }, { exits.byteInputStream() }, { stations.byteInputStream() },
        )
        val hint = repo.hints(journey(ride("station"), destinationWalk()), false)[0]
        assertEquals("far", hint?.exit?.id)
        assertNull(hint?.part)
        assertTrue(repo.hints(journey(ride("station", line = "C2")), false).isEmpty())
        assertTrue(repo.hints(journey(ride("station", line = "C3")), false).isEmpty())
    }

    @Test
    fun `RER A station arrivals at La Defense give an exit towards Place des Reflets`() = runTest {
        val repo = IdfmBoardingRepository(
            { java.io.File("src/main/assets/boarding/idfm.tsv").inputStream() },
            { java.io.File("src/main/assets/boarding/exits.tsv").inputStream() },
            { java.io.File("src/main/assets/boarding/stations.tsv").inputStream() },
        )
        // IGN/BAN coordinates for 17bis Place des Reflets, Courbevoie. No live request in tests.
        val destination = walk().let { it.copy(to = it.to.copy(point = GeoPoint(48.890332, 2.247596))) }
        for (id in listOf("71517", "470549", "473934", "473935", "473936", "473937")) {
            val rer = ride(id, from = "71673", line = "C01742").copy(mode = TransitMode.RAIL)
            val hint = repo.hints(journey(rer, destination), false)[0]
            assertEquals("50148846", hint?.exit?.id)
            assertEquals("6", hint?.exit?.number)
            assertEquals("Coupole - Regnault", hint?.exit?.name)
            if (id in listOf("71517", "470549")) assertNull(hint?.part)
        }
    }

    @Test
    fun `Metro 9 at Nation gives middle guidance for an RER station ID`() = runTest {
        val repo = IdfmBoardingRepository(
            { java.io.File("src/main/assets/boarding/idfm.tsv").inputStream() },
            { java.io.File("src/main/assets/boarding/exits.tsv").inputStream() },
            { java.io.File("src/main/assets/boarding/stations.tsv").inputStream() },
        )
        for (platform in listOf("21919", "462942")) {
            for (target in listOf("71673", "473875", "473919", "473920")) {
                val metro = ride(platform, line = "C01379")
                val rer = ride("71517", from = target, line = "C01742").copy(mode = TransitMode.RAIL)
                val hint = repo.hints(journey(metro, walk(), rer), false)[0]
                assertEquals(BoardingHint(TrainPart.MIDDLE, rer), hint)
                assertNull(hint?.exit)
            }
        }
        val rer = ride("71517", from = "71673", line = "C01742")
        assertNull(repo.hints(journey(ride("22073", line = "C01376"), walk(), rer), false)[0])
    }

    @Test
    fun `all files load once off the caller thread even with concurrent requests`() = runTest {
        val caller = Thread.currentThread()
        var positionLoads = 0
        var exitLoads = 0
        var stationLoads = 0
        val repo = IdfmBoardingRepository(
            open = {
                assertTrue(Thread.currentThread() != caller)
                positionLoads++
                table.byteInputStream()
            },
            openExits = {
                assertTrue(Thread.currentThread() != caller)
                exitLoads++
                exits.byteInputStream()
            },
            openStations = {
                assertTrue(Thread.currentThread() != caller)
                stationLoads++
                byteArrayOf().inputStream()
            },
        )
        assertEquals(0, stationLoads)
        assertEquals(0, positionLoads)
        assertEquals(0, exitLoads)
        List(4) { async { repo.positions(ride("1")) } }.awaitAll()
        assertEquals(1, positionLoads)
        assertEquals(1, exitLoads)
        assertEquals(1, stationLoads)
    }

    @Test
    fun `the shipped tables parse and give a named exit at Gare de Lyon`() = runTest {
        val positionsFile = java.io.File("src/main/assets/boarding/idfm.tsv")
        val exitsFile = java.io.File("src/main/assets/boarding/exits.tsv")
        val exits = IdfmBoardingRepository.parseExits(exitsFile.readLines())
        assertEquals(exitsFile.readLines().size, exits.size)
        assertTrue(exits.size > 1000)
        val parsed = IdfmBoardingRepository.parse(positionsFile.readLines(), exits)
        assertEquals(positionsFile.readLines().size, parsed.values.sumOf { it.size })
        assertTrue(parsed.size > 500)
        // Line 14 at Madeleine: the front is nearest to the exits.
        assertEquals(TrainPart.FRONT, parsed["463064" to "C01384"]?.filter { it.transferStop == null }?.onePart(false))
        val repo = IdfmBoardingRepository(
            { positionsFile.inputStream() }, { exitsFile.inputStream() },
            { java.io.File("src/main/assets/boarding/stations.tsv").inputStream() },
        )
        val hint = repo.hints(journey(ride("463197", line = "C01371"), destinationWalk()), false)[0]
        assertEquals("3", hint?.exit?.number)
        assertEquals("r. M. Chasles", hint?.exit?.name)
        assertNull(hint?.part)
        assertTrue(hint?.exit?.id in exits)
    }
}
