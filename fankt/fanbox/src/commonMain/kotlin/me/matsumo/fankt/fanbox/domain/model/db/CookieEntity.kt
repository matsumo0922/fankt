package me.matsumo.fankt.fanbox.domain.model.db

import androidx.room.Entity
import androidx.room.Index
import io.ktor.client.plugins.cookies.fillDefaults
import io.ktor.http.Cookie
import io.ktor.http.Url
import io.ktor.util.date.GMTDate

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

internal fun CookieEntity.toCookie(): Cookie {
    return Cookie(
        name = name,
        value = value,
        expires = expiresAt?.let(::GMTDate),
        domain = domain,
        path = path,
        secure = secure,
    )
}

internal fun Cookie.toEntity(requestUrl: Url, nowEpochMilliseconds: Long): CookieEntity {
    val cookie = fillDefaults(requestUrl)
    val domain = cookie.domain
        .orEmpty()
        .trimStart('.')
        .lowercase()
        .ifBlank { requestUrl.host.lowercase() }

    return CookieEntity(
        domain = domain,
        path = requireNotNull(cookie.path),
        name = cookie.name,
        value = cookie.value,
        expiresAt = cookie.maxAge
            ?.toLong()
            ?.times(1_000L)
            ?.let { duration -> nowEpochMilliseconds.saturatedPlus(duration) }
            ?: cookie.expires?.timestamp,
        secure = cookie.secure,
    )
}

private fun Long.saturatedPlus(other: Long): Long {
    return when {
        other > 0 && this > Long.MAX_VALUE - other -> Long.MAX_VALUE
        other < 0 && this < Long.MIN_VALUE - other -> Long.MIN_VALUE
        else -> this + other
    }
}
