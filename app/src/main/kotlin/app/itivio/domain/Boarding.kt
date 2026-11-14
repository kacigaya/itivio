package app.itivio.domain

/** The part of a train to board, in the direction of travel. */
enum class TrainPart { FRONT, MIDDLE, REAR }

data class StationExit(val id: String, val number: String?, val name: String, val point: GeoPoint)

/** part: null when the data has no single part for this target; exit: the chosen exit, or null for a transfer. */
data class BoardingHint(val part: TrainPart?, val transfer: Leg.Transit?, val exit: StationExit? = null)

/** A position near a transfer platform, or an exit. Exit details need known coordinates. */
data class BoardingPosition(
    val transferStop: String?,
    val part: TrainPart,
    val lift: Boolean,
    val exit: StationExit? = null,
)

/** The boarding positions of the stations that publish them. */
fun interface BoardingRepository {
    /** The positions on the platform where [leg] gets off. Empty when nobody publishes them. */
    suspend fun positions(leg: Leg.Transit): List<BoardingPosition>
}

/**
 * The boarding hint of each transit leg of [journey], by leg index. An exit hint chooses the
 * exit nearest to the final destination. Its part can be unknown when positions disagree.
 *
 * - The next step is the next transit leg when only walks come between, else the exit.
 * - A transfer that the data does not list gets no hint, not the exit hint: the rider stays in
 *   the station.
 * - [BoardingPosition.transferStop] is the ID of the operator. The provider adds a prefix to it,
 *   so a stop matches when its ID ends with ":" and that ID.
 */
suspend fun BoardingRepository.hints(journey: Journey, wheelchair: Boolean): Map<Int, BoardingHint> {
    val legs = journey.legs
    val hints = mutableMapOf<Int, BoardingHint>()
    legs.forEachIndexed { index, leg ->
        if (leg !is Leg.Transit || leg.cancelled) return@forEachIndexed
        val next = legs.drop(index + 1).firstOrNull { it is Leg.Transit } as Leg.Transit?
        val nextStop = next?.from?.stopId
        if (next != null && nextStop == null) return@forEachIndexed
        val known = positions(leg).filter { position ->
            val stop = position.transferStop
            if (nextStop == null) stop == null else stop != null && nextStop.endsWith(":$stop")
        }
        val exit = if (next == null) {
            known.mapNotNull { it.exit }.distinctBy { it.id }
                .minByOrNull { WalkRoute.distance(it.point, legs.last().to.point) }
        } else null
        val target = if (exit == null) known else known.filter { it.exit?.id == exit.id }
        val part = target.onePart(wheelchair)
        if (part != null || exit != null) hints[index] = BoardingHint(part, next, exit)
    }
    return hints
}

/**
 * The one part of the train that these positions agree on, or null. A rider in a wheelchair
 * needs a lift; other riders take the stairs or the escalator. When no position fits the rider,
 * all of them count.
 */
internal fun List<BoardingPosition>.onePart(wheelchair: Boolean): TrainPart? =
    filter { it.lift == wheelchair }.ifEmpty { this }.map { it.part }.distinct().singleOrNull()
