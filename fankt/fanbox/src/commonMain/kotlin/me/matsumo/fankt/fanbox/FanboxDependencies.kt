package me.matsumo.fankt.fanbox

import io.ktor.client.plugins.cookies.CookiesStorage
import kotlinx.coroutines.flow.Flow

/** FANBOX の repository で共有する依存関係。 */
// 認証状態の各操作をテストで独立に差し替えるため、callback を一つの依存セットに保持する。
@Suppress("LongParameterList")
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

internal fun createFanboxDependencies(cookieStore: FanboxCookieStorage, tokenStore: FanboxTokenStore): FanboxDependencies {
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
