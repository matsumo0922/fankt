package me.matsumo.fankt.fanbox.persistence.room

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import me.matsumo.fankt.fanbox.FanboxCookieRecord
import me.matsumo.fankt.fanbox.FanboxCookieStorage
import me.matsumo.fankt.fanbox.datasource.db.PersistentCookieStorage
import me.matsumo.fankt.fanbox.domain.model.db.FanktDatabase

/**
 * Explicitly created Room-backed storage for FANBOX Cookie records.
 *
 * This instance owns one database connection. [close] is idempotent, and operations started after
 * close fail. A [Flow] obtained before close remains backed by Room and can terminate with an
 * underlying database exception if it is collected after close. Callers must not race [close] with
 * storage operations. Create another storage to reopen the unchanged platform `fankt.db` file.
 * Schema version 3 stores every record as a domain Cookie, so loaded and written records expose
 * `hostOnly = false`.
 */
public class RoomFanboxCookieStorage internal constructor(
    private val database: FanktDatabase,
    private val backend: FanboxCookieStorage,
) : FanboxCookieStorage, AutoCloseable {
    private var closed: Boolean = false

    override val cookies: Flow<List<FanboxCookieRecord>>
        get() = requireOpen(backend.cookies)

    override suspend fun snapshot(): List<FanboxCookieRecord> = requireOpen { backend.snapshot() }

    override suspend fun upsert(cookie: FanboxCookieRecord): Unit = requireOpen { backend.upsert(cookie) }

    override suspend fun delete(domain: String, path: String, name: String): Unit =
        requireOpen { backend.delete(domain, path, name) }

    override suspend fun deleteExpired(nowEpochMilliseconds: Long): Unit =
        requireOpen { backend.deleteExpired(nowEpochMilliseconds) }

    override suspend fun replaceAll(cookies: List<FanboxCookieRecord>): Unit =
        requireOpen { backend.replaceAll(cookies) }

    override suspend fun clear(): Unit = requireOpen { backend.clear() }

    override fun close() {
        if (closed) return
        closed = true
        database.close()
    }

    private fun <T> requireOpen(value: T): T {
        check(!closed) { "Room FANBOX Cookie storage is closed" }
        return value
    }

    private suspend fun <T> requireOpen(block: suspend () -> T): T {
        check(!closed) { "Room FANBOX Cookie storage is closed" }
        return block()
    }
}

internal fun newRoomFanboxCookieStorage(
    database: FanktDatabase,
    ioDispatcher: CoroutineDispatcher,
): RoomFanboxCookieStorage = RoomFanboxCookieStorage(
    database = database,
    backend = PersistentCookieStorage(database.cookieDao(), ioDispatcher),
)
