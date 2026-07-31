package me.matsumo.fankt.fanbox

import io.ktor.client.plugins.cookies.CookiesStorage
import kotlinx.coroutines.flow.Flow

internal class FanboxDependencies(
    val cookieStorage: CookiesStorage,
    val cookies: Flow<List<FanboxCookieRecord>>,
    val csrfToken: Flow<String?>,
    val getCsrfToken: suspend () -> String?,
    val setCsrfToken: suspend (String) -> Unit,
    val clearCsrfToken: suspend () -> Unit,
    val overrideFanboxSessionId: suspend (String) -> Unit,
    val addCookie: suspend (FanboxCookieRecord) -> Unit,
    val replaceCookies: suspend (List<FanboxCookieRecord>) -> Unit,
)

internal fun createFanboxDependencies(
    cookieStore: FanboxCookieStorage,
    tokenStore: FanboxTokenStore,
): FanboxDependencies {
    val adapter = FanboxCookiesStorageAdapter(cookieStore)

    return FanboxDependencies(
        cookieStorage = adapter,
        cookies = cookieStore.cookies,
        csrfToken = tokenStore.token,
        getCsrfToken = tokenStore::get,
        setCsrfToken = { token -> tokenStore.set(token) },
        clearCsrfToken = { tokenStore.set(null) },
        overrideFanboxSessionId = adapter::overrideFanboxSessionId,
        addCookie = adapter::addRecord,
        replaceCookies = adapter::replaceAll,
    )
}
