package me.matsumo.fankt.fanbox

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A normalized Cookie record owned by a [FanboxCookieStorage].
 *
 * [value] is an authentication credential. Do not log, hash, or include this record in telemetry.
 * The custom [toString] intentionally omits it. [domain] is canonicalized without a leading dot,
 * and [hostOnly] distinguishes a host-only Cookie from a domain Cookie.
 */
public data class FanboxCookieRecord(
    public val domain: String,
    public val path: String,
    public val name: String,
    public val value: String,
    public val expiresAtEpochMilliseconds: Long?,
    public val secure: Boolean,
    public val hostOnly: Boolean,
) {
    override fun toString(): String =
        "FanboxCookieRecord(domain=$domain, path=$path, name=$name, " +
            "expiresAtEpochMilliseconds=$expiresAtEpochMilliseconds, secure=$secure, " +
            "hostOnly=$hostOnly, value=<redacted>)"
}

/**
 * Host-owned storage for normalized FANBOX Cookie records.
 *
 * Implementations store records and publish the current snapshot; request URL matching belongs to
 * fankt. [snapshot] must return a finite current value without depending on Flow collection.
 * [replaceAll] atomically replaces the complete snapshot and must retain the previous snapshot if
 * its commit fails. [deleteExpired] must atomically remove only records whose current expiry is at
 * or before its argument; a record refreshed after an earlier snapshot must survive cleanup. Every
 * collector of [cookies] receives the current snapshot at least once.
 *
 * A [Fanbox] never closes or clears an injected storage when the client closes. The host owns its
 * lifetime. State is shared only when the host passes the same instance to multiple clients.
 * Cookie values are credentials and must not be logged or included in telemetry.
 */
public interface FanboxCookieStorage {
    public val cookies: Flow<List<FanboxCookieRecord>>

    public suspend fun snapshot(): List<FanboxCookieRecord>

    public suspend fun upsert(cookie: FanboxCookieRecord)

    public suspend fun delete(domain: String, path: String, name: String)

    public suspend fun deleteExpired(nowEpochMilliseconds: Long)

    public suspend fun replaceAll(cookies: List<FanboxCookieRecord>)

    public suspend fun clear()
}

/**
 * Host-owned storage for the current short-lived CSRF token.
 *
 * Token values are credentials and must not be logged or persisted by the default implementation.
 * A [Fanbox] never closes or clears this store merely because the client closes. State is shared
 * only when the host explicitly passes the same instance to multiple clients.
 */
public interface FanboxTokenStore {
    public val token: Flow<String?>

    public suspend fun get(): String?

    public suspend fun set(token: String?)
}

/** Instance-local, process-memory Cookie storage. */
public class InMemoryFanboxCookieStorage(
    initialCookies: List<FanboxCookieRecord> = emptyList(),
) : FanboxCookieStorage {
    private val mutex = Mutex()
    private val state = MutableStateFlow(initialCookies.canonicalSnapshot())

    override val cookies: Flow<List<FanboxCookieRecord>> = state.asStateFlow().map { it.toList() }

    override suspend fun snapshot(): List<FanboxCookieRecord> = mutex.withLock { state.value.toList() }

    override suspend fun upsert(cookie: FanboxCookieRecord) {
        mutex.withLock {
            state.value = (state.value.filterNot { it.identity == cookie.identity } + cookie)
                .canonicalSnapshot()
        }
    }

    override suspend fun delete(domain: String, path: String, name: String) {
        val identity = CookieIdentity(domain.canonicalDomain(), path.canonicalPath(), name)
        mutex.withLock {
            state.value = state.value.filterNot { it.identity == identity }
        }
    }

    override suspend fun deleteExpired(nowEpochMilliseconds: Long) {
        mutex.withLock {
            state.value = state.value.filterNot { record ->
                record.expiresAtEpochMilliseconds?.let { expiry ->
                    expiry <= nowEpochMilliseconds
                } == true
            }
        }
    }

    override suspend fun replaceAll(cookies: List<FanboxCookieRecord>) {
        val replacement = cookies.canonicalSnapshot()
        mutex.withLock {
            state.value = replacement
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            state.value = emptyList()
        }
    }
}

/** Instance-local, process-memory CSRF token storage. */
public class InMemoryFanboxTokenStore(
    initialToken: String? = null,
) : FanboxTokenStore {
    private val mutex = Mutex()
    private val state = MutableStateFlow(initialToken)

    override val token: Flow<String?> = state.asStateFlow()

    override suspend fun get(): String? = mutex.withLock { state.value }

    override suspend fun set(token: String?) {
        mutex.withLock {
            state.value = token
        }
    }
}

internal data class CookieIdentity(
    val domain: String,
    val path: String,
    val name: String,
)

internal val FanboxCookieRecord.identity: CookieIdentity
    get() = CookieIdentity(domain.canonicalDomain(), path.canonicalPath(), name)

internal fun String.canonicalDomain(): String = trim().trimStart('.').lowercase()

internal fun String.canonicalPath(): String = takeIf { startsWith('/') } ?: "/"

private fun List<FanboxCookieRecord>.canonicalSnapshot(): List<FanboxCookieRecord> =
    associateBy { it.identity }
        .values
        .map { record ->
            record.copy(
                domain = record.domain.canonicalDomain(),
                path = record.path.canonicalPath(),
            )
        }
