package app.itivio.ui

import app.itivio.domain.JourneyOptions
import app.itivio.domain.JourneyOptionsRepository
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeJourneyOptionsRepository(initial: JourneyOptions = JourneyOptions()) : JourneyOptionsRepository {
    val stored = MutableStateFlow(initial)
    var failing = false
    var readGate: CompletableDeferred<Unit>? = null

    override val options: Flow<JourneyOptions> = stored.map { readGate?.await(); if (failing) throw IOException("storage") else it }

    override suspend fun save(options: JourneyOptions) {
        if (failing) throw IOException("storage")
        stored.value = options
    }
}
