package me.matsumo.fankt.fanbox.datasource.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.matsumo.fankt.fanbox.domain.model.db.CookieEntity

@Dao
internal interface CookieDao {

    @Query("SELECT * FROM fankt_cookies")
    fun getAllCookies(): Flow<List<CookieEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cookie: CookieEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cookies: List<CookieEntity>)

    @Query("DELETE FROM fankt_cookies WHERE domain = :domain AND path = :path AND name = :name")
    suspend fun delete(domain: String, path: String, name: String)

    @Query("DELETE FROM fankt_cookies WHERE expiresAt IS NOT NULL AND expiresAt <= :nowEpochMilliseconds")
    suspend fun deleteExpired(nowEpochMilliseconds: Long)

    @Query("DELETE FROM fankt_cookies")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(cookies: List<CookieEntity>) {
        clear()
        insertAll(cookies)
    }
}
