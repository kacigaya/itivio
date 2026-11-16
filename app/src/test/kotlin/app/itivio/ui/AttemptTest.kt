package app.itivio.ui

import app.itivio.ui.common.attempt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class AttemptTest {
    @Test fun preservesResultsAndFailures() = runTest {
        assertEquals(42, attempt { 42 }.getOrThrow())
        val failure = IOException("storage failed")
        assertSame(failure, attempt<Unit> { throw failure }.exceptionOrNull())
    }

    @Test fun rethrowsCancellation() = runTest {
        val cancellation = CancellationException("stopped")
        try {
            attempt<Unit> { throw cancellation }
            fail("Cancellation must escape")
        } catch (error: CancellationException) {
            assertSame(cancellation, error)
        }
    }
}
