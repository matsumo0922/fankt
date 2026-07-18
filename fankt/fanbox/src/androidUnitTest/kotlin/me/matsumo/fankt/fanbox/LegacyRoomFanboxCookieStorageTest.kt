package me.matsumo.fankt.fanbox

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import me.matsumo.fankt.fanbox.datasource.db.getLegacyRoomDatabasePath
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.sql.DriverManager
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
class LegacyRoomFanboxCookieStorageTest {
    @Test
    fun opensAllLegacyVersionsAndSupportsVerifiedCleanup() {
        FanktInitializer().create(ApplicationProvider.getApplicationContext<Context>())
        verifyLegacyRoomBridgeVersions(::createFixture)
    }

    private fun createFixture(version: Int) {
        val path = getLegacyRoomDatabasePath()
        checkNotNull(File(path).parentFile).mkdirs()
        DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
            fixtureStatements(version).forEach { sql ->
                connection.createStatement().use { it.execute(sql) }
            }
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
