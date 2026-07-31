@file:OptIn(kotlin.time.ExperimentalTime::class)

package me.matsumo.fankt.fanbox

import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.fillDefaults
import io.ktor.http.Cookie
import io.ktor.http.Url
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock

internal class FanboxCookiesStorageAdapter(
    private val storage: FanboxCookieStorage,
    private val nowEpochMilliseconds: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : CookiesStorage {
    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        val now = nowEpochMilliseconds()
        val record = cookie.toRecord(requestUrl, now)
        if (record.isExpired(now)) {
            storage.delete(record.domain, record.path, record.name)
        } else {
            storage.upsert(record)
        }
    }

    override suspend fun get(requestUrl: Url): List<Cookie> {
        val now = nowEpochMilliseconds()
        val snapshot = storage.snapshot()

        if (snapshot.any { it.isExpired(now) }) {
            try {
                storage.deleteExpired(now)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Filtering is authoritative. A later request retries best-effort cleanup.
            }
        }

        return snapshot
            .asSequence()
            .filterNot { it.isExpired(now) }
            .filter { it.matches(requestUrl) }
            .map(FanboxCookieRecord::toCookie)
            .toList()
    }

    override fun close(): Unit = Unit

    suspend fun addRecord(record: FanboxCookieRecord) {
        val now = nowEpochMilliseconds()
        val canonicalRecord = record.canonicalized()
        if (canonicalRecord.isExpired(now)) {
            storage.delete(canonicalRecord.domain, canonicalRecord.path, canonicalRecord.name)
        } else {
            storage.upsert(canonicalRecord)
        }
    }

    suspend fun replaceAll(records: List<FanboxCookieRecord>) {
        val now = nowEpochMilliseconds()
        val replacement = records
            .filterNot { record -> record.isExpired(now) }
            .canonicalSnapshot()
        storage.replaceAll(replacement)
    }

    suspend fun overrideFanboxSessionId(sessionId: String) {
        addCookie(
            requestUrl = Url("https://www.fanbox.cc"),
            cookie = Cookie(
                name = FANBOX_SESSION_COOKIE_NAME,
                value = sessionId,
                domain = ".fanbox.cc",
                path = "/",
                expires = null,
                secure = true,
            ),
        )
    }
}

internal fun FanboxCookieRecord.toCookie(): Cookie = Cookie(
    name = name,
    value = value,
    expires = expiresAtEpochMilliseconds?.let(::GMTDate),
    domain = domain,
    path = path,
    secure = secure,
)

internal fun Cookie.toRecord(
    requestUrl: Url,
    nowEpochMilliseconds: Long,
): FanboxCookieRecord {
    val hostOnly = domain.isNullOrBlank()
    val normalized = fillDefaults(requestUrl)
    val normalizedDomain = normalized.domain
        .orEmpty()
        .canonicalDomain()
        .ifBlank { requestUrl.host.canonicalDomain() }

    return FanboxCookieRecord(
        domain = normalizedDomain,
        path = requireNotNull(normalized.path).canonicalPath(),
        name = normalized.name,
        value = normalized.value,
        expiresAtEpochMilliseconds = normalized.maxAge
            ?.toLong()
            ?.times(1_000L)
            ?.let { duration -> nowEpochMilliseconds.saturatedPlus(duration) }
            ?: normalized.expires?.timestamp,
        secure = normalized.secure,
        hostOnly = hostOnly,
    )
}

private fun FanboxCookieRecord.matches(requestUrl: Url): Boolean {
    val requestHost = requestUrl.host.canonicalDomain()
    val cookieDomain = domain.canonicalDomain()
    val domainMatches = if (hostOnly) {
        requestHost == cookieDomain
    } else {
        requestHost == cookieDomain || requestHost.endsWith(".$cookieDomain")
    }
    if (!domainMatches) return false
    if (secure && requestUrl.protocol.name != "https") return false

    val requestPath = requestUrl.encodedPath.ifBlank { "/" }
    val cookiePath = path.canonicalPath()
    return requestPath == cookiePath ||
        requestPath.startsWith(cookiePath) &&
        (cookiePath.endsWith('/') || requestPath.getOrNull(cookiePath.length) == '/')
}

private fun FanboxCookieRecord.isExpired(nowEpochMilliseconds: Long): Boolean =
    expiresAtEpochMilliseconds?.let { it <= nowEpochMilliseconds } == true

private fun Long.saturatedPlus(other: Long): Long = when {
    other > 0 && this > Long.MAX_VALUE - other -> Long.MAX_VALUE
    other < 0 && this < Long.MIN_VALUE - other -> Long.MIN_VALUE
    else -> this + other
}

internal const val FANBOX_SESSION_COOKIE_NAME: String = "FANBOXSESSID"
