package me.matsumo.fankt.fanbox.persistence.room

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import me.matsumo.fankt.fanbox.datasource.db.buildFanktDatabase
import me.matsumo.fankt.fanbox.domain.model.db.FanktDatabase

/**
 * Opens Room-backed FANBOX Cookie storage at this application's existing `fankt.db` path.
 *
 * The returned storage owns its database instance. Close every [me.matsumo.fankt.fanbox.Fanbox]
 * using it before closing the storage.
 */
public fun createRoomFanboxCookieStorage(
    context: Context,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): RoomFanboxCookieStorage {
    val applicationContext = context.applicationContext
    val database = buildFanktDatabase(
        builder = Room.databaseBuilder<FanktDatabase>(
            context = applicationContext,
            name = roomFanboxDatabasePath(applicationContext),
        ),
        driver = AndroidSQLiteDriver(),
        queryCoroutineContext = ioDispatcher,
    )
    return newRoomFanboxCookieStorage(database, ioDispatcher)
}

internal fun roomFanboxDatabasePath(context: Context): String =
    context.applicationContext.getDatabasePath(DATABASE_NAME).absolutePath

private const val DATABASE_NAME: String = "fankt.db"
