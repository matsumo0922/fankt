package me.matsumo.fankt.fanbox.domain.model.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.ktor.http.Cookie
import io.ktor.util.date.GMTDate

@Entity(tableName = "fankt_cookies")
internal data class CookieEntity(
    @PrimaryKey
    val id: String,
    val domain: String,
    val path: String,
    val name: String,
    val value: String,
    val expiresAt: Long,
)

internal fun CookieEntity.toCookie(): Cookie {
    return Cookie(
        name = name,
        value = value,
        expires = expiresAt.takeIf { it > 0 }?.let { GMTDate(it) },
        domain = domain,
        path = path,
    )
}

internal fun Cookie.toEntity(host: String): CookieEntity {
    return CookieEntity(
        id = "$domain-$name-$path",
        domain = domain ?: host,
        path = path ?: "/",
        name = name,
        value = value,
        expiresAt = expires?.timestamp ?: -1,
    )
}
