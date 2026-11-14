package app.itivio.domain

/** The part of a train to board, in the direction of travel. */
enum class TrainPart { FRONT, MIDDLE, REAR }

/**
 * Where to board the train of a transit leg, so that the rider gets off near the next step.
 * [transfer] is the next transit leg when the hint is for that transfer. Null means the hint is
 * for the station exit.
 */
data class BoardingHint(val part: TrainPart, val transfer: Leg.Transit?)

/**
 * One known position on a platform: [part] of the train stops nearest to the transfer platform
 * [transferStop], or to an exit when it is null. [lift] is true when the path has a lift.
 */
data class BoardingPosition(val transferStop: String?, val part: TrainPart, val lift: Boolean)

/** The boarding positions of the stations that publish them. */
fun interface BoardingRepository {
    /** The positions on the platform where [leg] gets off. Empty when nobody publishes them. */
    suspend fun positions(leg: Leg.Transit): List<BoardingPosition>
}

/**
 * The boarding hint of each transit leg of [journey], by leg index. A hint shows only when the
 * data gives one part of the train for the next step: a wrong hint is worse than none.
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
        known.onePart(wheelchair)?.let { hints[index] = BoardingHint(it, next) }
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
