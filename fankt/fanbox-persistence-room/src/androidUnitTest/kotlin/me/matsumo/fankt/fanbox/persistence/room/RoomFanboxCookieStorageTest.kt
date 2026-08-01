package me.matsumo.fankt.fanbox.persistence.room

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.sql.DriverManager
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
class RoomFanboxCookieStorageTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun opensAllLegacyVersionsAndRestoresSessionThroughFanbox() {
        verifyRoomStorageVersions(
            cleanupFixture = ::cleanupFixture,
            createFixture = ::createFixture,
            createStorage = { createRoomFanboxCookieStorage(context) },
        )
    }

    @Test
    fun terminatesCollectionsAtCloseAndOutlivesClientCycles() {
        verifyRoomStorageCloseContract(
            cleanupFixture = ::cleanupFixture,
            createFixture = ::createFixture,
            createStorage = { createRoomFanboxCookieStorage(context) },
        )
    }

    private fun createFixture(version: Int) {
        val path = roomFanboxDatabasePath(context)
        checkNotNull(File(path).parentFile).mkdirs()
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
            fixtureStatements(version).forEach { sql ->
                connection.createStatement().use { it.execute(sql) }
            }
        }
    }

    private fun cleanupFixture() {
        val database = File(roomFanboxDatabasePath(context))
        databaseSidecars(database).forEach { file ->
            check(!file.exists() || file.delete()) { "Unable to delete test database file: $file" }
        }
    }

    private fun databaseSidecars(database: File): List<File> = listOf(
        database,
        File("${database.path}-wal"),
        File("${database.path}-shm"),
        File("${database.path}-journal"),
    )
}
