package me.matsumo.fankt.fanbox

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import me.matsumo.fankt.fanbox.datasource.db.PersistentCookieStorage
import me.matsumo.fankt.fanbox.datasource.db.buildFanktDatabase
import me.matsumo.fankt.fanbox.datasource.db.deleteLegacyRoomDatabaseFiles
import me.matsumo.fankt.fanbox.domain.model.db.FanktDatabase

/**
 * Marks the temporary Room migration bridge retained for existing fankt databases.
 *
 * The API is removed when PixiView-KMP's oldest supported version already includes secure session
 * migration and fankt issue #34 confirms that the legacy bridge is no longer required.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This temporary API exists only for migration from the legacy fankt Room database.",
)
public annotation class ExperimentalLegacyFanboxStorageApi

/**
 * A closeable, explicitly requested bridge to the current platform's legacy `fankt.db`.
 *
 * The bridge owns a fresh Room database instance. All records are exposed and stored as
 * domain-scoped (`hostOnly = false`) because schema version 3 cannot retain host-only ownership.
 * Call [clear], then [close], then [deleteDatabaseFiles] only after destination migration has been
 * committed and verified. Closing this bridge does not prevent a later factory call from opening a
 * fresh instance for cleanup retry.
 */
@ExperimentalLegacyFanboxStorageApi
public class LegacyRoomFanboxCookieStorage internal constructor(
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

    /** Deletes `fankt.db` and its SQLite sidecars after this bridge has been closed. */
    public fun deleteDatabaseFiles(): Boolean {
        check(closed) { "Close the legacy Room bridge before deleting database files" }
        return deleteLegacyRoomDatabaseFiles()
    }

    private fun <T> requireOpen(value: T): T {
        check(!closed) { "Legacy Room bridge is closed" }
        return value
    }

    private suspend fun <T> requireOpen(block: suspend () -> T): T {
        check(!closed) { "Legacy Room bridge is closed" }
        return block()
    }
}

/**
 * Opens a fresh, bridge-owned Room instance for explicit migration from platform `fankt.db`.
 * Default [Fanbox] construction never calls this factory.
 */
@ExperimentalLegacyFanboxStorageApi
public fun createLegacyRoomFanboxCookieStorage(
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): LegacyRoomFanboxCookieStorage {
    val database = buildFanktDatabase()
    return LegacyRoomFanboxCookieStorage(
        database = database,
        backend = PersistentCookieStorage(database.cookieDao(), ioDispatcher),
    )
}
