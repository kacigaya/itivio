package app.itivio.data.transitous

import app.itivio.domain.GeoPoint
import kotlin.math.pow

internal fun decodePolyline(points: String, precision: Int): List<GeoPoint> {
    require(precision in 0..7) { "Invalid polyline precision" }
    val scale = 10.0.pow(precision)
    var index = 0
    var latitude = 0L
    var longitude = 0L
    fun component(): Long {
        var result = 0L
        var shift = 0
        var chunk: Int
        do {
            require(index < points.length && shift <= 30) { "Invalid polyline" }
            chunk = points[index++].code - 63
            require(chunk in 0..63) { "Invalid polyline character" }
            result = result or ((chunk and 31).toLong() shl shift)
            shift += 5
        } while (chunk >= 32)
        return if (result and 1L != 0L) (result shr 1).inv() else result shr 1
    }
    return buildList {
        while (index < points.length) {
            latitude += component()
            longitude += component()
            add(GeoPoint(latitude / scale, longitude / scale))
        }
    }
}
