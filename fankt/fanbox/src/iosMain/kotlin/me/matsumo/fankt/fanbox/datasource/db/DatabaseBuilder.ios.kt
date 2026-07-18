package me.matsumo.fankt.fanbox.datasource.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.NativeSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import me.matsumo.fankt.fanbox.domain.model.db.FanktDatabase
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
internal actual fun getCookieDatabaseBuilder(): RoomDatabase.Builder<FanktDatabase> {
    return Room.databaseBuilder<FanktDatabase>(getLegacyRoomDatabasePath())
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun getLegacyRoomDatabasePath(): String {
    val documentDir = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return "${documentDir?.path}/fankt.db"
}

internal actual fun getFanktDatabaseDriver(): SQLiteDriver = NativeSQLiteDriver()

private val databaseInstance by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    buildFanktDatabase()
}

internal actual fun getFanktDatabase(): FanktDatabase = databaseInstance

@OptIn(ExperimentalForeignApi::class)
internal actual fun deleteLegacyRoomDatabaseFiles(): Boolean {
    val databasePath = getLegacyRoomDatabasePath()
    var allDeleted = true
    for (path in listOf(databasePath, "$databasePath-wal", "$databasePath-shm", "$databasePath-journal")) {
        if (
            NSFileManager.defaultManager.fileExistsAtPath(path) &&
            !NSFileManager.defaultManager.removeItemAtPath(path, error = null)
        ) {
            allDeleted = false
        }
    }
    return allDeleted
}
