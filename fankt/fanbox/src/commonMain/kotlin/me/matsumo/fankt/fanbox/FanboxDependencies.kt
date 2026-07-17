package me.matsumo.fankt.fanbox

import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.http.Cookie
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import me.matsumo.fankt.fanbox.datasource.db.PersistentCookieStorage
import me.matsumo.fankt.fanbox.datasource.db.getFanktDatabase
import me.matsumo.fankt.fanbox.domain.model.db.toCookie

private val processCsrfToken = MutableStateFlow<String?>(null)

internal class FanboxDependencies(
    val cookieStorage: CookiesStorage,
    val cookies: Flow<List<Cookie>>,
    val csrfToken: Flow<String?>,
    val getCsrfToken: suspend () -> String?,
    val setCsrfToken: suspend (String) -> Unit,
    val clearCsrfToken: () -> Unit,
    val clearCookies: suspend () -> Unit,
    val overrideFanboxSessionId: suspend (String) -> Unit,
)

internal fun createFanboxDependencies(ioDispatcher: CoroutineDispatcher): FanboxDependencies {
    val database = getFanktDatabase()
    val cookieDao = database.cookieDao()
    val cookieStorage = PersistentCookieStorage(cookieDao, ioDispatcher)

    return FanboxDependencies(
        cookieStorage = cookieStorage,
        cookies = cookieDao.getAllCookies().map { cookies -> cookies.map { it.toCookie() } },
        csrfToken = processCsrfToken.asStateFlow(),
        getCsrfToken = { processCsrfToken.value },
        setCsrfToken = { token -> processCsrfToken.value = token },
        clearCsrfToken = { processCsrfToken.value = null },
        clearCookies = cookieStorage::clear,
        overrideFanboxSessionId = cookieStorage::overrideFanboxSessionId,
    )
}
