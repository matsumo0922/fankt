package me.matsumo.fankt.fanbox

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import me.matsumo.fankt.fanbox.datasource.db.getLegacyRoomDatabasePath
import kotlin.test.Test

class LegacyRoomFanboxCookieStorageTest {
    @Test
    fun opensAllLegacyVersionsAndSupportsVerifiedCleanup() {
        verifyLegacyRoomBridgeVersions(::createFixture)
    }

    private fun createFixture(version: Int) {
        val connection = BundledSQLiteDriver().open(getLegacyRoomDatabasePath())
        try {
            fixtureStatements(version).forEach(connection::execSQL)
        } finally {
            connection.close()
        }
    }
}

private fun fixtureStatements(version: Int): List<String> = buildList {
    if (version == 1) {
        add("CREATE TABLE fankt_cookies (id TEXT NOT NULL PRIMARY KEY, domain TEXT NOT NULL, path TEXT NOT NULL, name TEXT NOT NULL, value TEXT NOT NULL, expiresAt INTEGER NOT NULL)")
        add("CREATE TABLE fankt_csrf_tokens (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, value TEXT NOT NULL, createdAt INTEGER NOT NULL)")
        add("INSERT INTO fankt_cookies VALUES ('fixture', '.FANBOX.CC', '/', 'FANBOXSESSID', 'legacy-session-v1', -1)")
    } else {
        add("CREATE TABLE fankt_cookies (domain TEXT NOT NULL, path TEXT NOT NULL, name TEXT NOT NULL, value TEXT NOT NULL, expiresAt INTEGER, secure INTEGER NOT NULL, PRIMARY KEY(domain, path, name))")
        add("CREATE INDEX index_fankt_cookies_expiresAt ON fankt_cookies(expiresAt)")
        if (version == 2) add("CREATE TABLE fankt_csrf_tokens (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, value TEXT NOT NULL, createdAt INTEGER NOT NULL)")
        add("INSERT INTO fankt_cookies VALUES ('fanbox.cc', '/', 'FANBOXSESSID', 'legacy-session-v$version', NULL, 1)")
    }
    add("PRAGMA user_version = $version")
}
