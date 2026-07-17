package me.matsumo.fankt.fanbox.datasource.db

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import me.matsumo.fankt.fanbox.FanboxDiagnosticSource
import me.matsumo.fankt.fanbox.buildHttpClient
import me.matsumo.fankt.fanbox.createFanboxJson
import me.matsumo.fankt.fanbox.domain.model.db.CookieEntity
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersistentCookieStorageTest {

    @Test
    fun fanboxRequestReturnsOnlyMatchingDomainCookie() = runBlocking {
        val dao = FakeCookieDao(
            cookie("fanbox", "fanbox-value", domain = "fanbox.cc"),
            cookie("pixiv", "pixiv-value", domain = "pixiv.net"),
        )
        val storage = storage(dao)

        val cookies = storage.get(Url("https://api.fanbox.cc/post.info"))

        assertEquals(listOf("fanbox"), cookies.map { it.name })
    }

    @Test
    fun pathAndSecureAttributesRestrictCookieDelivery() = runBlocking {
        val dao = FakeCookieDao(
            cookie("account", "account-value", path = "/account/"),
            cookie("secure", "secure-value", secure = true),
        )
        val storage = storage(dao)

        assertEquals(
            listOf("secure"),
            storage.get(Url("https://api.fanbox.cc/post.info")).map { it.name },
        )
        assertTrue(storage.get(Url("http://api.fanbox.cc/post.info")).isEmpty())
    }

    @Test
    fun overriddenFanboxSessionIsSentOnlyOverHttps() = runBlocking {
        val storage = storage(FakeCookieDao())

        storage.overrideFanboxSessionId("session-value")

        assertEquals(
            listOf("FANBOXSESSID" to "session-value"),
            storage.get(Url("https://api.fanbox.cc/post.info")).map { it.name to it.value },
        )
        assertTrue(storage.get(Url("http://api.fanbox.cc/post.info")).isEmpty())
    }

    @Test
    fun expiredCookieIsFilteredAndDeletedAtReadBoundary() = runBlocking {
        val dao = FakeCookieDao(
            cookie("past", "past-value", expiresAt = NOW - 1),
            cookie("boundary", "boundary-value", expiresAt = NOW),
            cookie("future", "future-value", expiresAt = NOW + 1),
            cookie("session", "session-value", expiresAt = null),
        )
        val storage = storage(dao)

        val cookies = storage.get(Url("https://api.fanbox.cc/"))

        assertEquals(setOf("future", "session"), cookies.map { it.name }.toSet())
        assertEquals(setOf("future", "session"), dao.cookies.value.map { it.name }.toSet())
    }

    @Test
    fun cleanupFailureStillReturnsOnlyActiveCookies() = runBlocking {
        val dao = FakeCookieDao(
            cookie("past", "past-value", expiresAt = NOW - 1),
            cookie("active", "active-value"),
        ).apply { cleanupFailure = IllegalStateException("database busy") }
        val storage = storage(dao)

        val cookies = storage.get(Url("https://api.fanbox.cc/"))

        assertEquals(listOf("active"), cookies.map { it.name })
        assertEquals(setOf("past", "active"), dao.cookies.value.map { it.name }.toSet())
        assertEquals(1, dao.cleanupAttempts)
    }

    @Test
    fun cleanupDoesNotDeleteCookieRefreshedAfterSnapshot() = runBlocking {
        val dao = FakeCookieDao(cookie("session", "expired", expiresAt = NOW - 1))
        dao.beforeCleanup = {
            dao.cookies.value = listOf(cookie("session", "refreshed", expiresAt = NOW + 60_000))
        }
        val storage = storage(dao)

        val cookies = storage.get(Url("https://api.fanbox.cc/"))

        assertTrue(cookies.isEmpty())
        assertEquals("refreshed", dao.cookies.value.single().value)
    }

    @Test
    fun canonicalIdentityReplacesDomainVariantsAndHostDefaults() = runBlocking {
        val dao = FakeCookieDao()
        val storage = storage(dao)
        val requestUrl = Url("https://api.fanbox.cc/path")

        storage.addCookie(
            requestUrl,
            Cookie("session", "first", domain = ".FANBOX.CC", path = "/"),
        )
        storage.addCookie(
            requestUrl,
            Cookie("session", "second", domain = "fanbox.cc", path = "/"),
        )
        storage.addCookie(
            requestUrl,
            Cookie("host-only", "host-value", path = "/"),
        )

        assertEquals(2, dao.cookies.value.size)
        assertEquals("second", dao.cookies.value.single { it.name == "session" }.value)
        assertEquals("api.fanbox.cc", dao.cookies.value.single { it.name == "host-only" }.domain)
    }

    @Test
    fun maxAgeBecomesAbsoluteExpiryAndSessionRemainsNull() = runBlocking {
        val dao = FakeCookieDao()
        val storage = storage(dao)
        val requestUrl = Url("https://api.fanbox.cc/")

        storage.addCookie(requestUrl, Cookie("max-age", "value", maxAge = 30))
        storage.addCookie(requestUrl, Cookie("session", "value"))

        assertEquals(NOW + 30_000, dao.cookies.value.single { it.name == "max-age" }.expiresAt)
        assertNull(dao.cookies.value.single { it.name == "session" }.expiresAt)
    }

    @Test
    fun maxAgeOverflowSaturatesAndImmediateExpiryDeletesExistingCookie() = runBlocking {
        val dao = FakeCookieDao(cookie("delete-me", "old"))
        val nearMaximum = Long.MAX_VALUE - 1_000
        val storage = PersistentCookieStorage(
            cookieDao = dao,
            nowEpochMilliseconds = { nearMaximum },
        )
        val requestUrl = Url("https://api.fanbox.cc/")

        storage.addCookie(requestUrl, Cookie("long-lived", "value", maxAge = Int.MAX_VALUE))
        storage.addCookie(
            requestUrl,
            Cookie(
                name = "delete-me",
                value = "expired",
                maxAge = 0,
                domain = "fanbox.cc",
                path = "/",
            ),
        )

        assertEquals(Long.MAX_VALUE, dao.cookies.value.single { it.name == "long-lived" }.expiresAt)
        assertFalse(dao.cookies.value.any { it.name == "delete-me" })
    }

    @Test
    fun addCookieEntersInjectedDispatcherBeforeDaoCall() = runBlocking {
        val dispatcher = RecordingDispatcher()
        val dao = FakeCookieDao()
        val storage = PersistentCookieStorage(
            cookieDao = dao,
            ioDispatcher = dispatcher,
            nowEpochMilliseconds = { NOW },
        )

        storage.addCookie(Url("https://api.fanbox.cc/"), Cookie("session", "value"))

        assertTrue(dispatcher.dispatchCount > 0)
        assertTrue(dao.insertObservedDispatch)
    }

    @Test
    fun productionFactorySendsOnlyActiveMatchingCookies() = runBlocking {
        val dao = FakeCookieDao(
            cookie("fanbox", "fanbox-value"),
            cookie("pixiv", "pixiv-value", domain = "pixiv.net"),
            cookie("expired", "expired-value", expiresAt = NOW),
        )
        val storage = storage(dao)
        var cookieHeader: String? = null
        val engine = MockEngine { request ->
            cookieHeader = request.headers[HttpHeaders.Cookie]
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = buildHttpClient(
            formatter = createFanboxJson(),
            cookieStorage = storage,
            source = FanboxDiagnosticSource.PublicRaw,
            engine = engine,
        )

        client.get("https://api.fanbox.cc/post.info")

        assertEquals("fanbox=fanbox-value", cookieHeader)
    }

    private fun storage(dao: FakeCookieDao): PersistentCookieStorage {
        return PersistentCookieStorage(
            cookieDao = dao,
            nowEpochMilliseconds = { NOW },
        )
    }

    private fun cookie(
        name: String,
        value: String,
        domain: String = "fanbox.cc",
        path: String = "/",
        expiresAt: Long? = null,
        secure: Boolean = false,
    ) = CookieEntity(domain, path, name, value, expiresAt, secure)

    private class FakeCookieDao(vararg initialCookies: CookieEntity) : CookieDao {
        val cookies = MutableStateFlow(initialCookies.toList())
        var cleanupFailure: Exception? = null
        var beforeCleanup: (() -> Unit)? = null
        var cleanupAttempts = 0
        var insertObservedDispatch = false

        private val CookieEntity.identity: Triple<String, String, String>
            get() = Triple(domain, path, name)

        override fun getAllCookies(): Flow<List<CookieEntity>> = cookies

        override suspend fun insert(cookie: CookieEntity) {
            insertObservedDispatch = RecordingDispatcher.isDispatching
            cookies.value = cookies.value
                .filterNot { it.identity == cookie.identity } + cookie
        }

        override suspend fun delete(domain: String, path: String, name: String) {
            cookies.value = cookies.value.filterNot {
                it.domain == domain && it.path == path && it.name == name
            }
        }

        override suspend fun deleteExpired(nowEpochMilliseconds: Long) {
            cleanupAttempts += 1
            beforeCleanup?.invoke()
            cleanupFailure?.let { throw it }
            cookies.value = cookies.value.filterNot {
                it.expiresAt?.let { expiresAt -> expiresAt <= nowEpochMilliseconds } == true
            }
        }

        override suspend fun clear() {
            cookies.value = emptyList()
        }
    }

    private class RecordingDispatcher : CoroutineDispatcher() {
        var dispatchCount = 0

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatchCount += 1
            isDispatching = true
            try {
                block.run()
            } finally {
                isDispatching = false
            }
        }

        companion object {
            var isDispatching = false
        }
    }

    private companion object {
        const val NOW = 1_750_000_000_000L
    }
}
