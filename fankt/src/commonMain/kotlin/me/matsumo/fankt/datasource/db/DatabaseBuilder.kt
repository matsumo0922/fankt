package me.matsumo.fankt.datasource.db

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import me.matsumo.fankt.domain.model.db.CookieDatabase

internal expect fun getCookieDatabaseBuilder(): RoomDatabase.Builder<CookieDatabase>

internal fun getCookieDatabase(): CookieDatabase {
    return getCookieDatabaseBuilder()
        .fallbackToDestructiveMigrationOnDowngrade(false)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}