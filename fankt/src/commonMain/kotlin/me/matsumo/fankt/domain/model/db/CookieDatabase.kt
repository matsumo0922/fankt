package me.matsumo.fankt.domain.model.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import me.matsumo.fankt.datasource.db.CookieDao

@Database(entities = [CookieEntity::class], version = 1)
@ConstructedBy(CookieDatabaseConstructor::class)
internal abstract class CookieDatabase: RoomDatabase() {
    abstract fun cookieDao(): CookieDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
internal expect object CookieDatabaseConstructor : RoomDatabaseConstructor<CookieDatabase> {
    override fun initialize(): CookieDatabase
}