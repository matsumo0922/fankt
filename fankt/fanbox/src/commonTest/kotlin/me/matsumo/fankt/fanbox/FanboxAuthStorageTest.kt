package me.matsumo.fankt.fanbox

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.fixture.FanboxMetadataHtmlFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FanboxAuthStorageTest {
    @Test
    fun canonicalizationDefinesPersistenceIdentityParityVectors() = runBlocking {
        val storage = InMemoryFanboxCookieStorage()

        storage.upsert(record("session", "value", domain = " .FANBOX.CC ", path = "index"))

        assertEquals("fanbox.cc", storage.snapshot().single().domain)
        assertEquals("/", storage.snapshot().single().path)

        storage.delete(" .FANBOX.CC ", "index", "session")

        assertTrue(storage.snapshot().isEmpty())
    }

    @Test
    fun snapshotReturnsCurrentValueWithoutFlowCollection() = runBlocking {
        val storage = InMemoryFanboxCookieStorage(listOf(record("session", "value")))

        assertEquals(listOf("session"), storage.snapshot().map { it.name })
    }

    @Test
    fun observationStartsWithCurrentSnapshot() = runBlocking {
        val storage = InMemoryFanboxCookieStorage(listOf(record("session", "value")))

        assertEquals(storage.snapshot(), storage.cookies.first())
    }

    @Test
    fun snapshotsAndFlowEmissionsDoNotExposeInternalMutableList() = runBlocking {
        val storage = InMemoryFanboxCookieStorage(listOf(record("session", "value")))

        runCatching { (storage.snapshot() as? MutableList)?.clear() }
        runCatching { (storage.cookies.first() as? MutableList)?.clear() }

        assertEquals(listOf("session"), storage.snapshot().map { it.name })
    }

    @Test
    fun upsertDeleteClearAndReplaceAllPublishDeterministically() = runBlocking {
        val storage = InMemoryFanboxCookieStorage()
        storage.upsert(record("first", "one"))
        assertEquals("one", storage.cookies.first().single().value)

        storage.upsert(record("first", "two"))
        assertEquals("two", storage.snapshot().single().value)

        storage.replaceAll(listOf(record("second", "value"), record("third", "value")))
        assertEquals(setOf("second", "third"), storage.snapshot().map { it.name }.toSet())

        storage.delete("fanbox.cc", "/", "second")
        assertEquals(listOf("third"), storage.snapshot().map { it.name })

        storage.clear()
        assertTrue(storage.snapshot().isEmpty())
    }

    @Test
    fun concurrentMutationsDoNotLoseDistinctCookies() = runBlocking {
        val storage = InMemoryFanboxCookieStorage()

        (0 until 100).map { index ->
            async { storage.upsert(record("cookie-$index", "value-$index")) }
        }.awaitAll()

        assertEquals(100, storage.snapshot().size)
    }

    @Test
    fun failedAtomicReplacementRetainsPreviousSnapshot() = runBlocking {
        val previous = listOf(record("previous", "value"))
        val storage = FailingReplacementStorage(previous)
        val adapter = FanboxCookiesStorageAdapter(storage)

        runCatching {
            adapter.replaceAll(listOf(record("new", "value")))
        }

        assertEquals(previous, storage.snapshot())
    }

    @Test
    fun hostOnlyCookieDoesNotReachSiblingHost() = runBlocking {
        val storage = InMemoryFanboxCookieStorage(
            listOf(record("host-only", "value", domain = "www.fanbox.cc", hostOnly = true)),
        )
        val adapter = FanboxCookiesStorageAdapter(storage)

        assertTrue(adapter.get(Url("https://api.fanbox.cc/")).isEmpty())
        assertEquals(listOf("host-only"), adapter.get(Url("https://www.fanbox.cc/")).map { it.name })
    }

    @Test
    fun domainCookieMatchesDotDelimitedSubdomainButNotLookalikeSuffix() = runBlocking {
        val storage = InMemoryFanboxCookieStorage(listOf(record("session", "value")))
        val adapter = FanboxCookiesStorageAdapter(storage)

        assertEquals(listOf("session"), adapter.get(Url("https://api.fanbox.cc/")).map { it.name })
        assertTrue(adapter.get(Url("https://evilfanbox.cc/")).isEmpty())
        assertTrue(adapter.get(Url("https://pixiv.net/")).isEmpty())
    }

    @Test
    fun pathBoundaryAndSecureTransportRestrictDelivery() = runBlocking {
        val storage = InMemoryFanboxCookieStorage(
            listOf(
                record("account", "value", path = "/account"),
                record("secure", "value", secure = true),
            ),
        )
        val adapter = FanboxCookiesStorageAdapter(storage)

        assertEquals(
            setOf("account", "secure"),
            adapter.get(Url("https://api.fanbox.cc/account/profile")).map { it.name }.toSet(),
        )
        assertEquals(listOf("secure"), adapter.get(Url("https://api.fanbox.cc/accounting")).map { it.name })
        assertTrue(adapter.get(Url("http://api.fanbox.cc/account/profile")).isEmpty())
    }

    @Test
    fun blankDomainCookieIsRecordedAsHostOnly() = runBlocking {
        val storage = InMemoryFanboxCookieStorage()
        val adapter = FanboxCookiesStorageAdapter(storage)

        adapter.addCookie(Url("https://www.fanbox.cc/"), Cookie("session", "value", domain = ""))

        assertTrue(storage.snapshot().single().hostOnly)
        assertEquals("www.fanbox.cc", storage.snapshot().single().domain)
    }

    @Test
    fun expiredCookieIsExcludedWhenCleanupFails() = runBlocking {
        val storage = CleanupFailingStorage(
            listOf(
                record("expired", "secret", expiresAt = NOW),
                record("active", "value", expiresAt = NOW + 1),
            ),
        )
        val adapter = FanboxCookiesStorageAdapter(storage) { NOW }

        assertEquals(listOf("active"), adapter.get(Url("https://api.fanbox.cc/")).map { it.name })
        assertEquals(1, storage.cleanupAttempts)
    }

    @Test
    fun expiryCleanupCancellationIsRethrown() = runBlocking {
        val storage = CleanupCancellingStorage(
            listOf(record("expired", "secret", expiresAt = NOW)),
        )
        val adapter = FanboxCookiesStorageAdapter(storage) { NOW }

        assertFailsWith<CancellationException> {
            adapter.get(Url("https://api.fanbox.cc/"))
        }
        Unit
    }

    @Test
    fun cleanupDoesNotDeleteCookieRefreshedAfterSnapshot() = runBlocking {
        val expired = record("session", "expired", expiresAt = NOW)
        val refreshed = record("session", "refreshed", expiresAt = NOW + 60_000)
        val storage = RefreshAfterSnapshotStorage(expired, refreshed)
        val adapter = FanboxCookiesStorageAdapter(storage) { NOW }

        assertTrue(adapter.get(Url("https://api.fanbox.cc/")).isEmpty())
        assertEquals("refreshed", storage.snapshot().single().value)
    }

    @Test
    fun expiryBoundaryAndLaterCleanupRetryRemainSafe() = runBlocking {
        val storage = RetryCleanupStorage(
            listOf(
                record("past", "secret", expiresAt = NOW - 1),
                record("boundary", "secret", expiresAt = NOW),
                record("future", "value", expiresAt = NOW + 1),
                record("session", "value"),
            ),
        )
        val adapter = FanboxCookiesStorageAdapter(storage) { NOW }

        assertEquals(setOf("future", "session"), adapter.get(Url("https://api.fanbox.cc/")).map { it.name }.toSet())
        assertEquals(setOf("future", "session"), adapter.get(Url("https://api.fanbox.cc/")).map { it.name }.toSet())
        assertEquals(2, storage.cleanupAttempts)
        assertFalse(storage.snapshot().any { it.name == "past" || it.name == "boundary" })
    }

    @Test
    fun resetCookiesUsesExactlyOneAtomicReplacement() = runBlocking {
        val storage = RecordingMutationStorage()
        val fanbox = Fanbox(
            clientFactory = metadataClientFactory("unused-token"),
            cookieStorage = storage,
        )
        try {
            fanbox.setCookies(
                cookies = listOf(record("first", "one"), record("second", "two")),
                reset = true,
            )

            assertEquals(1, storage.replaceAllCalls)
            assertEquals(0, storage.clearCalls)
            assertEquals(0, storage.upsertCalls)
            assertEquals(setOf("first", "second"), storage.snapshot().map { it.name }.toSet())
        } finally {
            fanbox.close()
        }
    }

    @Test
    fun publicAdditiveExpiredRecordDeletesMatchingIdentity() = runBlocking {
        val storage = InMemoryFanboxCookieStorage(listOf(record("session", "old")))
        val fanbox = Fanbox(cookieStorage = storage)
        try {
            fanbox.setCookies(listOf(record("session", "expired", expiresAt = 0)))

            assertTrue(storage.snapshot().isEmpty())
            assertTrue(fanbox.cookies.first().isEmpty())
        } finally {
            fanbox.close()
        }
    }

    @Test
    fun publicReplacementAtomicallyOmitsExpiredRecords() = runBlocking {
        val storage = RecordingMutationStorage()
        val fanbox = Fanbox(cookieStorage = storage)
        try {
            fanbox.setCookies(
                listOf(
                    record("live", "value"),
                    record("expired", "secret", expiresAt = 0),
                ),
                reset = true,
            )

            assertEquals(1, storage.replaceAllCalls)
            assertEquals(listOf("live"), storage.snapshot().map { it.name })
        } finally {
            fanbox.close()
        }
    }

    @Test
    fun maxAgeIsAbsoluteSessionExpiryStaysNullAndIdentityIsCanonical() = runBlocking {
        val storage = InMemoryFanboxCookieStorage()
        val adapter = FanboxCookiesStorageAdapter(storage) { NOW }
        val url = Url("https://api.fanbox.cc/path")

        adapter.addCookie(url, Cookie("max-age", "value", maxAge = 30))
        adapter.addCookie(url, Cookie("session", "first", domain = ".FANBOX.CC", path = "/"))
        adapter.addCookie(url, Cookie("session", "second", domain = "fanbox.cc", path = "/"))
        adapter.addCookie(url, Cookie("host-only", "value"))

        val snapshot = storage.snapshot()
        assertEquals(NOW + 30_000, snapshot.single { it.name == "max-age" }.expiresAtEpochMilliseconds)
        assertEquals(null, snapshot.single { it.name == "session" }.expiresAtEpochMilliseconds)
        assertEquals("second", snapshot.single { it.name == "session" }.value)
        assertEquals("fanbox.cc", snapshot.single { it.name == "session" }.domain)
        assertEquals("api.fanbox.cc", snapshot.single { it.name == "host-only" }.domain)
        assertTrue(snapshot.single { it.name == "host-only" }.hostOnly)
    }

    @Test
    fun maxAgeOverflowSaturatesAndImmediateExpiryDeletesCanonicalIdentity() = runBlocking {
        val nearMaximum = Long.MAX_VALUE - 1_000
        val storage = InMemoryFanboxCookieStorage(listOf(record("delete-me", "old")))
        val adapter = FanboxCookiesStorageAdapter(storage) { nearMaximum }
        val url = Url("https://api.fanbox.cc/")

        adapter.addCookie(url, Cookie("long-lived", "value", maxAge = Int.MAX_VALUE))
        adapter.addCookie(url, Cookie("delete-me", "expired", maxAge = 0, domain = ".FANBOX.CC", path = "/"))

        assertEquals(Long.MAX_VALUE, storage.snapshot().single { it.name == "long-lived" }.expiresAtEpochMilliseconds)
        assertFalse(storage.snapshot().any { it.name == "delete-me" })
    }

    @Test
    fun defaultFanboxInstancesHaveIndependentCookieAndTokenStores() = runBlocking {
        val first = Fanbox(clientFactory = metadataClientFactory("first-token"))
        val second = Fanbox(clientFactory = metadataClientFactory("second-token"))
        try {
            first.setCookies(listOf(record("first", "value")))
            first.updateCsrfToken()

            assertEquals(listOf("first"), first.cookies.first().map { it.name })
            assertTrue(second.cookies.first().isEmpty())
            assertEquals("first-token", first.csrfToken.first())
            assertNull(second.csrfToken.first())
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun explicitlySharedStoresShareStateAndCloseDoesNotClearThem() = runBlocking {
        val cookies = InMemoryFanboxCookieStorage()
        val tokens = InMemoryFanboxTokenStore()
        val first = Fanbox(cookieStorage = cookies, tokenStore = tokens)
        val second = Fanbox(cookieStorage = cookies, tokenStore = tokens)
        try {
            first.setCookies(listOf(record("shared", "value")))
            tokens.set("shared-token")

            assertEquals(listOf("shared"), second.cookies.first().map { it.name })
            assertEquals("shared-token", second.csrfToken.first())
            first.close()
            assertEquals(listOf("shared"), cookies.snapshot().map { it.name })
            assertEquals("shared-token", tokens.get())
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun injectedStoresServeGeneratedAndDownloadClientPaths() = runBlocking {
        val requests = mutableListOf<Triple<String, String?, String?>>()
        val factory = FanboxHttpClientFactory { block ->
            HttpClient(
                MockEngine { request ->
                    requests += Triple(
                        request.url.encodedPath,
                        request.headers[HttpHeaders.Cookie],
                        request.headers[FANBOX_CSRF_HEADER],
                    )
                    if (request.url.host == "www.fanbox.cc") {
                        respond(
                            FanboxMetadataHtmlFixtures.home,
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "text/html; charset=UTF-8"),
                        )
                    } else {
                        respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                },
                block,
            )
        }
        val cookies = InMemoryFanboxCookieStorage()
        val tokens = InMemoryFanboxTokenStore()
        val fanbox = Fanbox(factory, cookieStorage = cookies, tokenStore = tokens)
        try {
            fanbox.setFanboxSessionId("session-value")
            tokens.set("request-token")

            fanbox.updateCsrfToken()
            fanbox.likePost(FanboxPostId("fixture"))
            fanbox.download("https://api.fanbox.cc/media.bin") {}

            assertTrue(requests.any { it.first.endsWith("post.likePost") })
            assertTrue(requests.any { it.first == "/media.bin" })
            assertTrue(requests.all { it.second == "FANBOXSESSID=session-value" })
            assertTrue(requests.drop(1).all { it.third == "fixture-token" })
        } finally {
            fanbox.close()
        }
    }

    private fun record(
        name: String,
        value: String,
        domain: String = "fanbox.cc",
        path: String = "/",
        hostOnly: Boolean = false,
        expiresAt: Long? = null,
        secure: Boolean = true,
    ) = FanboxCookieRecord(domain, path, name, value, expiresAt, secure, hostOnly)

    private fun metadataClientFactory(token: String) = FanboxHttpClientFactory { block ->
        HttpClient(
            MockEngine { request ->
                if (request.url.host == "www.fanbox.cc") {
                    respond(
                        content = FanboxMetadataHtmlFixtures.home.replace("fixture-token", token),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/html; charset=UTF-8"),
                    )
                } else {
                    respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            },
            block,
        )
    }

    private class FailingReplacementStorage(initial: List<FanboxCookieRecord>) : FanboxCookieStorage {
        private val delegate = InMemoryFanboxCookieStorage(initial)
        override val cookies = delegate.cookies
        override suspend fun snapshot() = delegate.snapshot()
        override suspend fun upsert(cookie: FanboxCookieRecord) = delegate.upsert(cookie)
        override suspend fun delete(domain: String, path: String, name: String) =
            delegate.delete(domain, path, name)
        override suspend fun deleteExpired(nowEpochMilliseconds: Long) =
            delegate.deleteExpired(nowEpochMilliseconds)
        override suspend fun replaceAll(cookies: List<FanboxCookieRecord>): Unit =
            error("atomic commit failed")
        override suspend fun clear() = delegate.clear()
    }

    private class CleanupFailingStorage(initial: List<FanboxCookieRecord>) : FanboxCookieStorage {
        private val delegate = InMemoryFanboxCookieStorage(initial)
        var cleanupAttempts = 0
        override val cookies = delegate.cookies
        override suspend fun snapshot() = delegate.snapshot()
        override suspend fun upsert(cookie: FanboxCookieRecord) = delegate.upsert(cookie)
        override suspend fun delete(domain: String, path: String, name: String) =
            delegate.delete(domain, path, name)
        override suspend fun deleteExpired(nowEpochMilliseconds: Long) {
            cleanupAttempts += 1
            error("cleanup unavailable")
        }
        override suspend fun replaceAll(cookies: List<FanboxCookieRecord>) = delegate.replaceAll(cookies)
        override suspend fun clear() = delegate.clear()
    }

    private class RetryCleanupStorage(initial: List<FanboxCookieRecord>) : FanboxCookieStorage {
        private val delegate = InMemoryFanboxCookieStorage(initial)
        var cleanupAttempts = 0
        override val cookies = delegate.cookies
        override suspend fun snapshot() = delegate.snapshot()
        override suspend fun upsert(cookie: FanboxCookieRecord) = delegate.upsert(cookie)
        override suspend fun delete(domain: String, path: String, name: String) =
            delegate.delete(domain, path, name)
        override suspend fun deleteExpired(nowEpochMilliseconds: Long) {
            cleanupAttempts += 1
            if (cleanupAttempts == 1) error("first cleanup unavailable")
            delegate.deleteExpired(nowEpochMilliseconds)
        }
        override suspend fun replaceAll(cookies: List<FanboxCookieRecord>) = delegate.replaceAll(cookies)
        override suspend fun clear() = delegate.clear()
    }

    private class CleanupCancellingStorage(initial: List<FanboxCookieRecord>) : FanboxCookieStorage {
        private val delegate = InMemoryFanboxCookieStorage(initial)
        override val cookies = delegate.cookies
        override suspend fun snapshot() = delegate.snapshot()
        override suspend fun upsert(cookie: FanboxCookieRecord) = delegate.upsert(cookie)
        override suspend fun delete(domain: String, path: String, name: String) =
            delegate.delete(domain, path, name)
        override suspend fun deleteExpired(nowEpochMilliseconds: Long): Unit =
            throw CancellationException("cleanup cancelled")
        override suspend fun replaceAll(cookies: List<FanboxCookieRecord>) = delegate.replaceAll(cookies)
        override suspend fun clear() = delegate.clear()
    }

    private class RefreshAfterSnapshotStorage(
        expired: FanboxCookieRecord,
        private val refreshed: FanboxCookieRecord,
    ) : FanboxCookieStorage {
        private val delegate = InMemoryFanboxCookieStorage(listOf(expired))
        private var refreshPending = true
        override val cookies = delegate.cookies
        override suspend fun snapshot(): List<FanboxCookieRecord> {
            val snapshot = delegate.snapshot()
            if (refreshPending) {
                refreshPending = false
                delegate.upsert(refreshed)
            }
            return snapshot
        }
        override suspend fun upsert(cookie: FanboxCookieRecord) = delegate.upsert(cookie)
        override suspend fun delete(domain: String, path: String, name: String) =
            delegate.delete(domain, path, name)
        override suspend fun deleteExpired(nowEpochMilliseconds: Long) =
            delegate.deleteExpired(nowEpochMilliseconds)
        override suspend fun replaceAll(cookies: List<FanboxCookieRecord>) = delegate.replaceAll(cookies)
        override suspend fun clear() = delegate.clear()
    }

    private class RecordingMutationStorage : FanboxCookieStorage {
        private val delegate = InMemoryFanboxCookieStorage()
        var upsertCalls = 0
        var replaceAllCalls = 0
        var clearCalls = 0
        override val cookies = delegate.cookies
        override suspend fun snapshot() = delegate.snapshot()
        override suspend fun upsert(cookie: FanboxCookieRecord) {
            upsertCalls += 1
            delegate.upsert(cookie)
        }
        override suspend fun delete(domain: String, path: String, name: String) =
            delegate.delete(domain, path, name)
        override suspend fun deleteExpired(nowEpochMilliseconds: Long) =
            delegate.deleteExpired(nowEpochMilliseconds)
        override suspend fun replaceAll(cookies: List<FanboxCookieRecord>) {
            replaceAllCalls += 1
            delegate.replaceAll(cookies)
        }
        override suspend fun clear() {
            clearCalls += 1
            delegate.clear()
        }
    }

    private companion object {
        const val NOW = 1_750_000_000_000L
    }
}
