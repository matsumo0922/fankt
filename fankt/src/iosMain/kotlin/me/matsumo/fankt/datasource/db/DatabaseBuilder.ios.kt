package me.matsumo.fankt.datasource.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.NativeSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import me.matsumo.fankt.domain.model.db.FanktDatabase
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
internal actual fun getCookieDatabaseBuilder(): RoomDatabase.Builder<FanktDatabase> {
    val documentDir = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    val dbFilePath = "${documentDir?.path}/fankt.db"

    return Room.databaseBuilder<FanktDatabase>(dbFilePath)
}

internal actual fun getFanktDatabase(): FanktDatabase {
    return getCookieDatabaseBuilder()
        .fallbackToDestructiveMigrationOnDowngrade(false)
        .setDriver(NativeSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
