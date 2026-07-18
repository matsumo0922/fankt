package me.matsumo.fankt.fanbox

import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.http.Cookie
import io.ktor.http.Url
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class FanboxDependencies(
    val cookieStorage: CookiesStorage,
    val cookies: Flow<List<Cookie>>,
    val csrfToken: Flow<String?>,
    val getCsrfToken: suspend () -> String?,
    val setCsrfToken: suspend (String) -> Unit,
    val clearCsrfToken: suspend () -> Unit,
    val overrideFanboxSessionId: suspend (String) -> Unit,
    val replaceCookies: suspend (Url, List<Cookie>) -> Unit,
)

internal fun createFanboxDependencies(
    cookieStore: FanboxCookieStorage,
    tokenStore: FanboxTokenStore,
): FanboxDependencies {
    val adapter = FanboxCookiesStorageAdapter(cookieStore)

    return FanboxDependencies(
        cookieStorage = adapter,
        cookies = cookieStore.cookies.map { records -> records.map(FanboxCookieRecord::toCookie) },
        csrfToken = tokenStore.token,
        getCsrfToken = tokenStore::get,
        setCsrfToken = { token -> tokenStore.set(token) },
        clearCsrfToken = { tokenStore.set(null) },
        overrideFanboxSessionId = adapter::overrideFanboxSessionId,
        replaceCookies = adapter::replaceAll,
    )
}
