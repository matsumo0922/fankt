package me.matsumo.fankt.datasource.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.matsumo.fankt.domain.model.db.CookieEntity

@Dao
internal interface CookieDao {

    @Query("SELECT * FROM fankt_cookies")
    suspend fun getAllCookies(): List<CookieEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cookie: CookieEntity)

    @Query("DELETE FROM fankt_cookies WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM fankt_cookies")
    suspend fun clear()
}