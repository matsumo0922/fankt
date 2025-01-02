package me.matsumo.fankt.datasource.db

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import me.matsumo.fankt.domain.model.db.CookieDatabase
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
internal actual fun getCookieDatabaseBuilder(): RoomDatabase.Builder<CookieDatabase> {
    val documentDir = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    val dbFilePath = "${documentDir?.path}/fankt_cookies.db"

    return Room.databaseBuilder<CookieDatabase>(dbFilePath)
}