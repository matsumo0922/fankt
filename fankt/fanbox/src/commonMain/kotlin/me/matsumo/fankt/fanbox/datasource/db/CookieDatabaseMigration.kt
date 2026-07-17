package me.matsumo.fankt.fanbox.datasource.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal val COOKIE_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        COOKIE_MIGRATION_1_2_STATEMENTS.forEach(connection::execSQL)
    }
}

internal val COOKIE_MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        COOKIE_MIGRATION_2_3_STATEMENTS.forEach(connection::execSQL)
    }
}

internal val COOKIE_MIGRATION_1_2_STATEMENTS = listOf(
    """
    CREATE TABLE IF NOT EXISTS `fankt_cookies_new` (
        `domain` TEXT NOT NULL,
        `path` TEXT NOT NULL,
        `name` TEXT NOT NULL,
        `value` TEXT NOT NULL,
        `expiresAt` INTEGER,
        `secure` INTEGER NOT NULL,
        PRIMARY KEY(`domain`, `path`, `name`)
    )
    """.trimIndent(),
    """
    INSERT INTO `fankt_cookies_new` (`domain`, `path`, `name`, `value`, `expiresAt`, `secure`)
    SELECT
        lower(ltrim(cookie.`domain`, '.')),
        cookie.`path`,
        cookie.`name`,
        cookie.`value`,
        CASE WHEN cookie.`expiresAt` = -1 THEN NULL ELSE cookie.`expiresAt` END,
        1
    FROM `fankt_cookies` AS cookie
    WHERE cookie.rowid = (
        SELECT MAX(candidate.rowid)
        FROM `fankt_cookies` AS candidate
        WHERE lower(ltrim(candidate.`domain`, '.')) = lower(ltrim(cookie.`domain`, '.'))
            AND candidate.`path` = cookie.`path`
            AND candidate.`name` = cookie.`name`
    )
    """.trimIndent(),
    "DROP TABLE `fankt_cookies`",
    "ALTER TABLE `fankt_cookies_new` RENAME TO `fankt_cookies`",
    "CREATE INDEX IF NOT EXISTS `index_fankt_cookies_expiresAt` " +
        "ON `fankt_cookies` (`expiresAt`)",
)

internal val COOKIE_MIGRATION_2_3_STATEMENTS = listOf(
    "DROP TABLE IF EXISTS `fankt_csrf_tokens`",
)
