package me.matsumo.fankt.fanbox.persistence.room

import androidx.room.Room
import androidx.sqlite.driver.NativeSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import me.matsumo.fankt.fanbox.datasource.db.buildFanktDatabase
import me.matsumo.fankt.fanbox.domain.model.db.FanktDatabase
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * Opens Room-backed FANBOX Cookie storage at the existing Documents-directory `fankt.db` path.
 *
 * The returned storage owns its database instance. Close every [me.matsumo.fankt.fanbox.Fanbox]
 * using it before closing the storage.
 */
public fun createRoomFanboxCookieStorage(
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): RoomFanboxCookieStorage {
    val database = buildFanktDatabase(
        builder = Room.databaseBuilder<FanktDatabase>(roomFanboxDatabasePath()),
        driver = NativeSQLiteDriver(),
        queryCoroutineContext = ioDispatcher,
    )
    return newRoomFanboxCookieStorage(database, ioDispatcher)
}

@OptIn(ExperimentalForeignApi::class)
internal fun roomFanboxDatabasePath(): String {
    val documentDir = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return "${requireNotNull(documentDir?.path)}/fankt.db"
}
