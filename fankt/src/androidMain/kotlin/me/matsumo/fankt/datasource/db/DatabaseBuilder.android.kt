package me.matsumo.fankt.datasource.db

import androidx.room.Room
import androidx.room.RoomDatabase
import me.matsumo.fankt.applicationContext
import me.matsumo.fankt.domain.model.db.CookieDatabase

internal actual fun getCookieDatabaseBuilder(): RoomDatabase.Builder<CookieDatabase> {
    val dbFile = applicationContext.getDatabasePath("fankt_cookies.db")
    val databaseBuilder = Room.databaseBuilder<CookieDatabase>(
        context = applicationContext,
        name = dbFile.absolutePath,
    )

    return databaseBuilder
}