package me.matsumo.fankt.domain.model.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import me.matsumo.fankt.datasource.db.CookieDao
import me.matsumo.fankt.datasource.db.TokenDao

@Database(entities = [CookieEntity::class, CSRFToken::class], version = 1)
@ConstructedBy(CookieDatabaseConstructor::class)
internal abstract class FanktDatabase: RoomDatabase() {
    abstract fun cookieDao(): CookieDao
    abstract fun tokenDao(): TokenDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
internal expect object CookieDatabaseConstructor : RoomDatabaseConstructor<FanktDatabase> {
    override fun initialize(): FanktDatabase
}
