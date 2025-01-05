package me.matsumo.fankt.datasource.db

import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.http.Cookie
import io.ktor.http.Url
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import me.matsumo.fankt.domain.model.db.CookieEntity

internal class PersistentCookieStorage(
    private val cookieDao: CookieDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
): CookiesStorage {

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        withContext(Dispatchers.IO) {
            cookieDao.insert(
                CookieEntity(
                    id = "${cookie.domain}-${cookie.name}-${cookie.path}",
                    domain = cookie.domain ?: requestUrl.host,
                    path = cookie.path ?: "/",
                    name = cookie.name,
                    value = cookie.value,
                    expiresAt = cookie.expires?.timestamp ?: -1
                )
            )
        }
    }

    override suspend fun get(requestUrl: Url): List<Cookie> {
        return withContext(ioDispatcher) {
            cookieDao.getAllCookies().map { entity ->
                Cookie(
                    name = entity.name,
                    value = entity.value,
                    expires = entity.expiresAt.takeIf { it > 0 }?.let { GMTDate(it) },
                    domain = entity.domain,
                    path = entity.path
                )
            }
        }
    }

    override fun close() {
        // do nothing
    }

    suspend fun overrideFanboxSessionId(sessionId: String) {
        val cookie = Cookie(
            name = "FANBOXSESSID",
            value = sessionId,
            domain = ".fanbox.cc",
            path = "/",
            expires = null
        )

        addCookie(Url("https://www.fanbox.cc"), cookie)
    }
}