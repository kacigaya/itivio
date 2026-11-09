package app.itivio.ui.common

import app.itivio.domain.RoutingException
import kotlinx.coroutines.CancellationException

enum class Failure { OFFLINE, SERVER, UNEXPECTED, NO_LOCATION }

sealed interface LoadState<out T> {
    data object Idle : LoadState<Nothing>
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
    data class Failed(val failure: Failure) : LoadState<Nothing>
}

fun Throwable.toFailure(): Failure = when (this) {
    is CancellationException -> throw this
    is RoutingException.Offline -> Failure.OFFLINE
    is RoutingException.Server -> Failure.SERVER
    else -> Failure.UNEXPECTED
}
