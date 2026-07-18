package me.matsumo.fankt.fanbox.persistence.room

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.matsumo.fankt.fanbox.Fanbox
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
