package me.matsumo.fankt.datasource.db

import androidx.room.Room
import androidx.room.RoomDatabase
import me.matsumo.fankt.domain.model.db.CookieDatabase
import me.matsumo.fankt.fanktApplicationContext

internal actual fun getCookieDatabaseBuilder(): RoomDatabase.Builder<CookieDatabase> {
    val dbFile = fanktApplicationContext.getDatabasePath("fankt_cookies.db")
    val databaseBuilder = Room.databaseBuilder<CookieDatabase>(
        context = fanktApplicationContext,
        name = dbFile.absolutePath,
    )

    return databaseBuilder
}