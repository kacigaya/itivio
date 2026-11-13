package app.itivio.data.boarding

import app.itivio.domain.BoardingPosition
import app.itivio.domain.BoardingRepository
import app.itivio.domain.Leg
import app.itivio.domain.TrainPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * The boarding positions of the RATP metro and RER platforms, from the IDFM dataset
 * "Positionnement dans la rame". The app ships the table (see `tools/boarding-positions.py`),
 * so this sends no request. [open] reads the table; it runs once, on the first call.
 */
class IdfmBoardingRepository(private val open: () -> InputStream) : BoardingRepository {
    private val lock = Mutex()
    private var table: Map<Pair<String, String>, List<BoardingPosition>>? = null

    override suspend fun positions(leg: Leg.Transit): List<BoardingPosition> {
        val platform = idfmId(leg.to.stopId) ?: return emptyList()
        val line = idfmId(leg.line.id) ?: return emptyList()
        return table()[platform to line].orEmpty()
    }

    private suspend fun table() = lock.withLock {
        table ?: withContext(Dispatchers.IO) { open().bufferedReader().use { parse(it.readLines()) } }.also { table = it }
    }

    internal companion object {
        /** IDFM IDs keep their own part after the prefix of the feed: "…_IDFM:22194" gives "22194". */
        fun idfmId(id: String?): String? = id?.substringAfterLast("IDFM:", "")?.takeIf { it.isNotEmpty() }

        /** One line per position: platform, line, target ("exit" or a stop ID), part (F, M, R), and "L" for a lift. */
        fun parse(lines: List<String>): Map<Pair<String, String>, List<BoardingPosition>> = lines.mapNotNull { line ->
            val fields = line.split('\t')
            if (fields.size < 4) return@mapNotNull null
            val part = when (fields[3]) {
                "F" -> TrainPart.FRONT
                "M" -> TrainPart.MIDDLE
                "R" -> TrainPart.REAR
                else -> return@mapNotNull null
            }
            val target = fields[2].takeIf { it != "exit" }
            (fields[0] to fields[1]) to BoardingPosition(target, part, lift = fields.getOrNull(4) == "L")
        }.groupBy({ it.first }, { it.second })
    }
}
