package app.itivio.domain

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** What a provider step asks the walker to do, apart from the turn. */
enum class StepKind { WALK, STAIRS, ELEVATOR }

/**
 * One step of a walk, as the provider gives it. A step is often only a few metres long, and the
 * provider can give no turn and no street name. [WalkRoute] makes instructions from the steps.
 */
data class WalkStep(
    val distanceMeters: Double,
    /** Null when the way has no name. */
    val streetName: String?,
    val geometry: List<GeoPoint>,
    val kind: StepKind = StepKind.WALK,
    /** The turn the provider gives at the start of the step. Null when it gives none, or "continue". */
    val turn: Maneuver? = null,
)

enum class Maneuver {
    DEPART, STRAIGHT, SLIGHT_LEFT, LEFT, SHARP_LEFT, SLIGHT_RIGHT, RIGHT, SHARP_RIGHT, U_TURN, STAIRS, ELEVATOR, ARRIVE,
}

enum class CompassDirection { NORTH, NORTHEAST, EAST, SOUTHEAST, SOUTH, SOUTHWEST, WEST, NORTHWEST }

/**
 * One instruction of the walking view. It starts with [maneuver] at [startMeters] along the walk
 * and goes on for [lengthMeters]. [heading] is set for [Maneuver.DEPART] only.
 */
data class WalkInstruction(
    val maneuver: Maneuver,
    val streetName: String?,
    val startMeters: Double,
    val lengthMeters: Double,
    val heading: CompassDirection? = null,
)

/** Where the walker is on a [WalkRoute]. */
data class WalkProgress(
    /** Distance walked along the route, in metres. */
    val alongMeters: Double,
    /** Distance from the position to the nearest point of the route. */
    val offRouteMeters: Double,
) {
    val isOffRoute: Boolean get() = offRouteMeters > WalkRoute.OFF_ROUTE_METERS
}

/**
 * The geometry and the instructions of one walk. The turns come from the geometry: providers
 * often give "continue" for every step.
 */
class WalkRoute(val points: List<GeoPoint>, val instructions: List<WalkInstruction>) {
    private val cumulative: DoubleArray = DoubleArray(points.size).also { sums ->
        for (i in 1 until points.size) sums[i] = sums[i - 1] + distance(points[i - 1], points[i])
    }

    val lengthMeters: Double get() = cumulative.lastOrNull() ?: 0.0

    /** Projects [position] on the route. With more than one near part, the nearest wins. */
    fun progress(position: GeoPoint): WalkProgress {
        if (points.size < 2) return WalkProgress(0.0, points.firstOrNull()?.let { distance(it, position) } ?: 0.0)
        var bestOff = Double.MAX_VALUE
        var bestAlong = 0.0
        for (i in 0 until points.lastIndex) {
            val (fraction, off) = project(points[i], points[i + 1], position)
            if (off < bestOff) {
                bestOff = off
                bestAlong = cumulative[i] + fraction * (cumulative[i + 1] - cumulative[i])
            }
        }
        return WalkProgress(bestAlong, bestOff)
    }

    /**
     * The instruction to show at [alongMeters]: the start instruction near the start, then the
     * next maneuver ahead. The arrival is the last one.
     */
    fun nextInstructionIndex(alongMeters: Double): Int {
        if (alongMeters < START_RADIUS_METERS) return 0
        val next = instructions.indexOfFirst { it.startMeters > alongMeters }
        return if (next >= 0) next else instructions.lastIndex
    }

    private fun pointAt(meters: Double): GeoPoint {
        val target = meters.coerceIn(0.0, lengthMeters)
        val index = cumulative.indexOfFirst { it >= target }.coerceAtLeast(1)
        val a = points[index - 1]
        val b = points[index]
        val span = cumulative[index] - cumulative[index - 1]
        val t = if (span <= 0) 0.0 else (target - cumulative[index - 1]) / span
        return GeoPoint(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
    }

    /** Bearing of the route over [BEARING_WINDOW_METERS] that end or start at [meters]. Null on a zero-length part. */
    private fun bearing(meters: Double, ahead: Boolean): Double? {
        val (from, to) = if (ahead) meters to meters + BEARING_WINDOW_METERS else meters - BEARING_WINDOW_METERS to meters
        val a = pointAt(from)
        val b = pointAt(to)
        return if (distance(a, b) < 1.0) null else bearing(a, b)
    }

    /**
     * The change of direction at [meters], or over a short bend from [meters] to [end], in degrees
     * from -180 to 180. Positive turns right. Null on a zero-length part.
     */
    private fun angleAt(meters: Double, end: Double = meters): Double? {
        val before = bearing(meters, ahead = false) ?: return null
        val after = bearing(end, ahead = true) ?: return null
        return normalized(after - before)
    }

    internal fun turnAt(meters: Double, end: Double = meters): Maneuver = angleAt(meters, end)?.let(::turn) ?: Maneuver.STRAIGHT

    companion object {
        /** A position farther than this from the route counts as off the route. */
        const val OFF_ROUTE_METERS = 50.0

        /** Until the walker is this far along, the view shows the start instruction. */
        const val START_RADIUS_METERS = 15.0

        /** Long enough to smooth the short steps of footpaths, short enough to keep corners. */
        private const val BEARING_WINDOW_METERS = 15.0

        /** A smaller change of direction is a bend of the way, not a turn. */
        private const val MIN_TURN_DEGREES = 35.0

        /** Shorter instructions join the one before; they come from small bends in paths. */
        private const val MIN_INSTRUCTION_METERS = 10.0

        /**
         * Builds the instructions of [steps]. A new instruction starts at a turn, at stairs or a
         * lift, and at a turn the provider gives. Without steps, [geometry] gives one instruction.
         */
        fun of(steps: List<WalkStep>, geometry: List<GeoPoint>): WalkRoute {
            val usable = steps.filter { it.geometry.size >= 2 }
            val parts = usable.ifEmpty { listOf(WalkStep(0.0, null, geometry)) }
            val points = buildList {
                parts.forEach { step -> step.geometry.forEach { if (lastOrNull() != it) add(it) } }
            }
            if (points.size < 2) return WalkRoute(points, emptyList())
            val shape = WalkRoute(points, emptyList())

            // Start of each step, in metres along the route.
            val starts = DoubleArray(parts.size)
            var position = 0.0
            parts.forEachIndexed { index, step ->
                starts[index] = position
                position += step.geometry.zipWithNext { a, b -> distance(a, b) }.sum()
            }

            // One corner shows at each step start near it. Of a run of starts that turn to the same
            // side, only the sharpest one is the corner.
            val corners = mutableSetOf<Int>()
            val run = mutableListOf<Pair<Int, Double>>()
            fun flush() {
                run.maxByOrNull { abs(it.second) }?.let { corners += it.first }
                run.clear()
            }
            for (index in 1 until parts.size) {
                if (parts[index].kind != StepKind.WALK || parts[index - 1].kind != StepKind.WALK || parts[index].turn != null) continue
                val angle = shape.angleAt(starts[index])?.takeIf { abs(it) >= MIN_TURN_DEGREES } ?: continue
                val last = run.lastOrNull()
                if (last != null && (starts[index] - starts[last.first] >= BEARING_WINDOW_METERS || (angle > 0) != (last.second > 0))) flush()
                run += index to angle
            }
            flush()

            class Draft(
                val maneuver: Maneuver,
                val start: Double,
                val names: MutableMap<String, Double> = mutableMapOf(),
                /** Where the start direction is measured. */
                var headingAt: Double = start,
            )
            val drafts = mutableListOf<Draft>()
            parts.forEachIndexed { index, step ->
                val maneuver = when {
                    step.kind != StepKind.WALK -> step.kind.maneuver()
                    index == 0 -> Maneuver.DEPART
                    // After stairs or a lift, a new instruction starts, even straight on.
                    parts[index - 1].kind != StepKind.WALK -> shape.turnAt(starts[index])
                    step.turn != null -> step.turn
                    index in corners -> shape.turnAt(starts[index])
                    else -> null
                }
                if (maneuver != null) drafts += Draft(maneuver, starts[index])
                val length = (if (index + 1 < parts.size) starts[index + 1] else shape.lengthMeters) - starts[index]
                step.streetName?.let { drafts.last().names.merge(it, length, Double::plus) }
            }

            // A short turn and the turn after it are one bend, such as a path that crosses a road.
            val kept = mutableListOf<Draft>()
            drafts.forEach { draft ->
                val last = kept.lastOrNull()
                if (last != null && kept.size > 1 && last.maneuver.isTurn() && draft.maneuver.isTurn() &&
                    draft.start - last.start < MIN_INSTRUCTION_METERS
                ) {
                    draft.names.forEach { (name, length) -> last.names.merge(name, length, Double::plus) }
                    val bend = shape.turnAt(last.start, draft.start)
                    kept.removeAt(kept.lastIndex)
                    // A bend that comes out straight is no instruction: the one before goes on.
                    if (bend == Maneuver.STRAIGHT) last.names.forEach { (name, length) -> kept.last().names.merge(name, length, Double::plus) }
                    else kept += Draft(bend, last.start, last.names)
                } else {
                    kept += draft
                }
            }
            // A turn just after the start or just before the end comes from the way into a station
            // or a building: the start then points along the way after the turn, and the end needs no turn.
            if (kept.size > 1 && kept[0].maneuver == Maneuver.DEPART && kept[1].maneuver.isTurn() &&
                kept[1].start < MIN_INSTRUCTION_METERS
            ) {
                val turn = kept.removeAt(1)
                turn.names.forEach { (name, length) -> kept[0].names.merge(name, length, Double::plus) }
                kept[0].headingAt = turn.start
            }
            while (kept.size > 1 && kept.last().maneuver.isTurn() && shape.lengthMeters - kept.last().start < MIN_INSTRUCTION_METERS) {
                val turn = kept.removeAt(kept.lastIndex)
                turn.names.forEach { (name, length) -> kept.last().names.merge(name, length, Double::plus) }
            }

            val instructions = kept.mapIndexed { index, draft ->
                val end = kept.getOrNull(index + 1)?.start ?: shape.lengthMeters
                // The name that covers most of the instruction. Short named bits do not rename a path.
                val name = draft.names.maxByOrNull { it.value }?.takeIf { it.value >= (end - draft.start) / 3 }?.key
                WalkInstruction(
                    maneuver = draft.maneuver,
                    streetName = name,
                    startMeters = draft.start,
                    lengthMeters = end - draft.start,
                    heading = if (draft.maneuver == Maneuver.DEPART) shape.bearing(draft.headingAt, ahead = true)?.let(::compass) else null,
                )
            } + WalkInstruction(Maneuver.ARRIVE, null, shape.lengthMeters, 0.0)
            return WalkRoute(points, instructions)
        }

        private fun normalized(degrees: Double) = ((degrees % 360) + 540) % 360 - 180

        internal fun turn(delta: Double): Maneuver {
            val angle = normalized(delta)
            val size = abs(angle)
            val right = angle > 0
            return when {
                size < MIN_TURN_DEGREES -> Maneuver.STRAIGHT
                size < 60 -> if (right) Maneuver.SLIGHT_RIGHT else Maneuver.SLIGHT_LEFT
                size < 120 -> if (right) Maneuver.RIGHT else Maneuver.LEFT
                size < 160 -> if (right) Maneuver.SHARP_RIGHT else Maneuver.SHARP_LEFT
                else -> Maneuver.U_TURN
            }
        }

        private fun compass(bearing: Double): CompassDirection =
            CompassDirection.entries[(((bearing % 360) + 360) % 360 / 45).roundToInt() % 8]

        private fun StepKind.maneuver(): Maneuver = when (this) {
            StepKind.WALK -> Maneuver.STRAIGHT
            StepKind.STAIRS -> Maneuver.STAIRS
            StepKind.ELEVATOR -> Maneuver.ELEVATOR
        }

        private fun Maneuver.isTurn() = this != Maneuver.DEPART && this != Maneuver.STAIRS && this != Maneuver.ELEVATOR && this != Maneuver.ARRIVE

        private const val METERS_PER_DEGREE = 111_320.0

        /** Equirectangular distance in metres: exact enough for the parts of a walk. */
        fun distance(a: GeoPoint, b: GeoPoint): Double {
            val dy = (b.latitude - a.latitude) * METERS_PER_DEGREE
            val dx = (b.longitude - a.longitude) * METERS_PER_DEGREE * cos(Math.toRadians((a.latitude + b.latitude) / 2))
            return sqrt(dx * dx + dy * dy)
        }

        /** Degrees clockwise from north. */
        private fun bearing(a: GeoPoint, b: GeoPoint): Double {
            val dy = b.latitude - a.latitude
            val dx = (b.longitude - a.longitude) * cos(Math.toRadians((a.latitude + b.latitude) / 2))
            return Math.toDegrees(atan2(dx, dy))
        }

        /** Fraction along a–b of the point nearest to [p], and the distance to it in metres. */
        private fun project(a: GeoPoint, b: GeoPoint, p: GeoPoint): Pair<Double, Double> {
            val scale = cos(Math.toRadians(p.latitude))
            val ax = a.longitude * scale
            val bx = b.longitude * scale
            val px = p.longitude * scale
            val dx = bx - ax
            val dy = b.latitude - a.latitude
            val lengthSquared = dx * dx + dy * dy
            val t = if (lengthSquared == 0.0) 0.0 else (((px - ax) * dx + (p.latitude - a.latitude) * dy) / lengthSquared).coerceIn(0.0, 1.0)
            val nearest = GeoPoint(a.latitude + dy * t, a.longitude + (b.longitude - a.longitude) * t)
            return t to distance(nearest, p)
        }
    }
}
