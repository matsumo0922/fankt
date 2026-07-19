package me.matsumo.fankt.fanbox.domain.model.db

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "fankt_cookies",
    primaryKeys = ["domain", "path", "name"],
    indices = [Index("expiresAt")],
)
internal data class CookieEntity(
    val domain: String,
    val path: String,
    val name: String,
    val value: String,
    val expiresAt: Long?,
    val secure: Boolean,
)
