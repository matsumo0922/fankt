package me.matsumo.fankt.fanbox.datasource.db

import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.matches
import io.ktor.http.Cookie
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import me.matsumo.fankt.fanbox.domain.model.db.toCookie
import me.matsumo.fankt.fanbox.domain.model.db.toEntity

internal class PersistentCookieStorage(
    private val cookieDao: CookieDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowEpochMilliseconds: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : CookiesStorage {

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        withContext(ioDispatcher) {
            val now = nowEpochMilliseconds()
            val entity = cookie.toEntity(requestUrl, now)

            if (entity.expiresAt != null && entity.expiresAt <= now) {
                cookieDao.delete(entity.domain, entity.path, entity.name)
            } else {
                cookieDao.insert(entity)
            }
        }
    }

    override suspend fun get(requestUrl: Url): List<Cookie> {
        return withContext(ioDispatcher) {
            val now = nowEpochMilliseconds()
            val cookies = cookieDao.getAllCookies().first()
                .filterNot { entity -> entity.expiresAt?.let { it <= now } == true }
                .map { it.toCookie() }
                .filter { it.matches(requestUrl) }

            try {
                cookieDao.deleteExpired(now)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Expiry filtering above remains authoritative; a later read retries cleanup.
            }

            cookies
        }
    }

    override fun close() {
        // do nothing
    }

    suspend fun clear() {
        withContext(ioDispatcher) {
            cookieDao.clear()
        }
    }

    suspend fun overrideFanboxSessionId(sessionId: String) {
        val cookie = Cookie(
            name = "FANBOXSESSID",
            value = sessionId,
            domain = ".fanbox.cc",
            path = "/",
            expires = null,
            secure = true,
        )

        addCookie(Url("https://www.fanbox.cc"), cookie)
    }
}
