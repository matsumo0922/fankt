package me.matsumo.fankt.fanbox.persistence.room

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import me.matsumo.fankt.fanbox.FanboxCookieRecord
import me.matsumo.fankt.fanbox.FanboxCookieStorage
import me.matsumo.fankt.fanbox.datasource.db.PersistentCookieStorage
import me.matsumo.fankt.fanbox.domain.model.db.FanktDatabase

/**
 * Explicitly created Room-backed storage for FANBOX Cookie records.
 *
 * This instance owns one database connection. [close] is idempotent, and operations started after
 * close fail with [IllegalStateException].
 *
 * A collection of [cookies] terminates with [IllegalStateException] when [close] is the first
 * terminal event to reach it, and a [Flow] obtained before close fails the same way when it is first
 * collected after close. A terminal event raised inside the collection wins instead: cancelling the
 * collecting coroutine surfaces its cancellation, an exception thrown by the collector itself
 * propagates unchanged, and a database failure delivered while this storage is still open propagates
 * unchanged. [close] does not wait for a collection to unwind.
 *
 * [close] neither suspends nor preempts a running collector, so a collector that throws, or a
 * cancellation that arrives, after close but before that collection observes the close still
 * determines the observed cause. Reporting close instead would have to discard the host's own
 * exception or swallow a cancellation. Do not depend on the cause in that window.
 *
 * Do not call [close] from a coroutine context that cannot make progress concurrently with this
 * storage's query dispatcher, such as a single-parallelism dispatcher also passed as the storage's
 * `ioDispatcher`. Room's close barrier waits for in-flight database work to release, and that work
 * cannot resume on a dispatcher occupied by the [close] call itself.
 *
 * Creating and closing [me.matsumo.fankt.fanbox.Fanbox] instances over this storage neither closes
 * it nor disturbs an established collection; only the host closes it. Create another storage to
 * reopen the unchanged platform `fankt.db` file. Schema version 3 stores every record as a domain
 * Cookie, so loaded and written records expose `hostOnly = false`.
 */
public class RoomFanboxCookieStorage internal constructor(
    private val database: FanktDatabase,
    private val backend: FanboxCookieStorage,
) : FanboxCookieStorage, AutoCloseable {
    private val closeSignal = CompletableDeferred<Unit>()
    private val closeAwareCookies = closeAwareFlow(backend.cookies, closeSignal)

    override val cookies: Flow<List<FanboxCookieRecord>>
        get() = requireOpen(closeAwareCookies)

    override suspend fun snapshot(): List<FanboxCookieRecord> = requireOpen { backend.snapshot() }

    override suspend fun upsert(cookie: FanboxCookieRecord): Unit = requireOpen { backend.upsert(cookie) }

    override suspend fun delete(domain: String, path: String, name: String): Unit =
        requireOpen { backend.delete(domain, path, name) }

    override suspend fun deleteExpired(nowEpochMilliseconds: Long): Unit =
        requireOpen { backend.deleteExpired(nowEpochMilliseconds) }

    override suspend fun replaceAll(cookies: List<FanboxCookieRecord>): Unit =
        requireOpen { backend.replaceAll(cookies) }

    override suspend fun clear(): Unit = requireOpen { backend.clear() }

    /**
     * Closes the owned database. A later call does nothing.
     *
     * Completing the close signal marks this storage closed before the database itself closes, so a
     * collection started afterwards cannot reach the database. A collection already running observes
     * that signal on its own turn, as described on this class.
     */
    override fun close() {
        if (!closeSignal.complete(Unit)) return
        database.close()
    }

    private fun <T> requireOpen(value: T): T {
        check(!closeSignal.isCompleted) { CLOSED_MESSAGE }
        return value
    }

    private suspend fun <T> requireOpen(block: suspend () -> T): T {
        check(!closeSignal.isCompleted) { CLOSED_MESSAGE }
        return block()
    }
}

/**
 * Wraps [upstream] so that completing [closeSignal] terminates a collection with
 * [IllegalStateException].
 *
 * The returned flow rechecks [closeSignal] at collection start, so a collection that begins after
 * close never subscribes to [upstream]. While collecting, the termination branch wins as soon as the
 * signal completes. An [upstream] failure arriving after the signal completes becomes the same
 * [IllegalStateException], because cancelling the upstream branch is asynchronous and the observed
 * cause must not depend on which side of that race wins; a failure arriving while the signal is
 * incomplete propagates unchanged.
 *
 * `catch` receives neither the collecting coroutine's own cancellation nor an exception thrown by
 * the downstream collector, so neither is rewritten.
 */
internal fun <T> closeAwareFlow(
    upstream: Flow<T>,
    closeSignal: CompletableDeferred<Unit>,
): Flow<T> = flow {
    check(!closeSignal.isCompleted) { CLOSED_MESSAGE }
    emitAll(
        merge(
            upstream.catch { failure -> throw upstreamCause(failure, closeSignal.isCompleted) },
            closeTerminationFlow(closeSignal),
        ),
    )
}

/**
 * Chooses the cause an upstream [failure] surfaces as, given whether the close signal has completed.
 *
 * A failure that arrives after close is reported as a close failure so the observed cause does not
 * depend on whether the close termination branch or the cancelled upstream branch wins that race.
 */
internal fun upstreamCause(failure: Throwable, closed: Boolean): Throwable =
    if (closed) closedFailure() else failure

private fun closeTerminationFlow(closeSignal: CompletableDeferred<Unit>): Flow<Nothing> = flow {
    closeSignal.await()
    throw closedFailure()
}

private fun closedFailure(): IllegalStateException = IllegalStateException(CLOSED_MESSAGE)

internal const val CLOSED_MESSAGE: String = "Room FANBOX Cookie storage is closed"

internal fun newRoomFanboxCookieStorage(
    database: FanktDatabase,
    ioDispatcher: CoroutineDispatcher,
): RoomFanboxCookieStorage = RoomFanboxCookieStorage(
    database = database,
    backend = PersistentCookieStorage(database.cookieDao(), ioDispatcher),
)
