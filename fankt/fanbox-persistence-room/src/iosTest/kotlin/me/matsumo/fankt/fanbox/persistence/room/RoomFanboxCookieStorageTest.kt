package me.matsumo.fankt.fanbox.persistence.room

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.cinterop.ExperimentalForeignApi
import me.matsumo.fankt.fanbox.FanboxGuestCache
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import kotlin.test.Test

class RoomFanboxCookieStorageTest {
    private val guestCache = FanboxGuestCache(NSTemporaryDirectory() + "fanbox-guest")

    @Test
    fun opensAllLegacyVersionsAndRestoresSessionThroughFanbox() {
        verifyRoomStorageVersions(
            cleanupFixture = ::cleanupFixture,
            createFixture = ::createFixture,
            createStorage = ::createRoomFanboxCookieStorage,
            guestCache = guestCache,
        )
    }

    @Test
    fun terminatesCollectionsAtCloseAndOutlivesClientCycles() {
        verifyRoomStorageCloseContract(
            cleanupFixture = ::cleanupFixture,
            createFixture = ::createFixture,
            createStorage = ::createRoomFanboxCookieStorage,
            guestCache = guestCache,
        )
    }

    private fun createFixture(version: Int) {
        val connection = BundledSQLiteDriver().open(roomFanboxDatabasePath())
        try {
            fixtureStatements(version).forEach(connection::execSQL)
        } finally {
            connection.close()
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun cleanupFixture() {
        val databasePath = roomFanboxDatabasePath()
        databaseSidecars(databasePath).forEach { path ->
            if (NSFileManager.defaultManager.fileExistsAtPath(path)) {
                check(NSFileManager.defaultManager.removeItemAtPath(path, error = null)) { "Unable to delete test database file: $path" }
            }
        }
    }

    private fun databaseSidecars(databasePath: String): List<String> = listOf(
        databasePath,
        "$databasePath-wal",
        "$databasePath-shm",
        "$databasePath-journal",
    )
}
