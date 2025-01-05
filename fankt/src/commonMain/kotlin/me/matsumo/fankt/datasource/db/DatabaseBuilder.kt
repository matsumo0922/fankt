package me.matsumo.fankt.datasource.db

import androidx.room.RoomDatabase
import me.matsumo.fankt.domain.model.db.FanktDatabase

internal expect fun getCookieDatabaseBuilder(): RoomDatabase.Builder<FanktDatabase>

internal expect fun getFanktDatabase(): FanktDatabase
