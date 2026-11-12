package app.itivio.domain

import kotlinx.coroutines.flow.Flow

/**
 * Keeps the journey options between app starts. The options stay on the device; they go to the
 * routing provider only as part of a journey request.
 */
interface JourneyOptionsRepository {
    val options: Flow<JourneyOptions>

    suspend fun save(options: JourneyOptions)
}
