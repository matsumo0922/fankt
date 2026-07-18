package me.matsumo.fankt.fanbox.datasource.db

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import me.matsumo.fankt.fanbox.domain.model.db.FanktDatabase
import kotlin.coroutines.CoroutineContext

internal fun buildFanktDatabase(
    builder: RoomDatabase.Builder<FanktDatabase>,
    driver: SQLiteDriver,
    queryCoroutineContext: CoroutineContext = Dispatchers.IO,
): FanktDatabase = builder
    .addMigrations(COOKIE_MIGRATION_1_2, COOKIE_MIGRATION_2_3)
    .fallbackToDestructiveMigrationOnDowngrade(false)
    .setDriver(driver)
    .setQueryCoroutineContext(queryCoroutineContext)
    .build()
