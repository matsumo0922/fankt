package me.matsumo.fankt.fanbox.domain.model.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import me.matsumo.fankt.fanbox.datasource.db.CookieDao

@Database(entities = [CookieEntity::class], version = 3)
@ConstructedBy(CookieDatabaseConstructor::class)
internal abstract class FanktDatabase : RoomDatabase() {
    abstract fun cookieDao(): CookieDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
internal expect object CookieDatabaseConstructor : RoomDatabaseConstructor<FanktDatabase> {
    override fun initialize(): FanktDatabase
}
