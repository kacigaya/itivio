package app.itivio.data.ign

import app.itivio.domain.GeoPoint
import app.itivio.domain.Location
import app.itivio.domain.LocationKind
import app.itivio.domain.RoutingException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

class IgnAddressSearch(private val client: HttpClient) {
    suspend fun search(query: String): List<Location> {
        val response = client.get("search") {
            parameter("q", query)
            parameter("index", "address")
            parameter("limit", 5)
            timeout { requestTimeoutMillis = 5_000 }
        }
        if (!response.status.isSuccess()) throw RoutingException.Server(response.status.value)
        return response.body<AddressResponse>().features.mapNotNull { feature ->
            val properties = feature.properties ?: return@mapNotNull null
            val geometry = feature.geometry ?: return@mapNotNull null
            val coordinates = geometry.coordinates
            if (geometry.type != "Point" || coordinates.size != 2 ||
                coordinates[0] !in -180.0..180.0 || coordinates[1] !in -90.0..90.0 ||
                properties.id.isBlank() || properties.name.isBlank() || properties.score < 0.5 ||
                properties.type !in setOf("housenumber", "street")
            ) return@mapNotNull null
            Location(
                id = "ign:${properties.id}",
                name = properties.name,
                kind = LocationKind.ADDRESS,
                point = GeoPoint(coordinates[1], coordinates[0]),
                area = listOf(properties.postcode, properties.city).filter { it.isNotBlank() }
                    .joinToString(" ").ifBlank { null },
            )
        }
    }

    companion object {
        private val address = Regex(
            """(^\d+\S*\s+\p{L}|\b(rue|avenue|boulevard|chemin|impasse|allée|allee|route|place|quai)\s+\p{L})""",
            RegexOption.IGNORE_CASE,
        )

        fun looksLikeAddress(query: String): Boolean = address.containsMatchIn(query)
    }
}

@Serializable
private data class AddressResponse(val features: List<AddressFeature>)

@Serializable
private data class AddressFeature(val geometry: Geometry? = null, val properties: Properties? = null)

@Serializable
private data class Geometry(val type: String, val coordinates: List<Double>)

@Serializable
private data class Properties(
    val id: String,
    val name: String,
    val postcode: String = "",
    val city: String = "",
    val type: String,
    val score: Double,
)
