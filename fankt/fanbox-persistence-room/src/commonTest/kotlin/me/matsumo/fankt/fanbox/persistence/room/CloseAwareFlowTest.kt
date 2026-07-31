package me.matsumo.fankt.fanbox.persistence.room

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Drives [closeAwareFlow] with a substitute upstream so that each precedence rule of the close
 * contract is observable without a real database.
 */
class CloseAwareFlowTest {

    @Test
    fun collectionStartedAfterCloseFailsWithoutSubscribingUpstream() = runBlocking {
        val upstream = SubscriptionCountingFlow<Int>()
        val closeSignal = CompletableDeferred<Unit>()
        val flow = closeAwareFlow(upstream.flow, closeSignal)

        closeSignal.complete(Unit)

        val failure = assertFailsWith<IllegalStateException> {
            withTimeout(TIMEOUT_MILLISECONDS) { flow.first() }
        }

        assertEquals(CLOSED_MESSAGE, failure.message)
        assertEquals(0, upstream.subscriptions)
    }

    @Test
    fun liveCollectionTerminatesWhenTheSignalCompletes() = runBlocking {
        val upstream = MutableSharedFlow<Int>(replay = 1)
        val closeSignal = CompletableDeferred<Unit>()
        val received = Channel<Int>(Channel.UNLIMITED)
        val collection = collectOutcome(closeAwareFlow(upstream, closeSignal), received)

        upstream.emit(1)
        assertEquals(1, received.receiveWithTimeout())

        closeSignal.complete(Unit)

        val failure = collection.awaitFailure()
        assertIs<IllegalStateException>(failure)
        assertEquals(CLOSED_MESSAGE, failure.message)
    }

    @Test
    fun upstreamFailureBeforeCloseIsPropagatedUnchanged() = runBlocking {
        val closeSignal = CompletableDeferred<Unit>()
        val upstream = flow<Int> { throw UpstreamFailure() }
        val collection = collectOutcome(closeAwareFlow(upstream, closeSignal), Channel(Channel.UNLIMITED))

        assertIs<UpstreamFailure>(collection.awaitFailure())
        assertTrue(!closeSignal.isCompleted)
    }

    @Test
    fun upstreamFailureAfterCloseIsReportedAsClosed() = runBlocking {
        val closeSignal = CompletableDeferred<Unit>()
        val releaseFailure = CompletableDeferred<Unit>()
        val upstream = flow<Int> {
            releaseFailure.await()
            throw UpstreamFailure()
        }
        val collection = collectOutcome(closeAwareFlow(upstream, closeSignal), Channel(Channel.UNLIMITED))

        closeSignal.complete(Unit)
        releaseFailure.complete(Unit)

        val failure = collection.awaitFailure()
        assertIs<IllegalStateException>(failure)
        assertEquals(CLOSED_MESSAGE, failure.message)
    }

    @Test
    fun collectorFailureIsPropagatedUnchanged() = runBlocking {
        val upstream = MutableSharedFlow<Int>(replay = 1)
        val closeSignal = CompletableDeferred<Unit>()
        val collection: Deferred<Throwable?> = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching {
                closeAwareFlow(upstream, closeSignal).collect { throw CollectorFailure() }
            }.exceptionOrNull()
        }

        upstream.emit(1)

        val failure = collection.awaitFailure()
        assertIs<CollectorFailure>(failure)
        assertTrue(!closeSignal.isCompleted)
    }

    @Test
    fun collectionCancellationBeforeCloseIsNotReportedAsClosed() = runBlocking {
        val upstream = MutableSharedFlow<Int>(replay = 1)
        val closeSignal = CompletableDeferred<Unit>()
        val received = Channel<Int>(Channel.UNLIMITED)
        val collection = collectOutcome(closeAwareFlow(upstream, closeSignal), received)

        upstream.emit(1)
        assertEquals(1, received.receiveWithTimeout())

        collection.cancel(CancellationException("host cancelled"))

        assertFailsWith<CancellationException> {
            withTimeout(TIMEOUT_MILLISECONDS) { collection.await() }
        }
        assertTrue(!closeSignal.isCompleted)
    }
}

private class UpstreamFailure : RuntimeException("upstream failed")

private class CollectorFailure : RuntimeException("collector failed")

/**
 * Counts how many times its [flow] is collected, so a test can assert that a closed storage never
 * reaches the database.
 */
private class SubscriptionCountingFlow<T> {
    var subscriptions: Int = 0
        private set

    val flow: Flow<T> = flow {
        subscriptions += 1
        emitAll(emptyFlow())
    }
}

/**
 * Collects [flow] in a child that captures its own outcome as a value, so a thrown
 * [IllegalStateException] cannot cancel the enclosing scope before the assertion runs.
 */
private fun <T> CoroutineScope.collectOutcome(
    flow: Flow<T>,
    received: Channel<T>,
): Deferred<Throwable?> = async(start = CoroutineStart.UNDISPATCHED) {
    runCatching {
        flow.collect { value -> received.send(value) }
    }.exceptionOrNull()
}

private suspend fun Deferred<Throwable?>.awaitFailure(): Throwable =
    checkNotNull(withTimeout(TIMEOUT_MILLISECONDS) { await() }) { "The collection completed without failing" }

private suspend fun <T> Channel<T>.receiveWithTimeout(): T =
    withTimeout(TIMEOUT_MILLISECONDS) { receive() }

private const val TIMEOUT_MILLISECONDS: Long = 5_000
