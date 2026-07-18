package me.matsumo.fankt.fanbox.datasource.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver
import me.matsumo.fankt.fanbox.domain.model.db.FanktDatabase
import me.matsumo.fankt.fanbox.fanktApplicationContext

internal actual fun getCookieDatabaseBuilder(): RoomDatabase.Builder<FanktDatabase> {
    val dbFile = java.io.File(getLegacyRoomDatabasePath())
    val databaseBuilder = Room.databaseBuilder<FanktDatabase>(
        context = fanktApplicationContext,
        name = dbFile.absolutePath,
    )

    return databaseBuilder
}

internal actual fun getLegacyRoomDatabasePath(): String =
    fanktApplicationContext.getDatabasePath("fankt.db").absolutePath

internal actual fun getFanktDatabaseDriver(): SQLiteDriver = AndroidSQLiteDriver()

private val databaseInstance by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    buildFanktDatabase()
}

internal actual fun getFanktDatabase(): FanktDatabase = databaseInstance

internal actual fun deleteLegacyRoomDatabaseFiles(): Boolean {
    val database = java.io.File(getLegacyRoomDatabasePath())
    var allDeleted = true
    for (file in listOf(database, java.io.File("${database.path}-wal"), java.io.File("${database.path}-shm"), java.io.File("${database.path}-journal"))) {
        if (file.exists() && !file.delete()) allDeleted = false
    }
    return allDeleted
}
