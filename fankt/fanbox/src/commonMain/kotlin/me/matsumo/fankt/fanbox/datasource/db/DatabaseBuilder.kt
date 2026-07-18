package me.matsumo.fankt.fanbox.datasource.db

import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import me.matsumo.fankt.fanbox.domain.model.db.FanktDatabase

internal expect fun getCookieDatabaseBuilder(): RoomDatabase.Builder<FanktDatabase>

internal expect fun getFanktDatabase(): FanktDatabase

internal expect fun deleteLegacyRoomDatabaseFiles(): Boolean

internal expect fun getLegacyRoomDatabasePath(): String

internal fun buildFanktDatabase(): FanktDatabase = getCookieDatabaseBuilder()
    .addMigrations(COOKIE_MIGRATION_1_2, COOKIE_MIGRATION_2_3)
    .fallbackToDestructiveMigrationOnDowngrade(false)
    .setDriver(getFanktDatabaseDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()

internal expect fun getFanktDatabaseDriver(): androidx.sqlite.SQLiteDriver
