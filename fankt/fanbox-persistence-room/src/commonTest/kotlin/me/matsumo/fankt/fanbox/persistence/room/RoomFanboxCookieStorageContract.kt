package me.matsumo.fankt.fanbox.persistence.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.matsumo.fankt.fanbox.Fanbox
import me.matsumo.fankt.fanbox.FanboxCookieRecord
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal fun verifyRoomStorageVersions(
    cleanupFixture: () -> Unit,
    createFixture: (Int) -> Unit,
    createStorage: () -> RoomFanboxCookieStorage,
) = runBlocking {
    for (version in 1..3) {
        cleanupFixture()
        createFixture(version)

        val first = createStorage()
        val snapshot = first.snapshot()
        assertEquals("legacy-session-v$version", snapshot.single().value)
        assertFalse(snapshot.single().hostOnly)
        first.upsert(snapshot.single().copy(name = "coerced", hostOnly = true))
        assertFalse(first.snapshot().single { it.name == "coerced" }.hostOnly)
        val independentlyOwned = createStorage()
        first.close()
        first.close()
        assertEquals(2, independentlyOwned.snapshot().size)
        independentlyOwned.close()
        assertFailsWith<IllegalStateException> { first.cookies }
        assertFailsWith<IllegalStateException> { first.snapshot() }

        val reacquired = createStorage()
        assertEquals(2, reacquired.snapshot().size)
        val fanbox = Fanbox(cookieStorage = reacquired)
        val restoredSession = fanbox.cookies.first().single { it.name == "FANBOXSESSID" }
        assertEquals("legacy-session-v$version", restoredSession.value)
        assertEquals("fanbox.cc", restoredSession.domain)
        fanbox.close()
        reacquired.clear()
        assertTrue(reacquired.snapshot().isEmpty())
        reacquired.close()
        cleanupFixture()
    }
}

/**
 * Verifies the close contract of a storage over a real database on the calling platform.
 *
 * Every collection runs in a child that captures its own outcome as a value, so a termination
 * failure cannot cancel this scope before it is asserted, and every wait is bounded so a collection
 * that hangs fails the test instead of the suite.
 */
internal fun verifyRoomStorageCloseContract(
    cleanupFixture: () -> Unit,
    createFixture: (Int) -> Unit,
    createStorage: () -> RoomFanboxCookieStorage,
) = runBlocking {
    cleanupFixture()
    createFixture(3)

    verifyLiveCollectionTerminatesAtClose(createStorage)
    verifyCapturedFlowFailsAfterClose(createStorage)
    verifyFanboxCollectionTerminatesAtStorageClose(createStorage)
    verifyStorageOutlivesClientCycles(createStorage)

    cleanupFixture()
}

private suspend fun CoroutineScope.verifyLiveCollectionTerminatesAtClose(
    createStorage: () -> RoomFanboxCookieStorage,
) {
    val storage = createStorage()
    val observed = Channel<List<FanboxCookieRecord>>(Channel.UNLIMITED)
    val collection = collectStorageOutcome(storage.cookies, observed)

    observed.awaitRecordNamed("FANBOXSESSID")
    storage.upsert(cookieRecord("live-marker"))
    observed.awaitRecordNamed("live-marker")

    storage.close()

    val failure = collection.awaitFailure()
    assertIs<IllegalStateException>(failure)
    storage.close()
}

private suspend fun CoroutineScope.verifyCapturedFlowFailsAfterClose(
    createStorage: () -> RoomFanboxCookieStorage,
) {
    val storage = createStorage()
    val captured = storage.cookies
    storage.close()

    assertFailsWith<IllegalStateException> {
        withTimeout(CONTRACT_TIMEOUT_MILLISECONDS) { captured.first() }
    }
}

private suspend fun CoroutineScope.verifyFanboxCollectionTerminatesAtStorageClose(
    createStorage: () -> RoomFanboxCookieStorage,
) {
    val storage = createStorage()
    val fanbox = Fanbox(cookieStorage = storage)
    val observed = Channel<List<FanboxCookieRecord>>(Channel.UNLIMITED)
    val collection = collectStorageOutcome(fanbox.cookies, observed)

    observed.awaitRecordNamed("FANBOXSESSID")

    storage.close()

    assertIs<IllegalStateException>(collection.awaitFailure())
    fanbox.close()
}

private suspend fun CoroutineScope.verifyStorageOutlivesClientCycles(
    createStorage: () -> RoomFanboxCookieStorage,
) {
    val storage = createStorage()
    val observed = Channel<List<FanboxCookieRecord>>(Channel.UNLIMITED)
    val collection = collectStorageOutcome(storage.cookies, observed)

    observed.awaitRecordNamed("FANBOXSESSID")

    repeat(CLIENT_CYCLES) { Fanbox(cookieStorage = storage).close() }

    // A delivered value, rather than an active Job, is what shows the collection is not suspended
    // forever after the cycles.
    storage.upsert(cookieRecord("post-cycle-marker"))
    observed.awaitRecordNamed("post-cycle-marker")

    assertTrue(storage.snapshot().any { it.name == "post-cycle-marker" })
    storage.delete("fanbox.cc", "/", "post-cycle-marker")

    storage.close()
    assertIs<IllegalStateException>(collection.awaitFailure())
}

private fun CoroutineScope.collectStorageOutcome(
    cookies: Flow<List<FanboxCookieRecord>>,
    observed: Channel<List<FanboxCookieRecord>>,
): Deferred<Throwable?> = async(start = CoroutineStart.UNDISPATCHED) {
    runCatching { cookies.collect { records -> observed.send(records) } }.exceptionOrNull()
}

private suspend fun Deferred<Throwable?>.awaitFailure(): Throwable =
    checkNotNull(withTimeout(CONTRACT_TIMEOUT_MILLISECONDS) { await() }) {
        "The collection completed without failing"
    }

private suspend fun Channel<List<FanboxCookieRecord>>.awaitRecordNamed(name: String) {
    withTimeout(CONTRACT_TIMEOUT_MILLISECONDS) {
        while (receive().none { record -> record.name == name }) {
            // Room emits the current snapshot, so an earlier emission can predate this write.
        }
    }
}

private fun cookieRecord(name: String): FanboxCookieRecord = FanboxCookieRecord(
    domain = "fanbox.cc",
    path = "/",
    name = name,
    value = "marker",
    expiresAtEpochMilliseconds = null,
    secure = true,
    hostOnly = false,
)

private const val CLIENT_CYCLES: Int = 5

private const val CONTRACT_TIMEOUT_MILLISECONDS: Long = 10_000

internal fun fixtureStatements(version: Int): List<String> = buildList {
    if (version == 1) {
        add("CREATE TABLE fankt_cookies (id TEXT NOT NULL PRIMARY KEY, domain TEXT NOT NULL, path TEXT NOT NULL, name TEXT NOT NULL, value TEXT NOT NULL, expiresAt INTEGER NOT NULL)")
        add("CREATE TABLE fankt_csrf_tokens (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, value TEXT NOT NULL, createdAt INTEGER NOT NULL)")
        add("INSERT INTO fankt_cookies VALUES ('fixture', '.FANBOX.CC', '/', 'FANBOXSESSID', 'legacy-session-v1', -1)")
    } else {
        add("CREATE TABLE fankt_cookies (domain TEXT NOT NULL, path TEXT NOT NULL, name TEXT NOT NULL, value TEXT NOT NULL, expiresAt INTEGER, secure INTEGER NOT NULL, PRIMARY KEY(domain, path, name))")
        add("CREATE INDEX index_fankt_cookies_expiresAt ON fankt_cookies(expiresAt)")
        if (version == 2) {
            add("CREATE TABLE fankt_csrf_tokens (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, value TEXT NOT NULL, createdAt INTEGER NOT NULL)")
        }
        add("INSERT INTO fankt_cookies VALUES ('fanbox.cc', '/', 'FANBOXSESSID', 'legacy-session-v$version', NULL, 1)")
    }
    add("PRAGMA user_version = $version")
}
