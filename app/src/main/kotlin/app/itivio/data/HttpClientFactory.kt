package app.itivio.data

import app.itivio.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal val transitousJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

internal fun userAgent(version: String, contact: String): String =
    if (contact.isBlank()) "itivio/$version (Android)" else "itivio/$version (+$contact)"

fun createHttpClient(
    engine: HttpClientEngine = OkHttp.create(),
    version: String = BuildConfig.VERSION_NAME,
    contact: String = BuildConfig.CONTACT,
): HttpClient = HttpClient(engine) {
    expectSuccess = false
    install(ContentNegotiation) { json(transitousJson) }
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 30_000
    }
    defaultRequest {
        url("https://api.transitous.org/api/")
        header(HttpHeaders.UserAgent, userAgent(version, contact))
    }
}
