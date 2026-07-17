package me.matsumo.fankt.fanbox

import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.http.Cookie
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.matsumo.fankt.fanbox.datasource.db.PersistentCookieStorage
import me.matsumo.fankt.fanbox.datasource.db.getFanktDatabase
import me.matsumo.fankt.fanbox.domain.model.db.CSRFToken
import me.matsumo.fankt.fanbox.domain.model.db.toCookie

internal class FanboxDependencies(
    val cookieStorage: CookiesStorage,
    val cookies: Flow<List<Cookie>>,
    val csrfToken: Flow<String?>,
    val getLatestToken: suspend () -> CSRFToken?,
    val insertToken: suspend (CSRFToken) -> Unit,
    val clearCookies: suspend () -> Unit,
    val overrideFanboxSessionId: suspend (String) -> Unit,
)

internal fun createFanboxDependencies(ioDispatcher: CoroutineDispatcher): FanboxDependencies {
    val database = getFanktDatabase()
    val cookieDao = database.cookieDao()
    val tokenDao = database.tokenDao()
    val cookieStorage = PersistentCookieStorage(cookieDao, ioDispatcher)
    val latestToken = tokenDao.getLatestToken()

    return FanboxDependencies(
        cookieStorage = cookieStorage,
        cookies = cookieDao.getAllCookies().map { cookies -> cookies.map { it.toCookie() } },
        csrfToken = latestToken.map { it?.value },
        getLatestToken = { latestToken.first() },
        insertToken = tokenDao::insert,
        clearCookies = cookieStorage::clear,
        overrideFanboxSessionId = cookieStorage::overrideFanboxSessionId,
    )
}
