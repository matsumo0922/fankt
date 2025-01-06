package me.matsumo.fankt.fanbox.datasource.db

import androidx.room.RoomDatabase
import me.matsumo.fankt.fanbox.domain.model.db.FanktDatabase

internal expect fun getCookieDatabaseBuilder(): RoomDatabase.Builder<FanktDatabase>

internal expect fun getFanktDatabase(): FanktDatabase
