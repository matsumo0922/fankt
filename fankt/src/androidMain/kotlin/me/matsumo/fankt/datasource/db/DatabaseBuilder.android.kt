package me.matsumo.fankt.datasource.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import me.matsumo.fankt.domain.model.db.FanktDatabase
import me.matsumo.fankt.fanktApplicationContext

internal actual fun getCookieDatabaseBuilder(): RoomDatabase.Builder<FanktDatabase> {
    val dbFile = fanktApplicationContext.getDatabasePath("fankt.db")
    val databaseBuilder = Room.databaseBuilder<FanktDatabase>(
        context = fanktApplicationContext,
        name = dbFile.absolutePath,
    )

    return databaseBuilder
}

internal actual fun getFanktDatabase(): FanktDatabase {
    return getCookieDatabaseBuilder()
        .fallbackToDestructiveMigrationOnDowngrade(false)
        .setDriver(AndroidSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
