package me.matsumo.fankt.fanbox.datasource.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.Dispatchers
import me.matsumo.fankt.fanbox.domain.model.db.FanktDatabase
import me.matsumo.fankt.fanbox.fanktApplicationContext

internal actual fun getCookieDatabaseBuilder(): RoomDatabase.Builder<FanktDatabase> {
    val dbFile = fanktApplicationContext.getDatabasePath("fankt.db")
    val databaseBuilder = Room.databaseBuilder<FanktDatabase>(
        context = fanktApplicationContext,
        name = dbFile.absolutePath,
    )

    return databaseBuilder
}

private val databaseInstance by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    getCookieDatabaseBuilder()
        .addMigrations(COOKIE_MIGRATION_1_2)
        .fallbackToDestructiveMigrationOnDowngrade(false)
        .setDriver(AndroidSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}

internal actual fun getFanktDatabase(): FanktDatabase {
    return databaseInstance
}
