package me.matsumo.fankt.datasource.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.matsumo.fankt.domain.model.db.CSRFToken

@Dao
internal interface TokenDao {

    @Query("SELECT * FROM fankt_csrf_tokens")
    suspend fun getAllTokens(): List<CSRFToken>

    @Query("SELECT * FROM fankt_csrf_tokens ORDER BY createdAt DESC LIMIT 1")
    fun getLatestToken(): Flow<CSRFToken?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(token: CSRFToken)

    @Query("DELETE FROM fankt_csrf_tokens WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM fankt_csrf_tokens")
    suspend fun clear()
}
