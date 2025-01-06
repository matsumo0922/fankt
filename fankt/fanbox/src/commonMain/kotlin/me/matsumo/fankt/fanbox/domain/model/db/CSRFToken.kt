package me.matsumo.fankt.fanbox.domain.model.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fankt_csrf_tokens")
internal data class CSRFToken(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val value: String,
    val createdAt: Long,
)
