package me.matsumo.fankt.datasource.db

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import me.matsumo.fankt.domain.model.db.FanktDatabase

internal expect fun getCookieDatabaseBuilder(): RoomDatabase.Builder<FanktDatabase>

internal expect fun getFanktDatabase(): FanktDatabase
