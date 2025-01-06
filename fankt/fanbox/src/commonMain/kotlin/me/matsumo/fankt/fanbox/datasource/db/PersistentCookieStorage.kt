package me.matsumo.fankt.fanbox.datasource.db

import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.http.Cookie
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import me.matsumo.fankt.fanbox.domain.model.db.toCookie
import me.matsumo.fankt.fanbox.domain.model.db.toEntity

internal class PersistentCookieStorage(
    private val cookieDao: CookieDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CookiesStorage {

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        withContext(Dispatchers.IO) {
            cookieDao.insert(cookie.toEntity(requestUrl.host))
        }
    }

    override suspend fun get(requestUrl: Url): List<Cookie> {
        return withContext(ioDispatcher) {
            cookieDao.getAllCookies().firstOrNull()?.map { it.toCookie() }.orEmpty()
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
            expires = null,
        )

        addCookie(Url("https://www.fanbox.cc"), cookie)
    }
}
