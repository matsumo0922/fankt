package me.matsumo.fankt.domain.model.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fankt_cookies")
internal data class CookieEntity(
    @PrimaryKey
    val id: String,
    val domain: String,
    val path: String,
    val name: String,
    val value: String,
    val expiresAt: Long
)