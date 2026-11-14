package app.itivio.data.boarding

import app.itivio.domain.BoardingPosition
import app.itivio.domain.BoardingRepository
import app.itivio.domain.GeoPoint
import app.itivio.domain.StationExit
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
 * so this sends no request. The files load once, off the main thread, on the first call.
 */
class IdfmBoardingRepository(
    private val open: () -> InputStream,
    private val openExits: () -> InputStream,
    private val openStations: () -> InputStream,
) : BoardingRepository {
    private val lock = Mutex()
    private data class Tables(
        val platforms: Map<Pair<String, String>, List<BoardingPosition>>,
        val stationExits: Map<Pair<String, String>, List<BoardingPosition>>,
    )

    private var table: Tables? = null

    override suspend fun positions(leg: Leg.Transit): List<BoardingPosition> {
        val platform = idfmId(leg.to.stopId) ?: return emptyList()
        val line = idfmId(leg.line.id) ?: return emptyList()
        val tables = table()
        return tables.platforms[platform to line] ?: tables.stationExits[platform to line].orEmpty()
    }

    private suspend fun table() = lock.withLock {
        table ?: withContext(Dispatchers.IO) {
            val exits = openExits().bufferedReader().use { parseExits(it.readLines()) }
            val stations = openStations().bufferedReader().use { parseStations(it.readLines()) }
            val platforms = open().bufferedReader().use { parse(it.readLines(), exits, stations) }
            Tables(platforms, stationExits(platforms, stations))
        }.also { table = it }
    }

    internal companion object {
        private fun stationExits(
            platforms: Map<Pair<String, String>, List<BoardingPosition>>,
            stations: Map<String, Set<String>>,
        ): Map<Pair<String, String>, List<BoardingPosition>> = platforms.entries.flatMap { (key, positions) ->
            stations[key.first].orEmpty().map { (it to key.second) to positions }
        }.groupBy({ it.first }, { it.second }).mapValues { (_, platformPositions) ->
            // With no arrival platform, use only exits listed for every platform of this line.
            val commonExits = platformPositions.map { positions -> positions.mapNotNull { it.exit?.id }.toSet() }
                .reduce { common, exits -> common intersect exits }
            platformPositions.flatten().filter { it.exit?.id in commonExits }
                .map { it.copy(part = null) }.distinct()
        }

        /** IDFM IDs keep their own part after the prefix of the feed: "…_IDFM:22194" gives "22194". */
        fun idfmId(id: String?): String? = id?.substringAfterLast("IDFM:", "")?.takeIf { it.isNotEmpty() }

        /** One line per position: platform, line, target ("exit:<access ID>" or a stop ID), part (F, M, R), and "L" for a lift. */
        fun parse(
            lines: List<String>,
            exits: Map<String, StationExit> = emptyMap(),
            stations: Map<String, Set<String>> = emptyMap(),
        ): Map<Pair<String, String>, List<BoardingPosition>> = lines.mapNotNull { line ->
            val fields = line.split('\t')
            if (fields.size < 4) return@mapNotNull null
            val part = when (fields[3]) {
                "F" -> TrainPart.FRONT
                "M" -> TrainPart.MIDDLE
                "R" -> TrainPart.REAR
                else -> return@mapNotNull null
            }
            val target = fields[2]
            val isExit = target == "exit" || target.startsWith("exit:")
            (fields[0] to fields[1]) to BoardingPosition(
                transferStop = target.takeUnless { isExit },
                part = part,
                lift = fields.getOrNull(4) == "L",
                exit = if (isExit) exits[target.removePrefix("exit:")] else null,
                transferStations = if (isExit) emptySet() else stations[target].orEmpty(),
            )
        }.groupBy({ it.first }, { it.second }).let { table ->
            val linesByPlatform = table.keys.groupBy({ it.first }, { it.second })
            table.mapValues { (_, positions) ->
                positions.map { position ->
                    position.copy(transferLines = linesByPlatform[position.transferStop].orEmpty().toSet())
                }
            }
        }

        /** Platform, stop area, and parent station from the IDFM stop reference. */
        fun parseStations(lines: List<String>): Map<String, Set<String>> = lines.mapNotNull { line ->
            val fields = line.split('\t')
            if (fields.size != 3 || fields.any { it.isBlank() }) return@mapNotNull null
            fields[0] to setOf(fields[1], fields[2])
        }.toMap()

        /** Access ID, optional number, name, latitude, longitude. Invalid coordinates give no exit. */
        fun parseExits(lines: List<String>): Map<String, StationExit> = lines.mapNotNull { line ->
            val fields = line.split('\t')
            if (fields.size != 5 || fields[0].isBlank() || fields[2].isBlank()) return@mapNotNull null
            val lat = fields[3].toDoubleOrNull() ?: return@mapNotNull null
            val lon = fields[4].toDoubleOrNull() ?: return@mapNotNull null
            if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
            fields[0] to StationExit(fields[0], fields[1].takeIf { it.isNotBlank() }, fields[2], GeoPoint(lat, lon))
        }.toMap()
    }
}
