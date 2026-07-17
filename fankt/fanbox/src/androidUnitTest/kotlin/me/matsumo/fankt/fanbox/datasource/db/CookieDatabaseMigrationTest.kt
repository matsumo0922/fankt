package me.matsumo.fankt.fanbox.datasource.db

import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CookieDatabaseMigrationTest {

    @Test
    fun migrationFromVersionOneCanonicalizesCookiesAndDropsCsrfTokenInVersionThree() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createVersionOneSchema(connection)
            connection.execute(
                "INSERT INTO fankt_cookies VALUES " +
                    "('first', '.FANBOX.CC', '/', 'session', 'old', -1)",
            )
            connection.execute(
                "INSERT INTO fankt_cookies VALUES " +
                    "('second', 'fanbox.cc', '/', 'session', 'new', -1)",
            )
            connection.execute(
                "INSERT INTO fankt_cookies VALUES " +
                    "('expired', 'fanbox.cc', '/', 'expired', 'expired-value', 0)",
            )
            connection.execute(
                "INSERT INTO fankt_csrf_tokens (`value`, `createdAt`) VALUES ('csrf-value', 123)",
            )

            COOKIE_MIGRATION_1_2_STATEMENTS.forEach { sql -> connection.execute(sql) }
            COOKIE_MIGRATION_2_3_STATEMENTS.forEach { sql -> connection.execute(sql) }

            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT domain, value, expiresAt, secure FROM fankt_cookies " +
                        "WHERE name = 'session'",
                ).use { result ->
                    assertTrue(result.next())
                    assertEquals("fanbox.cc", result.getString(1))
                    assertEquals("new", result.getString(2))
                    assertEquals(null, result.getObject(3))
                    assertEquals(1, result.getInt(4))
                    assertFalse(result.next())
                }
                statement.executeQuery(
                    "SELECT expiresAt FROM fankt_cookies WHERE name = 'expired'",
                ).use { result ->
                    assertTrue(result.next())
                    assertEquals(0, result.getLong(1))
                }
                statement.executeQuery(
                    "SELECT name FROM sqlite_master " +
                        "WHERE type = 'index' AND name = 'index_fankt_cookies_expiresAt'",
                ).use { result ->
                    assertTrue(result.next())
                }
                assertFalse(connection.tableExists("fankt_csrf_tokens"))
            }
        }
    }

    @Test
    fun migrationFromVersionTwoDropsOnlyCsrfTokenTable() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createVersionTwoSchema(connection)
            connection.execute(
                "INSERT INTO fankt_cookies " +
                    "(domain, path, name, value, expiresAt, secure) VALUES " +
                    "('fanbox.cc', '/', 'FANBOXSESSID', 'session-value', NULL, 1)",
            )
            connection.execute(
                "INSERT INTO fankt_csrf_tokens (`value`, `createdAt`) VALUES ('csrf-value', 123)",
            )

            COOKIE_MIGRATION_2_3_STATEMENTS.forEach { sql -> connection.execute(sql) }

            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT domain, path, name, value, expiresAt, secure FROM fankt_cookies",
                ).use { result ->
                    assertTrue(result.next())
                    assertEquals("fanbox.cc", result.getString(1))
                    assertEquals("/", result.getString(2))
                    assertEquals("FANBOXSESSID", result.getString(3))
                    assertEquals("session-value", result.getString(4))
                    assertEquals(null, result.getObject(5))
                    assertEquals(1, result.getInt(6))
                    assertFalse(result.next())
                }
                statement.executeQuery(
                    "SELECT name FROM sqlite_master " +
                        "WHERE type = 'index' AND name = 'index_fankt_cookies_expiresAt'",
                ).use { result -> assertTrue(result.next()) }
            }
            assertFalse(connection.tableExists("fankt_csrf_tokens"))
        }
    }

    private fun createVersionOneSchema(connection: Connection) {
        connection.execute(
            """
            CREATE TABLE fankt_cookies (
                id TEXT NOT NULL PRIMARY KEY,
                domain TEXT NOT NULL,
                path TEXT NOT NULL,
                name TEXT NOT NULL,
                value TEXT NOT NULL,
                expiresAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        connection.execute(
            """
            CREATE TABLE fankt_csrf_tokens (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                value TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun createVersionTwoSchema(connection: Connection) {
        connection.execute(
            """
            CREATE TABLE fankt_cookies (
                domain TEXT NOT NULL,
                path TEXT NOT NULL,
                name TEXT NOT NULL,
                value TEXT NOT NULL,
                expiresAt INTEGER,
                secure INTEGER NOT NULL,
                PRIMARY KEY(domain, path, name)
            )
            """.trimIndent(),
        )
        connection.execute(
            "CREATE INDEX index_fankt_cookies_expiresAt ON fankt_cookies(expiresAt)",
        )
        connection.execute(
            """
            CREATE TABLE fankt_csrf_tokens (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                value TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun Connection.tableExists(name: String): Boolean {
        prepareStatement(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
        ).use { statement ->
            statement.setString(1, name)
            statement.executeQuery().use { result -> return result.next() }
        }
    }

    private fun Connection.execute(sql: String) {
        createStatement().use { it.execute(sql) }
    }
}
